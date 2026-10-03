import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import 'model.dart';
import 'platform_model.dart';

/// Pairing belongs to this remote window, independently of the video lease.
/// The six-digit code only exists in the outgoing request, never in model state.
class AndroidAdbPairingModel extends ChangeNotifier {
  AndroidAdbPairingModel(this.parent);
  final WeakReference<FFI> parent;
  Timer? _deadline;
  Timer? _ackDeadline;
  String operationId = '';
  String phase = 'IDLE';
  String errorCode = '';
  bool busy = false;
  bool? localAdbReady;
  bool? paired;
  int _revision = -1;
  bool lastEventAccepted = false;
  bool _cancelling = false;
  bool _timedOut = false;
  final Map<String, String> _cancelOperations = {};
  bool _disposed = false;
  bool get cancelling => _cancelling;

  // These failures originate on the PC before any pairing code reaches Android.
  static String? channelErrorText(String code) => const <String, String>{
    'SECURE_CHANNEL_REQUIRED': '电脑与手机尚未建立加密通道，配对请求未发送。请核对服务器密钥与客户端 Key，重新连接后再试。',
    'SECURE_CHANNEL_SERVER_KEY_MISSING': '服务器未返回手机的签名公钥，配对请求未发送。请检查 hbbs 的密钥加载方式及手机公钥注册，再重新连接。',
    'SECURE_CHANNEL_PUBLIC_KEY_INVALID': '客户端配置的服务器 Key 格式无效。请填写服务器 id_ed25519.pub 公钥后重新连接。',
    'SECURE_CHANNEL_SERVER_SIGNATURE_INVALID': '服务器签名校验失败。请核对当前客户端 Key 与实际 hbbs 公钥是否一致，再重新连接。',
    'SECURE_CHANNEL_PEER_ID_MISMATCH': '握手中的设备身份不匹配，配对请求未发送。请核对服务器与目标设备后重新连接。',
    'SECURE_CHANNEL_PEER_SIGNATURE_INVALID': '手机握手签名与服务器登记不一致。请让手机重新注册，再重新连接；无需反复更换配对码。',
    'SECURE_CHANNEL_HANDSHAKE_INVALID': '远程加密握手消息异常，配对请求未发送。请核对电脑、手机和服务器版本后重新连接。',
  }[code];

  // These failures are returned by Android's session/runtime gates, before adb pair.
  static String? permissionErrorText(String code) => const <String, String>{
    'ADB_SESSION_CLOSED': '手机端远程会话已关闭，请重新连接后配对。',
    'ADB_SESSION_NOT_AUTHORIZED': '手机端尚未批准此远程会话，请先完成远控登录或确认连接。',
    'ADB_SESSION_NOT_ENCRYPTED': '手机端确认当前会话未加密，尚未执行配对。请核对两端服务器 Key 后重新连接。',
    'ADB_REMOTE_SESSION_REQUIRED': '请在普通远程控制窗口配对，文件传输、终端或摄像头会话不能执行此操作。',
    'ADB_CONTROL_PERMISSION_REQUIRED': '手机未允许此会话远程控制，尚未执行 ADB 配对。请在手机开启远程控制权限后重试。',
    'ADB_VIEW_ONLY_SESSION': '当前电脑窗口处于仅查看模式，请关闭“仅查看”后重试。',
    'ADB_VIDEO_SUBSCRIPTION_REQUIRED': 'ADB 配对可独立进行，但投屏需要当前远程窗口订阅视频。请恢复视频显示或重新连接。',
    'ANDROID_ADB_SESSION_NOT_REGISTERED': '手机 ADB 服务尚未登记此连接，未执行配对。请重新发起配对；仍失败时请同步更新 APK 和电脑端。',
    'ANDROID_SERVICE_UNAVAILABLE': '手机核心服务尚未就绪或调用失败，未执行配对。请打开手机 Tunnel 并确认服务运行后重试。',
    'RUNTIME_UNAVAILABLE': '手机 ADB 运行组件尚未初始化，请打开手机 Tunnel 后重试。',
    'PERMISSION_OR_SECURE_CHANNEL_REQUIRED': '手机仍使用旧版会话检查，无法区分加密、控制权限与视频订阅失败。请同步更新 APK 和电脑端。',
  }[code];

  static bool validPort(String value) {
    if (!RegExp(r'^\d{1,5}$').hasMatch(value)) return false;
    final port = int.tryParse(value);
    return port != null && port >= 1 && port <= 65535;
  }

