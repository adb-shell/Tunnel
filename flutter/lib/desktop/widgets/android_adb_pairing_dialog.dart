import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../common.dart';
import '../../models/android_adb_pairing_model.dart';
import '../../models/model.dart';

Future<void> showAndroidAdbPairingDialog(FFI ffi) async {
  const tag = 'android-adb-pairing';
  ffi.dialogManager.dismissByTag(tag);
  ffi.inputModel.beginLocalDialog();
  try {
    await ffi.dialogManager.show<void>(
      (_, close, context) => _AndroidAdbPairingOverlay(ffi: ffi, onClose: () => close()),
      tag: tag,
    );
  } catch (_) {
    if (!ffi.closed) showToast('配对窗口未能打开，请等待远程画面就绪后重试。');
  } finally {
    ffi.inputModel.endLocalDialog();
  }
}

// The manager requires CustomAlertDialog, but dragging needs full-window
// constraints instead of AlertDialog's intrinsic content sizing.
class _AndroidAdbPairingOverlay extends CustomAlertDialog {
  const _AndroidAdbPairingOverlay({required this.ffi, required this.onClose})
      : super(content: const SizedBox.shrink());
  final FFI ffi;
  final VoidCallback onClose;
  @override
  Widget build(BuildContext context) => AndroidAdbPairingDialog(ffi: ffi, onClose: onClose);
}

class AndroidAdbPairingDialog extends StatefulWidget {
  const AndroidAdbPairingDialog({Key? key, required this.ffi, required this.onClose})
      : super(key: key);
  final FFI ffi;
  final VoidCallback onClose;
  @override
  State<AndroidAdbPairingDialog> createState() => _AndroidAdbPairingDialogState();
}

class _AndroidAdbPairingDialogState extends State<AndroidAdbPairingDialog> {
  final _form = GlobalKey<FormState>();
  final _cardKey = GlobalKey();
  final _port = TextEditingController();
  final _code = TextEditingController();
  final _connectPort = TextEditingController();
  final _scope = FocusScopeNode(debugLabel: 'ADB local dialog');
  final _portFocus = FocusNode(debugLabel: 'ADB pairing port');
  Offset _offset = Offset.zero;
  bool _connectOnly = false;
  bool _manualPort = false;
  bool _showCode = false;
  AndroidAdbPairingModel get _model => widget.ffi.androidModeModel.pairing;

