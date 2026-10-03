import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_model.dart';

/// Phone-local LADB controller. Uses the same native key and transport as remote
/// ADB, but never forwards terminal text or output to a remote-control peer.
class LocalAdbModel extends ChangeNotifier {
  Timer? _poll;
  bool _disposed = false;
  bool _refreshing = false;
  bool _working = false;
  bool _active = false;
  Map<String, dynamic> _state = {};
  String error = '';
  String operation = '';
  bool? remoteControlAllowed;
  final List<String> history = [];
  final List<String> bookmarks = ['id', 'getprop ro.build.version.release'];
  List<String> pairingEndpoints = [];
  List<String> connectEndpoints = [];

  bool get supported => _state['supported'] == true;
  bool get binaryAvailable => _state['binaryAvailable'] == true;
  bool get binaryExecutable => _state['binaryExecutable'] == true;
  bool get paired => _state['paired'] == true;
  bool get shellReady => _state['shellReady'] == true;
  bool get terminalRunning => _state['terminalRunning'] == true;
  bool get busy => _working || _state['busy'] == true;
  bool get mirrorActive => _state['mirrorActive'] == true;
  String get output => _state['output'] as String? ?? '';
  String get status => !supported
      ? '需要 Android 11 或更新版本'
      : !binaryAvailable || !binaryExecutable
          ? '内置 ADB 不可用，请检查 APK 原生组件'
          : shellReady
              ? 'ADB shell 已就绪 · uid 2000'
              : _state['pairing'] == true
                  ? '正在配对'
                  : paired
                      ? '已配对，等待连接调试端口'
                      : '尚未连接 ADB';

  Future<dynamic> _call(String method, [dynamic args]) =>
      platformFFI.invokeMethod(method, args);

  Future<void> initialize() async {
    try {
      _accept(await _call('tunnel_adb_init'));
    } on PlatformException catch (e) {
      _setError(e.code);
    } catch (_) {
      _setError('CHANNEL_UNAVAILABLE');
    }
  }

  void setActive(bool active) {
    _active = active;
    _poll?.cancel();
    _poll = null;
    if (active && !_disposed) {
      unawaited(refresh());
      _poll = Timer.periodic(const Duration(seconds: 1), (_) => refresh());
    }
  }

  Future<void> refresh() async {
    if (_disposed || _refreshing) return;
    _refreshing = true;
    try {
      _accept(await _call('tunnel_adb_status'));
      final option = await bind.mainGetOption(key: 'enable-keyboard');
      if (!_disposed) {
        remoteControlAllowed = option != 'N';
        notifyListeners();
      }
    } catch (_) {
      _setError('CHANNEL_UNAVAILABLE');
    } finally {
      _refreshing = false;
    }
  }

  void _accept(dynamic result) {
    if (_disposed || result is! Map) return;
    _state = Map<String, dynamic>.from(result);
    final nativeError = _state['lastError'] as String? ?? '';
    if (nativeError.isNotEmpty) error = errorText(nativeError);
    notifyListeners();
  }

  void _setError(String code) {
    if (_disposed) return;
    error = errorText(code);
    notifyListeners();
  }

  Future<void> run(String label, String method, [dynamic args]) async {
    if (_disposed || busy || mirrorActive) return;
    _working = true;
    operation = label;
    error = '';
    notifyListeners();
    // Native operations have their own bounded deadlines. Do not abandon an
    // in-flight pairing when the user opens Settings or slides to the home page.
    try {
      final result = await _call(method, args);
      if (method == 'tunnel_adb_discover' && result is Map) {
        pairingEndpoints = (result['pairingEndpoints'] as List? ?? []).whereType<String>().toList();
        connectEndpoints = (result['connectEndpoints'] as List? ?? []).whereType<String>().toList();
        final nativeError = result['lastError'] as String? ?? '';
        if (nativeError.isNotEmpty) _setError(nativeError);
      } else {
        _accept(result);
      }
    } on PlatformException catch (e) {
      _setError(e.code);
    } catch (_) {
      _setError('CHANNEL_UNAVAILABLE');
    } finally {
      _working = false;
      operation = '';
      if (!_disposed) {
        notifyListeners();
        if (_active) unawaited(refresh());
      }
    }
  }

  Future<void> cancel() async {
    if (!_working) return; // Never cancel an operation owned by a remote peer.
    try {
      _accept(await _call('tunnel_adb_cancel'));
    } catch (_) {
      _setError('CANCEL_FAILED');
    }
  }

