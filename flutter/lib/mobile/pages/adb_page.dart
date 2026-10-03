import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/local_adb_model.dart';
import 'home_page.dart';

/// LADB's local pairing/terminal workflow, hosted inside Tunnel and sharing its
/// native ADB manager. Navigation never starts pairing or screen capture.
class AdbPage extends StatefulWidget implements PageShape {
  const AdbPage({Key? key, required this.active}) : super(key: key);

  final ValueListenable<bool> active;
  @override
  String get title => 'LADB';
  @override
  Widget get icon => const Icon(Icons.terminal);
  @override
  List<Widget> get appBarActions => const [];
  @override
  State<AdbPage> createState() => _AdbPageState();
}

class _AdbPageState extends State<AdbPage>
    with WidgetsBindingObserver, AutomaticKeepAliveClientMixin {
  final _model = LocalAdbModel();
  final _pairPort = TextEditingController();
  final _pairCode = TextEditingController();
  final _connectPort = TextEditingController();
  final _command = TextEditingController();
  final _outputScroll = ScrollController();
  final _form = GlobalKey<FormState>();
  bool _foreground = true;
  bool _showPairing = true;
  bool _autoScroll = true;
  double _fontSize = 13;
  String _previousOutput = '';

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.active.addListener(_visibilityChanged);
    _model.addListener(_modelChanged);
    unawaited(_model.initialize());
    _visibilityChanged();
  }

  void _visibilityChanged() =>
      _model.setActive(widget.active.value && _foreground);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _visibilityChanged();
  }

  void _modelChanged() {
    if (!mounted) return;
    if (_model.output != _previousOutput) {
      _previousOutput = _model.output;
      if (_autoScroll) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && _outputScroll.hasClients) {
            _outputScroll.jumpTo(_outputScroll.position.maxScrollExtent);
          }
        });
      }
    }
    setState(() {});
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.active.removeListener(_visibilityChanged);
    _model.removeListener(_modelChanged);
    _model.dispose();
    _pairPort.dispose();
    _pairCode.clear();
    _pairCode.dispose();
    _connectPort.dispose();
    _command.dispose();
    _outputScroll.dispose();
    super.dispose();
  }

  String? _portError(String? text) {
    if (text == null || text.isEmpty) return null;
    final port = int.tryParse(text);
    return port != null && port > 0 && port <= 65535
        ? null
        : '请输入 1–65535 的端口';
  }

  Future<void> _pair() async {
    if (_form.currentState?.validate() != true) return;
    final code = _pairCode.text;
    _pairCode.clear(); // Never persist or retain the code in the UI after submit.
    FocusScope.of(context).unfocus();
    await _model.run('配对并连接', 'tunnel_adb_pair', {
      'port': _pairPort.text.isEmpty ? 'auto' : _pairPort.text,
      'code': code,
      if (_connectPort.text.isNotEmpty)
        'connectionPort': int.parse(_connectPort.text),
    });
    if (mounted && _model.shellReady) setState(() => _showPairing = false);
  }

  Future<void> _connect() async {
    if (_connectPort.text.isEmpty || _portError(_connectPort.text) != null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请输入无线调试主页面的连接端口。')));
      return;
    }
    await _model.run('连接调试端口', 'tunnel_adb_connect',
        {'endpoint': _connectPort.text});
  }

  Future<void> _discover() async {
    await _model.run('发现无线调试端口', 'tunnel_adb_discover');
    if (!mounted) return;
    if (_model.pairingEndpoints.length == 1) {
      _pairPort.text = _model.pairingEndpoints.single.split(':').last;
    }
    if (_model.connectEndpoints.length == 1) {
      _connectPort.text = _model.connectEndpoints.single.split(':').last;
    }
    setState(() {});
    if (_model.pairingEndpoints.isEmpty && _model.connectEndpoints.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('未发现端口，请保持无线调试和配对窗口打开，也可手动填写。')));
    }
  }

  Future<void> _send() async {
    final text = _command.text;
    if (!_canRun || !_model.shellReady || text.trim().isEmpty) return;
    _command.clear();
    await _model.command(text);
  }

  bool get _canRun => !_model.busy && !_model.mirrorActive;
  bool get _canConnect => _canRun && !_model.terminalRunning;

  Future<void> _help() => showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
            title: const Text('LADB 本地无线调试'),
            content: const SingleChildScrollView(
              child: Text('1. 打开开发者选项 → 无线调试，并连接 Wi-Fi。\n\n'
                  '2. 使用系统分屏或浮窗，让隧道和设置同时可见。打开“使用配对码配对设备”，保持该窗口不关闭。\n\n'
                  '3. 输入该窗口的配对端口和 6 位配对码。端口留空可尝试自动发现。连接端口来自无线调试主页面，与配对端口不同，可先留空。\n\n'
                  '4. 点击配对，等待“ADB shell 已就绪 · uid 2000”。若已配对但连接未找到，填写连接端口后点击手动连接。\n\n'
                  '5. 启动终端，输入 id 验证。这里执行的是本机持久 ADB shell，不依赖 PC 或无障碍。命令无需 adb shell 前缀；支持连续命令、cd 和持续输出，可用中断按钮结束当前命令。\n\n'
                  '本地和远程使用同一套 ADB 密钥；本地配对不会自动启用投屏。首次信任仍需系统配对码，不能跳过 Android 授权。\n\n'
                  '历史、书签和终端输出仅保留在内存。\n\n'
                  '本功能基于 tytydraco/LADB 的无线调试与终端流程，感谢上游贡献者。'),
            ),
            actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('知道了'))],
          ));

  Future<void> _chooseCommand(bool bookmarks) async {
    final entries = bookmarks ? _model.bookmarks : _model.history;
    final selected = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(title: Text(bookmarks ? '命令书签 · 本次运行' : '命令历史 · 本次运行')),
          if (entries.isEmpty) const ListTile(title: Text('暂无命令')),
          Flexible(
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: entries.length,
              itemBuilder: (context, index) => ListTile(
                leading: Icon(bookmarks ? Icons.bookmark_outline : Icons.history),
                title: Text(entries[index], maxLines: 3, overflow: TextOverflow.ellipsis),
                onTap: () => Navigator.pop(context, entries[index]),
              ),
            ),
          ),
        ]),
      ),
    );
    if (!mounted || selected == null) return;
    _command.text = selected;
    _command.selection = TextSelection.collapsed(offset: selected.length);
  }

  void _menu(String action) {
    switch (action) {
      case 'history':
        unawaited(_chooseCommand(false));
        break;
      case 'bookmarks':
        unawaited(_chooseCommand(true));
        break;
      case 'bookmark':
        _model.addBookmark(_command.text);
        break;
      case 'copy':
        unawaited(Clipboard.setData(ClipboardData(text: _model.output)));
        break;
      case 'clear':
        unawaited(_model.clearOutput());
        break;
      case 'font':
        setState(() => _fontSize = _fontSize == 13 ? 17 : 13);
        break;
      case 'scroll':
        setState(() => _autoScroll = !_autoScroll);
        break;
      case 'stop':
        unawaited(_model.run('停止本地终端', 'tunnel_adb_stop'));
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final colors = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          Row(children: [
            const Icon(Icons.terminal),
            const SizedBox(width: 10),
            Expanded(child: Text('LADB · 本机 ADB', style: Theme.of(context).textTheme.titleLarge)),
            IconButton(onPressed: _model.openSettings, icon: const Icon(Icons.settings_outlined), tooltip: '无线调试设置'),
            IconButton(onPressed: _help, icon: const Icon(Icons.help_outline), tooltip: '配对教程'),
          ]),
          Text(_model.status, style: TextStyle(color: _model.shellReady ? colors.primary : null)),
          if (_model.mirrorActive)
            const Padding(padding: EdgeInsets.only(top: 8), child: Text('远程 ADB 投屏正在使用连接。切回普通投屏后再操作本地终端。')),
          if (_model.busy) ...[
            const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: LinearProgressIndicator()),
            Row(children: [
              Expanded(child: Text(_model.operation.isEmpty ? 'ADB 正在处理操作…' : _model.operation)),
              if (_model.operation.isNotEmpty)
                TextButton(onPressed: _model.cancel, child: const Text('取消')),
            ]),
          ],
          if (_model.error.isNotEmpty)
            Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: SelectableText(_model.error, style: TextStyle(color: colors.error))),
          Wrap(spacing: 8, children: [
            TextButton.icon(onPressed: () => setState(() => _showPairing = !_showPairing), icon: const Icon(Icons.link), label: Text(_showPairing ? '收起配对' : '配对 / 手动连接')),
            TextButton.icon(onPressed: _canConnect ? () => _model.run('扫描已授权连接', 'tunnel_adb_start') : null, icon: const Icon(Icons.wifi_find), label: const Text('扫描连接')),
          ]),
          if (_showPairing) _pairingForm(),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('允许远程控制 / ADB 配对'),
            subtitle: const Text('允许已授权、加密的 PC 会话请求配对及控制；不会自动打开无障碍或投屏。'),
            value: _model.remoteControlAllowed == true,
            onChanged: _model.remoteControlAllowed == null ? null : _model.setRemoteControlAllowed,
          ),
          const Divider(),
          Wrap(spacing: 8, children: [
            TextButton.icon(
                onPressed: _canConnect && _model.shellReady ? () => _model.run('启动本地终端', 'tunnel_adb_local_shell') : null,
                icon: const Icon(Icons.play_arrow), label: const Text('启动终端')),
            TextButton.icon(
                onPressed: _canRun && _model.terminalRunning ? () => _model.run('中断当前命令', 'tunnel_adb_terminal_interrupt') : null,
                icon: const Icon(Icons.pause), label: const Text('中断命令')),
            TextButton.icon(
                onPressed: _canRun && _model.terminalRunning ? () => _model.run('停止本地终端', 'tunnel_adb_stop') : null,
                icon: const Icon(Icons.stop), label: const Text('停止终端')),
          ]),
          if (_model.terminalRunning)
            const Text('本地终端正在占用 ADB。停止终端后，PC 可接管配对与投屏。'),
          Row(children: [
            const Expanded(child: Text('Shell 终端')),
            TextButton(onPressed: _model.shellReady && _canRun ? () => _model.command('id') : null, child: const Text('验证 id')),
            PopupMenuButton<String>(
              tooltip: '终端选项',
              onSelected: _menu,
              itemBuilder: (context) => [
                const PopupMenuItem(value: 'history', child: Text('命令历史')),
                const PopupMenuItem(value: 'bookmarks', child: Text('命令书签')),
                const PopupMenuItem(value: 'bookmark', child: Text('收藏输入框中的命令')),
                const PopupMenuItem(value: 'copy', child: Text('复制输出')),
                const PopupMenuItem(value: 'clear', child: Text('清空输出')),
                const PopupMenuItem(value: 'font', child: Text('切换终端字号')),
                PopupMenuItem(value: 'scroll', child: Text(_autoScroll ? '关闭自动滚动' : '开启自动滚动')),
                PopupMenuItem(value: 'stop', enabled: _canRun, child: const Text('停止本地终端（保留 ADB 服务）')),
              ],
            ),
          ]),
          Container(
            height: 260,
            decoration: BoxDecoration(color: const Color(0xff17202a), borderRadius: BorderRadius.circular(8)),
            child: Scrollbar(
              controller: _outputScroll,
              child: SingleChildScrollView(
                controller: _outputScroll,
                padding: const EdgeInsets.all(12),
                child: SizedBox(width: double.infinity, child: SelectableText(
                  _model.output.isEmpty ? '等待本机 ADB 连接。连接后输入 id 查看 shell 身份。' : _model.output,
                  style: TextStyle(fontFamily: 'monospace', fontSize: _fontSize, color: const Color(0xffe5edf5)),
                )),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Expanded(child: TextField(
              controller: _command,
              enabled: _model.shellReady && _canRun,
              autocorrect: false,
              enableSuggestions: false,
              maxLength: 8192,
              minLines: 1,
              maxLines: 4,
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => _send(),
              decoration: const InputDecoration(labelText: 'Shell 命令', hintText: '例如：id', border: OutlineInputBorder(), counterText: ''),
            )),
            IconButton(onPressed: _model.shellReady && _canRun ? _send : null, icon: const Icon(Icons.send), tooltip: '执行命令'),
          ]),
          const Padding(padding: EdgeInsets.only(top: 8), child: Text('持久 shell 在手机本机执行，保留当前目录与环境；输出不发送到 PC。')),
        ],
      ),
    );
  }

  Widget _pairingForm() => Card(
        margin: const EdgeInsets.symmetric(vertical: 8),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Form(
            key: _form,
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Text('保持系统配对窗口打开，可使用分屏同时输入。'),
              Align(alignment: Alignment.centerLeft, child: TextButton.icon(
                onPressed: _canConnect ? _discover : null,
                icon: const Icon(Icons.radar), label: const Text('自动发现端口'),
              )),
              if (_model.pairingEndpoints.length > 1)
                Wrap(spacing: 6, children: _model.pairingEndpoints.map((endpoint) => ActionChip(
                  label: Text('配对 $endpoint'),
                  onPressed: _canConnect ? () => _pairPort.text = endpoint.split(':').last : null,
                )).toList()),
              if (_model.connectEndpoints.length > 1)
                Wrap(spacing: 6, children: _model.connectEndpoints.map((endpoint) => ActionChip(
                  label: Text('连接 $endpoint'),
                  onPressed: _canConnect ? () => _connectPort.text = endpoint.split(':').last : null,
                )).toList()),
              const SizedBox(height: 12),
              TextFormField(
                controller: _pairPort,
                enabled: _canConnect,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(5)],
                validator: _portError,
                decoration: const InputDecoration(labelText: '配对端口', hintText: '留空自动发现', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _pairCode,
                enabled: _canConnect,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                validator: (value) => RegExp(r'^\d{6}$').hasMatch(value ?? '') ? null : '请输入 6 位配对码',
                decoration: const InputDecoration(labelText: '配对码', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _connectPort,
                enabled: _canConnect,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(5)],
                validator: _portError,
                decoration: const InputDecoration(labelText: '连接端口（可选）', helperText: '无线调试主页面中的端口，与配对端口不同', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              Wrap(spacing: 8, runSpacing: 8, children: [
                ElevatedButton(onPressed: _canConnect ? _pair : null, child: const Text('配对并连接')),
                OutlinedButton(onPressed: _canConnect ? _connect : null, child: const Text('手动连接')),
              ]),
            ]),
          ),
        ),
      );
}