  @override
  void initState() {
    super.initState();
    _model.addListener(_onPairingChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _scope.requestFocus(_portFocus);
    });
  }

  void _onPairingChanged() {
    if (mounted && _model.phase == 'PAIRED_CONNECT_REQUIRED') {
      setState(() { _connectOnly = true; _manualPort = true; });
    }
  }

  @override
  void dispose() {
    _model.removeListener(_onPairingChanged);
    _model.cancel();
    _port.dispose();
    _code.clear();
    _code.dispose();
    _connectPort.dispose();
    _portFocus.dispose();
    _scope.dispose();
    super.dispose();
  }

  String? get _blockedReason {
    final mode = widget.ffi.androidModeModel;
    if (widget.ffi.closed) return '远程连接已断开，请重新连接手机。';
    if (mode.usingAdb) return 'ADB 投屏正在运行，无需重复配对。请先退出投屏再重新配对。';
    if (mode.busy || mode.inputFrozen) return '投屏模式正在切换，请等待完成后再配对。';
    return null;
  }

  void _submit() {
    if (_model.busy || _blockedReason != null) return;
    if (_form.currentState?.validate() != true) return;
    final connectPort = (_manualPort || _connectOnly) ? _connectPort.text.trim() : '';
    if (_connectOnly) {
      _model.authorize(connectPort: connectPort);
    } else {
      _model.pair(port: _port.text.trim(), code: _code.text.trim(), connectPort: connectPort);
    }
    _code.clear();
  }

  InputDecoration _decoration(String label, {String? hint}) => InputDecoration(
    labelText: label, hintText: hint, isDense: true,
    border: const OutlineInputBorder(),
    contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
  );

  Widget _portField(TextEditingController controller, String label, {bool optional = false}) =>
      TextFormField(
    controller: controller,
    focusNode: optional ? null : _portFocus,
    enabled: !_model.busy,
    keyboardType: TextInputType.number,
    textInputAction: optional ? TextInputAction.done : TextInputAction.next,
    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(5)],
    decoration: _decoration(label, hint: optional ? '留空自动查找' : null),
    onFieldSubmitted: optional ? (_) => _submit() : null,
    validator: (value) {
      final text = (value ?? '').trim();
      if (optional && text.isEmpty) return null;
      return AndroidAdbPairingModel.validPort(text) ? null : '请输入 1–65535 的端口';
    },
  );

  Widget _modeButton(String label, bool connectOnly) => OutlinedButton(
    style: OutlinedButton.styleFrom(
      backgroundColor: _connectOnly == connectOnly
          ? Theme.of(context).colorScheme.primary.withOpacity(0.10) : null,
      side: BorderSide(color: _connectOnly == connectOnly
          ? Theme.of(context).colorScheme.primary : Theme.of(context).dividerColor),
    ),
    onPressed: _model.busy ? null : () => setState(() {
      _connectOnly = connectOnly;
      _code.clear();
      _form.currentState?.reset();
    }),
    child: Text(label),
  );

  Widget _contents(BuildContext context) {
    final theme = Theme.of(context);
    final blocked = _blockedReason;
    final statusColor = _model.errorText.isNotEmpty ? theme.colorScheme.error
        : _model.phase == 'VERIFIED' ? Colors.green : theme.colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.all(20),
      child: Form(key: _form, child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(children: [
            Expanded(child: _modeButton('首次配对', false)),
            const SizedBox(width: 8),
            Expanded(child: _modeButton('已配对，连接', true)),
          ]),
          const SizedBox(height: 16),
          Text(_connectOnly ? '打开手机无线调试。已配对过的手机可以直接连接。'
              : '手机：无线调试 → 使用配对码配对设备。保持配对窗口打开。',
              style: theme.textTheme.bodySmall),
          const SizedBox(height: 16),
          if (!_connectOnly) ...[
            _portField(_port, '配对端口'),
            const SizedBox(height: 14),
            TextFormField(
              controller: _code, enabled: !_model.busy, obscureText: !_showCode,
              enableSuggestions: false, autocorrect: false,
              keyboardType: TextInputType.number, textInputAction: TextInputAction.done,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
              decoration: _decoration('6 位配对码').copyWith(suffixIcon: IconButton(
                tooltip: _showCode ? '隐藏配对码' : '显示配对码',
                onPressed: () => setState(() => _showCode = !_showCode),
                icon: Icon(_showCode ? Icons.visibility_off_outlined : Icons.visibility_outlined, size: 20),
              )),
              onFieldSubmitted: (_) => _submit(),
              validator: (value) => RegExp(r'^\d{6}$').hasMatch(value ?? '') ? null : '请输入 6 位配对码',
            ),
            Align(alignment: Alignment.centerLeft, child: TextButton.icon(
              onPressed: _model.busy ? null : () => setState(() => _manualPort = !_manualPort),
              icon: Icon(_manualPort ? Icons.expand_less : Icons.expand_more, size: 18),
              label: const Text('手动指定连接端口'),
            )),
          ],
          if (_connectOnly || _manualPort) ...[
            _portField(_connectPort, '连接端口（可选）', optional: true),
            const SizedBox(height: 6),
            Text('连接端口在无线调试主页，与配对端口不同。', style: theme.textTheme.bodySmall),
            const SizedBox(height: 12),
          ],
          if (blocked != null) Text(blocked, style: TextStyle(color: theme.colorScheme.error)),
          if (_model.phase != 'IDLE') ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: statusColor.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(8)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (_model.busy) ...[
                  const LinearProgressIndicator(minHeight: 2),
                  const SizedBox(height: 10),
                ],
                Text(_model.statusText, style: TextStyle(color: statusColor, fontSize: 13)),
                if (_model.errorText.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  SelectableText(_model.errorText, style: TextStyle(color: statusColor, fontSize: 12)),
                ],
              ]),
            ),
          ],
          const SizedBox(height: 16),
          Row(children: [
            if (_model.busy) TextButton(onPressed: _model.cancelling ? null : _model.cancel,
                child: const Text('取消')),
            const Spacer(),
            ElevatedButton(onPressed: _model.busy || blocked != null ? null : _submit,
                child: Text(_connectOnly ? '连接并授权' : '配对并连接')),
          ]),
          const SizedBox(height: 10),
          Text('授权仅供本次远控使用；断线、撤销或退出 ADB 投屏后结束。',
              style: theme.textTheme.bodySmall),
        ],
      )),
    );
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: Listenable.merge([_model, widget.ffi.androidModeModel]),
    builder: (context, _) => LayoutBuilder(builder: (context, constraints) {
      final width = (constraints.maxWidth - 24).clamp(0.0, 410.0).toDouble();
      final limitX = (constraints.maxWidth - width) / 2;
      final card = _cardKey.currentContext?.findRenderObject();
      final cardHeight = card is RenderBox && card.hasSize ? card.size.height : constraints.maxHeight;
      final limitY = ((constraints.maxHeight - cardHeight) / 2).clamp(0.0, double.infinity).toDouble();
      final position = Offset(_offset.dx.clamp(-limitX, limitX).toDouble(),
          _offset.dy.clamp(-limitY, limitY).toDouble());
      return FocusScope(
        node: _scope, autofocus: true,
        onKeyEvent: (_, event) {
          if (event.logicalKey == LogicalKeyboardKey.escape) {
            if (event is KeyDownEvent) widget.onClose();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: MouseRegion(opaque: true, child: Center(child: Transform.translate(
          offset: position,
          child: Material(
            key: _cardKey, elevation: 12, borderRadius: BorderRadius.circular(12),
            clipBehavior: Clip.antiAlias, color: Theme.of(context).dialogBackgroundColor,
            child: SizedBox(width: width, child: ConstrainedBox(
              constraints: BoxConstraints(maxHeight:
                  (constraints.maxHeight - 24).clamp(0.0, double.infinity).toDouble()),
              child: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, children: [
                GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onPanUpdate: (details) => setState(() => _offset = position + details.delta),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 10, 8, 6),
                    child: Row(children: [
                      Icon(Icons.adb_rounded, size: 22, color: Theme.of(context).colorScheme.primary),
                      const SizedBox(width: 10),
                      Expanded(child: Text('远程 ADB', style: Theme.of(context).textTheme.titleMedium)),
                      IconButton(tooltip: '关闭', onPressed: widget.onClose,
                          icon: const Icon(Icons.close, size: 20)),
                    ]),
                  ),
                ),
                const Divider(height: 1),
                _contents(context),
              ])),
            )),
          ),
        ))),
      );
    }),
  );
}