  Future<void> openSettings() async {
    try {
      if (await _call('tunnel_adb_open_settings') != true) {
        _setError('SETTINGS_UNAVAILABLE');
      }
    } catch (_) {
      _setError('SETTINGS_UNAVAILABLE');
    }
  }

  Future<void> setRemoteControlAllowed(bool enabled) async {
    try {
      await bind.mainSetOption(key: 'enable-keyboard', value: enabled ? 'Y' : 'N');
      await refresh();
    } catch (_) {
      _setError('CONTROL_PERMISSION_FAILED');
    }
  }

  Future<void> clearOutput() async {
    try {
      await _call('tunnel_adb_clear_output');
      await refresh();
    } catch (_) {
      _setError('CHANNEL_UNAVAILABLE');
    }
  }

  Future<void> command(String text) async {
    if (!shellReady || busy || mirrorActive || text.trim().isEmpty) return;
    history.remove(text);
    history.insert(0, text);
    if (history.length > 30) history.removeLast();
    await run('执行 shell 命令', 'tunnel_adb_command', text);
  }

  void addBookmark(String command) {
    final value = command.trim();
    if (value.isEmpty || bookmarks.contains(value)) return;
    bookmarks.add(value);
    notifyListeners();
  }

  static String errorText(String code) {
    const errors = {
      'PAIR_CODE_INVALID': '配对码必须为 6 位数字。',
      'PAIR_CODE_REJECTED': '系统拒绝了配对码，请使用当前仍打开的配对窗口中的新配对码。',
      'PAIR_PORT_UNREACHABLE': '无法访问配对端口，请保持系统的“使用配对码配对设备”窗口打开。',
      'PAIR_ADDRESS_REQUIRED': '未发现配对端口，请手动输入配对窗口显示的端口。',
      'PAIR_RESPONSE_REJECTED': '配对端口关闭或返回无效响应，请重新打开系统配对窗口。',
      'PAIRED_CONNECT_REQUIRED': '配对已完成，但未发现连接端口；请填写无线调试主页面的连接端口。',
      'PAIRED_CONNECT_FAILED': '配对已完成，但连接失败；请核对连接端口，它不是配对端口。',
      'CONNECT_ADDRESS_REQUIRED': '未发现已授权的调试连接，请先配对，或手动填写连接端口。',
      'CONNECT_FAILED': '调试连接失败，请确认无线调试保持开启且连接端口未变化。',
      'ADB_BUSY': '其他本地或远程 ADB 操作正在使用连接，请稍后重试。',
      'ADB_LOCAL_BUSY': '其他本地或远程 ADB 操作正在使用连接，请稍后重试。',
      'ADB_PROBE_BUSY': '请先停止正在运行的投屏诊断。',
      'CANCELLED': '操作已取消。',
      'IDENTITY_EXPIRED': 'ADB 连接已失效，请重新扫描连接。',
      'TRANSPORT_UNAVAILABLE': '尚未连接 ADB，请先配对或连接。',
      'CHANNEL_UNAVAILABLE': '无法读取 Android ADB 组件，请确认安装的是对应新版 APK。',
      'SETTINGS_UNAVAILABLE': '无法打开系统设置，请手动进入开发者选项 → 无线调试。',
      'COMMAND_FAILED': '命令返回非零状态，请查看终端输出。',
      'TIMEOUT': '操作超时，请检查无线调试是否保持开启。',
      'TERMINAL_INPUT_BUSY': '终端输入繁忙，请稍后重试或中断当前命令。',
      'TERMINAL_NOT_RUNNING': '请先启动本地终端。',
      'TERMINAL_STOPPING': '终端仍在停止，请稍候再点停止终端，释放后 PC 才能接管。',
      'SERVER_START_FAILED': '内置 ADB 服务启动失败，请查看本机输出中的原生错误。',
      'ADB_BINARY_MISSING': 'APK 缺少内置 libadb.so，请用正式构建入口重新打包。',
      'OPERATION_TIMEOUT': '操作超时，请检查无线调试是否保持开启及端口是否正确。',
      'CONTROL_PERMISSION_FAILED': '控制权限未能保存，请重试并确认开关状态。',
    };
    return '${errors[code] ?? '操作未完成，请按错误码检查本地 ADB。'} ($code)';
  }

  @override
  void dispose() {
    _disposed = true;
    _poll?.cancel();
    history.clear();
    bookmarks.clear();
    super.dispose();
  }
}
