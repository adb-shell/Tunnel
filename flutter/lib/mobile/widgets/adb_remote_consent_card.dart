import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Consent exists only in Android memory and is scoped to the authenticated connection.
class AdbRemoteConsentCard extends StatefulWidget {
  const AdbRemoteConsentCard({Key? key}) : super(key: key);
  @override
  State<AdbRemoteConsentCard> createState() => _AdbRemoteConsentCardState();
}

class _AdbRemoteConsentCardState extends State<AdbRemoteConsentCard> {
  static const channel = MethodChannel('mChannel');
  Timer? _timer;
  bool _polling = false;
  bool _acting = false;
  int _responseGeneration = 0;
  Map<String, dynamic> _state = {};
  String _error = '';
  final _scopes = <String>{'video', 'input'};
  static const _labels = {
    'video': '投屏（必选）', 'input': '键盘和触摸控制',
    'accessibility': '暂停、恢复或关闭本应用无障碍',
    'display': '物理屏幕显示控制', 'snapshot': '无视模式截图',
    'hierarchy': '穿透模式节点画面', 'overlay': '覆盖层控制（取决于设备支持）',
  };

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _refresh());
  }

  Future<void> _refresh() async {
    if (_polling || _acting) return;
    _polling = true;
    final generation = _responseGeneration;
    try {
      final raw = await channel.invokeMethod<String>('tunnel_adb_remote_status');
      final value = Map<String, dynamic>.from(jsonDecode(raw ?? '{}'));
      if (mounted && generation == _responseGeneration) setState(() {
        _state = value;
        _error = '';
      });
    } catch (_) {
      if (mounted && generation == _responseGeneration) setState(() => _error = '远程 ADB 组件尚未就绪');
    } finally { _polling = false; }
  }

  Future<void> _action(String name, [Map<String, dynamic>? arguments]) async {
    _responseGeneration++;
    setState(() { _acting = true; _error = ''; });
    try {
      final raw = await channel.invokeMethod<String>(name, arguments);
      final value = Map<String, dynamic>.from(jsonDecode(raw ?? '{}'));
      if (mounted) setState(() => _state = value);
    } on PlatformException catch (e) {
      if (mounted) setState(() => _error = e.message ?? e.code);
    } catch (_) {
      if (mounted) setState(() => _error = '操作未完成，请刷新后重试');
    } finally { if (mounted) setState(() => _acting = false); }
  }

  @override
  void dispose() { _timer?.cancel(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final pending = (_state['pendingConsentConnId'] as num?)?.toInt() ?? 0;
    final owner = (_state['ownerConnId'] as num?)?.toInt() ?? 0;
    final consent = (_state['consentConnId'] as num?)?.toInt() ?? 0;
    final remaining = (_state['consentRemainingSeconds'] as num?)?.toInt() ?? 0;
    return Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(
      crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('远程 ADB 授权', style: TextStyle(fontWeight: FontWeight.bold)),
        Text('状态：${_state['phase'] ?? '尚未请求'} · 来源：${_state['actualFrameSource'] ?? 'NONE'}'),
        if (owner > 0) Text('正在授权的连接：$owner；关闭此页面不会结束会话。'),
        if (consent > 0 && remaining > 0) Text('连接 $consent 已获本机授权，剩余 $remaining 秒。'),
        const Text('先在本机完成无线调试配对，再由 PC 顶栏请求投屏。授权只对本次连接有效，10 分钟后自动撤销。'),
        if (pending > 0) ...[
          Text('连接 $pending 请求 ADB 控制，请仅同意你正在使用的 PC。'),
          for (final entry in _labels.entries)
            CheckboxListTile(dense: true, contentPadding: EdgeInsets.zero,
              title: Text(entry.value), value: _scopes.contains(entry.key),
              onChanged: entry.key == 'video' || _acting ? null : (value) => setState(() {
                if (value == true) _scopes.add(entry.key); else _scopes.remove(entry.key);
              })),
          ElevatedButton(onPressed: _acting ? null : () => _action('tunnel_adb_remote_grant', {
            'connId': pending, 'scopes': _scopes.toList(), 'ttlSeconds': 600,
          }), child: const Text('同意所选权限，然后在 PC 再次点击投屏')),
        ],
        TextButton(onPressed: _acting ? null : () => _action('tunnel_adb_remote_revoke'),
          child: const Text('立即撤销远程 ADB 授权')),
        if (_error.isNotEmpty) Text(_error, style: const TextStyle(color: Colors.red)),
        if ((_state['code'] ?? '').toString().isNotEmpty) Text(_state['code'].toString()),
      ],
    )));
  }
}