  String get statusText {
    switch (phase) {
      case 'SENDING': return '正在发送请求…';
      case 'WAITING_ACK': return '请求已提交，等待手机接收确认…';
      case 'PAIR_STARTING': return '手机正在准备 ADB 配对组件…';
      case 'PAIRING': return '手机正在使用配对端口验证配对码…';
      case 'CONNECTING': return '正在发现连接端口并验证 shell 权限…';
      case 'VERIFYING': return '正在核验手机本地 ADB shell 权限…';
      case 'REVOKING': return '正在撤销本连接的 ADB 授权…';
      case 'CANCELLING': return '已请求手机取消，正在等待确认…';
      case 'CANCEL_UNCONFIRMED': return '取消尚未得到手机确认，请刷新 ADB 状态后重试。';
      case 'VERIFIED': return '已连接，ADB shell 权限已验证';
      case 'PAIRED_CONNECT_REQUIRED':
        return '已配对，尚未连接。请填写无线调试主页上的连接端口后重试。';
      case 'CANCELLED': return errorCode == 'CONSENT_REVOKED' ? '本连接的 ADB 授权已撤销' :
          _timedOut ? '操作超时，手机已确认取消配对' : '手机已确认取消配对';
      case 'DISCONNECTED': return '远程连接已断开，配对请求已终止';
      case 'TIMEOUT': return '手机已接收请求，但操作超时，已请求取消。';
      case 'PAIR_FAILED': return '配对或连接失败';
      default: return '等待输入手机无线调试的配对信息';
    }
  }

  String get errorText {
    if (errorCode.isEmpty || errorCode == 'CONSENT_REVOKED' || errorCode == 'CANCELLED') return '';
    final channelMessage = channelErrorText(errorCode);
    if (channelMessage != null) return '$channelMessage ($errorCode)';
    final permissionMessage = permissionErrorText(errorCode);
    if (permissionMessage != null) return '$permissionMessage ($errorCode)';
    const messages = <String, String>{
      'ADB_PAIR_CODE_INVALID': '配对码必须是当前手机配对窗口显示的 6 位数字。',
      'ADB_PAIR_PORT_INVALID': '配对端口应为 1–65535，不能使用连接端口代替。',
      'ADB_PAIR_FAILED': '配对码或端口已失效。保持手机配对窗口打开，重新输入当前信息。',
      'ADB_CONNECT_REQUIRED': '自动发现未找到连接端口。请填写无线调试主页显示的连接端口。',
      'ADB_BUSY': '手机 ADB 正被本地终端或其他操作占用。请在手机 LADB 页面停止终端，或结束正在执行的操作后重试。',
      'ADB_LIBRARY_MISSING': '手机未找到打包的 ADB 组件，需要重新安装包含原生套件的 APK。',
      'SEND_FAILED': '配对请求未送达，请确认远程连接仍然正常。',
      'UNAUTHORIZED': '当前远程会话未获得此操作权限。',
      'SESSION_ADB_AUTHORIZATION_REQUIRED': '本连接尚未获得 ADB 授权，请点击“连接已配对设备并授权”。',
      'STOP_ADB_VIDEO_BEFORE_PAIRING': '手机仍在处理旧版投屏请求，请更新两端程序后重试连接。',
      'PAIR_CODE_INVALID': '配对码必须是当前手机配对窗口显示的 6 位数字。',
      'PAIR_CODE_REJECTED': '手机拒绝了配对码，请保持配对窗口打开并输入最新的配对码。',
      'PAIR_PORT_UNREACHABLE': '无法连接手机配对端口，请检查无线调试和当前配对窗口。',
      'PAIR_RESPONSE_REJECTED': '手机未完成配对握手，请重新打开系统配对窗口并重试。',
      'PAIR_FAILED': '配对失败，请核对当前配对端口和配对码，并保持系统配对窗口打开。',
      'PAIRED_CONNECT_REQUIRED': '已配对但未发现连接端口，请填写无线调试主页上的连接端口。',
      'PAIRED_CONNECT_FAILED': '已配对，但连接端口不可达，请核对无线调试主页的连接端口。',
      'CONNECT_PORT_INVALID': '连接端口必须为 1–65535，不能填写配对码或完整地址。',
      'CONNECT_FAILED': 'ADB 连接失败，请核对连接端口，并确认系统中此设备仍为已配对。',
      'CONNECT_ADDRESS_REQUIRED': '未发现连接端口，请手动填写无线调试主页的连接端口。',
      'ADB_BINARY_MISSING': 'APK 缺少原生 ADB 组件，需要使用包含完整 ADB 套件的构建。',
      'ADB_BINARY_NOT_EXECUTABLE': '原生 ADB 组件无法执行，请检查 APK 原生库打包和 ABI。',
      'UNSUPPORTED_ANDROID': '无线调试配对需要 Android 11 或更高版本。',
      'OPERATION_TIMEOUT': '手机 ADB 操作超时，请检查无线调试和系统配对窗口后重试。',
      'CANCEL_SEND_FAILED': '取消请求未送达，请检查远程连接并刷新 ADB 状态。',
      'NO_RESPONSE': '12 秒内未收到手机接收确认。请确认电脑和手机均已更新到同一版本；这不代表配对码错误。',
    };
    // Endpoint codes are identifiers, never native command output or the code.
    return '${messages[errorCode] ?? '操作未完成，请根据错误码排查对应步骤。'} ($errorCode)';
  }

