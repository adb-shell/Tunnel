import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Local P0 diagnostics only. Native BuildConfig remains the authority for availability.
class AdbMirrorProbeCard extends StatefulWidget {
  final ValueChanged<bool>? onActiveChanged;
  final bool Function()? isPageVisible;
  final bool blocked;

  const AdbMirrorProbeCard({
    Key? key,
    this.onActiveChanged,
    this.isPageVisible,
    this.blocked = false,
  }) : super(key: key);

  @override
  State<AdbMirrorProbeCard> createState() => _AdbMirrorProbeCardState();
}

class _AdbMirrorProbeCardState extends State<AdbMirrorProbeCard>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('mChannel');
  final _serial = TextEditingController();
  Timer? _pollTimer;
  bool _enabled = false;
  bool _active = false;
  bool _mayOwnRun = false;
  bool _starting = false;
  bool _cancelling = false;
  bool _pollInFlight = false;
  bool _foreground = true;
  bool _disposed = false;
  bool _reportedActive = false;
  int _generation = 0;
  String _phase = 'IDLE';
  String _reason = '';
  bool _shellVerified = false;
  bool _helperAuthenticated = false;
  bool _hasVideoCapabilities = false;
  int _configs = 0;
  int _frames = 0;
  int _keyFrames = 0;
  int _bytes = 0;
  int _width = 0;
  int _height = 0;

  bool get _visible => widget.isPageVisible?.call() ?? true;
  bool get _locked => _mayOwnRun || _active || _starting || _cancelling;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    _foreground = lifecycle == null ||
        lifecycle == AppLifecycleState.resumed ||
        lifecycle == AppLifecycleState.inactive;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      _refreshStatus();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _foreground = true;
      _refreshStatus();
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.detached) {
      _foreground = false;
      _stopPolling();
      if (_locked) _cancel();
    }
  }

  void _reportActive() {
    final value = _locked;
    if (_reportedActive == value || _disposed) return;
    _reportedActive = value;
    widget.onActiveChanged?.call(value);
  }

  void _stopPolling() {
    _pollTimer?.cancel();
    _pollTimer = null;
  }

  void _ensurePolling() {
    if (!_enabled || !_locked || !_foreground || _disposed) return;
    _pollTimer ??= Timer.periodic(const Duration(milliseconds: 500), (_) {
      if (!_visible) {
        _stopPolling();
        _cancel();
      } else {
        _refreshStatus();
      }
    });
  }

  Future<void> _refreshStatus() async {
    if (_disposed || !_foreground || _pollInFlight || _starting || _cancelling) {
      return;
    }
    _pollInFlight = true;
    final generation = _generation;
    try {
      final snapshot = await _channel
          .invokeMapMethod<String, dynamic>('tunnel_adb_p0_status');
      if (!mounted || _disposed || generation != _generation) return;
      _apply(snapshot);
      if (!_visible && _locked) _cancel();
    } catch (_) {
      if (mounted && !_disposed && generation == _generation) {
        setState(() => _reason = 'CHANNEL_UNAVAILABLE');
      }
    } finally {
      // No timeout releases this flag while its native call is still outstanding.
      _pollInFlight = false;
    }
  }

  Future<void> _start() async {
    if (!_enabled || _locked || widget.blocked || !_foreground || !_visible) {
      return;
    }
    final serial = _serial.text.trim();
    if (serial.isEmpty || serial.length > 80) {
      setState(() => _reason = 'TARGET_REQUIRED');
      return;
    }
    final generation = ++_generation;
    setState(() {
      _starting = true;
      _mayOwnRun = true;
      _phase = 'STARTING';
      _reason = '';
      _shellVerified = false;
      _helperAuthenticated = false;
      _hasVideoCapabilities = false;
      _configs = _frames = _keyFrames = _bytes = _width = _height = 0;
    });
    _reportActive();
    _ensurePolling();
    try {
      final snapshot = await _channel.invokeMapMethod<String, dynamic>(
          'tunnel_adb_p0_start', <String, dynamic>{'serial': serial});
      if (!mounted || _disposed || generation != _generation) return;
      _apply(snapshot);
    } on PlatformException catch (error) {
      if (mounted && !_disposed && generation == _generation) {
        if (error.code == 'ADB_LOCAL_BUSY') {
          // Native rejected before allocating a run. Do not cancel another local operation.
          setState(() {
            _mayOwnRun = false;
            _active = false;
            _phase = 'IDLE';
            _reason = 'BUSY';
          });
        } else {
          setState(() => _reason = 'CHANNEL_UNAVAILABLE');
          _cancel();
        }
      }
    } catch (_) {
      if (mounted && !_disposed && generation == _generation) {
        setState(() => _reason = 'CHANNEL_UNAVAILABLE');
        // A failed bridge response does not prove that native startup did not run.
        _cancel();
      }
    } finally {
      if (mounted && !_disposed && generation == _generation) {
        setState(() => _starting = false);
        _reportActive();
        if (_locked) {
          _ensurePolling();
        } else {
          _stopPolling();
        }
      }
    }
  }

  Future<void> _cancel() async {
    if (_disposed || _cancelling || !_locked) return;
    final generation = ++_generation;
    _stopPolling();
    setState(() {
      _starting = false;
      _cancelling = true;
    });
    _reportActive();
    try {
      final snapshot = await _channel
          .invokeMapMethod<String, dynamic>('tunnel_adb_p0_cancel');
      if (!mounted || _disposed || generation != _generation) return;
      _apply(snapshot);
    } catch (_) {
      if (mounted && !_disposed && generation == _generation) {
        setState(() => _reason = 'CHANNEL_UNAVAILABLE');
      }
    } finally {
      if (mounted && !_disposed && generation == _generation) {
        setState(() => _cancelling = false);
        _reportActive();
        if (_locked) {
          _ensurePolling();
        } else {
          _stopPolling();
        }
      }
    }
  }

  void _apply(Map<String, dynamic>? snapshot) {
    if (snapshot == null || snapshot['enabled'] is! bool ||
        snapshot['active'] is! bool) {
      throw const FormatException('Invalid P0 status shape');
    }
    setState(() {
      _enabled = snapshot['enabled'] == true;
      _active = snapshot['active'] == true;
      _mayOwnRun = _active;
      _phase = _safeCode(snapshot['phase']);
      _reason = _safeCode(snapshot['reason']);
      _shellVerified = snapshot['shellIdentityVerified'] == true;
      _helperAuthenticated = snapshot['helperAuthenticated'] == true;
      _hasVideoCapabilities = snapshot['videoCapabilities'] == true;
      _configs = _count(snapshot['configs']);
      _frames = _count(snapshot['frames']);
      _keyFrames = _count(snapshot['keyFrames']);
      _bytes = _count(snapshot['bytes']);
      _width = _count(snapshot['width']);
      _height = _count(snapshot['height']);
    });
    _reportActive();
    if (_locked && _enabled) {
      _ensurePolling();
    } else {
      _stopPolling();
    }
  }

  int _count(dynamic value) => value is num && value.isFinite && value >= 0
      ? value.clamp(0, 9007199254740991).toInt()
      : 0;

  String _safeCode(dynamic value) => value is String &&
          RegExp(r'^[A-Z0-9_]{0,64}$').hasMatch(value)
      ? value
      : 'UNKNOWN';

  String get _phaseLabel {
    if (_cancelling) return '正在停止';
    if (_starting) return '正在启动';
    switch (_phase) {
      case 'IDLE': return '尚未开始';
      case 'VERIFYING': return '正在核实本机 ADB';
      case 'PREPARING': return '正在准备验证程序';
      case 'STARTING': return '正在启动验证程序';
      case 'AUTHENTICATING': return '正在验证本机连接';
      case 'STREAMING':
      case 'CAPTURING': return '正在接收编码数据';
      case 'FINISHED': return '本次诊断结束';
      case 'CANCELLED': return '已停止';
      case 'FAILED': return '诊断未通过';
      default: return _active ? '正在验证' : '等待本机操作';
    }
  }

  String get _reasonLabel {
    switch (_reason) {
      case '':
      case 'NONE':
      case 'OK': return '';
      case 'TARGET_REQUIRED':
      case 'TARGET_NOT_LOCAL': return '请填写当前手机的无线调试连接地址和连接端口。';
      case 'ADB_MISSING':
      case 'ADB_NOT_EXECUTABLE': return '当前安装包缺少可执行的 ADB 组件。';
      case 'SERVER_UNAVAILABLE':
      case 'TRANSPORT_UNAVAILABLE': return '请先在本机 ADB 页面完成配对并连接。';
      case 'UNSUPPORTED_ANDROID': return '此原型仅接受 Android API 30–36，具体机型仍需验证。';
      case 'BUSY': return '另一项本机 ADB 操作正在进行，请稍后重试。';
      case 'HELPER_ASSET_INVALID_OR_MISSING': return '验证程序尚未打包或文件校验不符。';
      case 'HELPER_PREPARE_FAILED': return '本机验证程序准备失败，请重新连接 ADB 后重试。';
      case 'HELPER_REMOTE_HASH_INVALID': return '验证程序传输后的校验未通过，已停止启动。';
      case 'SHELL_UID_REQUIRED':
      case 'IDENTITY_INVALID':
      case 'IDENTITY_EXPIRED': return '未核实到有效的本机 ADB shell 身份。';
      case 'TIMEOUT':
      case 'DEADLINE_EXCEEDED':
      case 'PROCESS_TIMEOUT': return '本次验证超时。';
      case 'CANCELLED':
      case 'PROCESS_INTERRUPTED': return '本次验证已取消。';
      case 'CHANNEL_UNAVAILABLE': return '本机诊断暂时无法响应，请停止后重试。';
      case 'HELPER_OR_TRANSPORT_FAILED':
      case 'CONTROL_CHANNEL_FAILED':
      case 'HELPER_IO_FAILED': return '验证程序或本机连接中断，诊断已停止。';
      case 'HELPER_OUTPUT_LIMIT':
      case 'OUTPUT_LIMIT': return '验证程序输出超出限制，诊断已停止。';
      case 'NO_ENCODED_VIDEO': return '未收到完整的编码视频验证数据。';
      case 'RESOURCE_CLEANUP_FAILED':
      case 'RESTART_APP_REQUIRED': return '上次验证尚有资源未释放，请重新启动应用。';
      case 'ENCODED_SAMPLE_RECEIVED': return '已收到编码视频样本；PC 解码和呈现仍未验证。';
      case 'VERIFIED_SHELL_ONLY': return '已核实 shell；投屏和输入能力还需要独立验证。';
      default: return '本次诊断尚未完成全部验证，请查看状态和计数。';
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_enabled) return const SizedBox.shrink();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('ADB 投屏本机验证', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const Text('仅验证本机连接和编码数据，尚未接入 PC 画面。'),
            const SizedBox(height: 12),
            TextField(
              controller: _serial,
              enabled: !_locked && !widget.blocked,
              autocorrect: false,
              enableSuggestions: false,
              maxLength: 80,
              decoration: const InputDecoration(
                labelText: '本机无线调试连接地址',
                hintText: '127.0.0.1:连接端口',
                helperText: '使用无线调试首页的连接端口，不是配对码窗口的端口。',
                helperMaxLines: 3,
              ),
            ),
            Wrap(
              spacing: 12,
              children: [
                ElevatedButton(
                  onPressed: _locked || widget.blocked ? null : _start,
                  child: const Text('开始 10 秒验证'),
                ),
                TextButton(
                  onPressed: _locked && !_cancelling ? _cancel : null,
                  child: const Text('停止验证'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text('状态：$_phaseLabel'),
            if (_reasonLabel.isNotEmpty) Text(_reasonLabel),
            const SizedBox(height: 8),
            Text('Shell 身份：${_shellVerified ? "已核实" : "未核实"}'),
            Text('验证程序连接：${_helperAuthenticated ? "已认证" : "未认证"}'),
            Text('视频能力声明：${_hasVideoCapabilities ? "已收到" : "未收到"}'),
            Text('配置 $_configs · 编码帧 $_frames · 关键帧 $_keyFrames'),
            Text('接收 $_bytes 字节 · 尺寸 $_width × $_height'),
            const Text('PC 解码：未验证；PC 呈现：未验证。'),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    _stopPolling();
    WidgetsBinding.instance.removeObserver(this);
    if (_locked) {
      // No setState/response handler after disposal; native owns its own deadline/watchdog.
      _channel.invokeMethod<void>('tunnel_adb_p0_cancel').catchError((_) {});
    }
    if (_reportedActive) {
      final callback = widget.onActiveChanged;
      WidgetsBinding.instance.addPostFrameCallback((_) => callback?.call(false));
    }
    _serial.dispose();
    super.dispose();
  }
}