  Future<void> _send(String op, String id, Map<String, dynamic> payload) async {
    final ffi = parent.target;
    if (ffi == null || ffi.closed) throw StateError('closed');
    await bind.sessionPeerOption(sessionId: ffi.sessionId,
      name: 'android-control', value: jsonEncode({
        'v': 1, 'op': op, 'operationId': id,
        'generation': 0, 'epoch': 0, 'revision': 0, 'sequence': 0,
        'inputFrozen': false, 'payload': payload,
      }));
  }

  Future<bool> pair({required String port, required String code,
      String connectPort = ''}) async {
    if (busy) return false;
    if (!validPort(port) || !RegExp(r'^\d{6}$').hasMatch(code) ||
        (connectPort.isNotEmpty && !validPort(connectPort))) return false;
    return _begin('pair', {'port': port, 'code': code,
      if (connectPort.isNotEmpty) 'connectPort': connectPort});
  }

  Future<bool> authorize({String connectPort = ''}) async {
    if (busy || (connectPort.isNotEmpty && !validPort(connectPort))) return false;
    return _begin('authorize', {
      if (connectPort.isNotEmpty) 'connectPort': connectPort,
    });
  }

  Future<bool> revoke() async {
    if (busy) cancel();
    return _begin('revoke', {});
  }

  Future<bool> _begin(String op, Map<String, dynamic> payload) async {
    if (_disposed) return false;
    final ffi = parent.target;
    if (ffi == null || ffi.closed || ffi.ffiModel.secure != true) {
      _deadline?.cancel();
      _ackDeadline?.cancel();
      busy = false;
      _cancelling = false;
      final channelCode = ffi?.androidModeModel.reason ?? '';
      errorCode = ffi == null || ffi.closed ? 'SEND_FAILED'
          : channelErrorText(channelCode) != null ? channelCode : 'SECURE_CHANNEL_REQUIRED';
      phase = 'PAIR_FAILED';
      notifyListeners();
      return false;
    }
    operationId = Uuid().v4();
    final id = operationId;
    _revision = -1;
    _cancelling = false;
    _timedOut = false;
    phase = 'SENDING';
    errorCode = '';
    busy = true;
    notifyListeners();
    _deadline?.cancel();
    _ackDeadline?.cancel();
    _ackDeadline = Timer(const Duration(seconds: 12), () {
      if (!busy || operationId != id || _cancelling) return;
      errorCode = 'NO_RESPONSE';
      _timedOut = true;
      cancel();
    });
    _deadline = Timer(const Duration(seconds: 90), () {
      if (!busy || operationId != id) return;
      _timedOut = true;
      errorCode = 'OPERATION_TIMEOUT';
      cancel();
    });
    try {
      await _send(op, id, payload);
      if (!_disposed && operationId == id && phase == 'SENDING') {
        phase = 'WAITING_ACK';
        notifyListeners();
      }
      return true;
    } catch (_) {
      if (!_disposed && operationId == id && busy) {
        _deadline?.cancel();
        _ackDeadline?.cancel();
        busy = false;
        phase = 'PAIR_FAILED';
        errorCode = 'SEND_FAILED';
        notifyListeners();
      }
      return false;
    }
  }

  Future<void> _cancelRequest(String id) async {
    final cancelId = Uuid().v4();
    _cancelOperations[cancelId] = id;
    if (_cancelOperations.length > 8) _cancelOperations.remove(_cancelOperations.keys.first);
    try {
      await _send('pair_cancel', cancelId, {'pairOperationId': id});
      final ffi = parent.target;
      if (!_disposed && ffi != null && !ffi.closed) ffi.androidModeModel.request('status');
    } catch (_) {
      _cancelOperations.remove(cancelId);
      if (!_disposed && operationId == id && _cancelling && parent.target?.closed == false) {
        _deadline?.cancel();
        busy = false;
        phase = 'CANCEL_UNCONFIRMED';
        if (!_timedOut) errorCode = 'CANCEL_SEND_FAILED';
        notifyListeners();
      }
    }
  }

  void cancel() {
    if (!busy || _cancelling) return;
    final id = operationId;
    _cancelling = true;
    _ackDeadline?.cancel();
    _cancelRequest(operationId);
    _deadline?.cancel();
    phase = 'CANCELLING';
    _deadline = Timer(const Duration(seconds: 10), () {
      if (operationId != id || !_cancelling || !busy) return;
      busy = false;
      phase = 'CANCEL_UNCONFIRMED';
      notifyListeners();
    });
    notifyListeners();
  }

  /// Returns true for every pairing event so none can mutate video ownership.
  bool onEvent(Map<String, dynamic> update) {
    lastEventAccepted = false;
    if (_disposed) return update['kind'] == 'pairing';
    final isPairing = update['kind'] == 'pairing';
    final cancelId = update['operationId'] ?? update['requestOperationId'];
    final cancelledTarget = _cancelOperations[cancelId];
    if (!isPairing && cancelledTarget != null && update['phase'] == 'ERROR') {
      _cancelOperations.remove(cancelId);
      if (cancelledTarget == operationId && _cancelling) {
        _deadline?.cancel();
        busy = false;
        phase = 'CANCEL_UNCONFIRMED';
        final code = update['code'];
        if (!_timedOut) {
          errorCode = code is String && RegExp(r'^[A-Z0-9_]{1,80}$').hasMatch(code)
              ? code : 'CANCEL_SEND_FAILED';
        }
        notifyListeners();
      }
      return true;
    }
    final matches = operationId.isNotEmpty &&
        (update['operationId'] == operationId || update['requestOperationId'] == operationId);
    if (!isPairing && !(matches && update['phase'] == 'ERROR')) return false;
    if (!matches || (!busy && !(_cancelling && update['phase'] == 'CANCELLED'))) return true;
    if (_cancelling && update['phase'] != 'CANCELLED') return true;
    final revision = update['pairRevision'];
    if (revision != null) {
      if (revision is! int || revision < 0 || revision > 9007199254740991 || revision <= _revision) return true;
      _revision = revision;
    }
    final next = update['phase'];
    if (next is! String || !const {'PAIR_STARTING', 'PAIRING', 'CONNECTING', 'VERIFYING', 'VERIFIED',
        'PAIRED_CONNECT_REQUIRED', 'PAIR_FAILED', 'CANCELLED', 'ERROR'}.contains(next)) return true;
    _ackDeadline?.cancel();
    lastEventAccepted = true;
    phase = next == 'ERROR' ? 'PAIR_FAILED' : next;
    final code = update['code'];
    if (!(next == 'CANCELLED' && _timedOut)) {
      errorCode = code is String && RegExp(r'^[A-Z0-9_]{1,80}$').hasMatch(code) ? code : '';
    }
    if (update['localAdbReady'] is bool) localAdbReady = update['localAdbReady'];
    if (update['paired'] is bool) paired = update['paired'];
    if (!const {'PAIR_STARTING', 'PAIRING', 'CONNECTING', 'VERIFYING'}.contains(phase)) {
      busy = false;
      _cancelling = false;
      _deadline?.cancel();
    }
    notifyListeners();
    return true;
  }

  void reset() {
    if (busy) _cancelRequest(operationId);
    _deadline?.cancel();
    _ackDeadline?.cancel();
    _deadline = null;
    operationId = '';
    _revision = -1;
    _cancelling = false;
    _timedOut = false;
    _cancelOperations.clear();
    phase = busy ? 'DISCONNECTED' : 'IDLE';
    busy = false;
    errorCode = '';
    localAdbReady = null;
    paired = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    if (busy) _cancelRequest(operationId);
    _deadline?.cancel();
    _ackDeadline?.cancel();
    super.dispose();
  }
}
