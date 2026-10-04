import 'dart:async';
import 'dart:convert';
import 'package:flutter/widgets.dart';
import 'package:uuid/uuid.dart';
import 'model.dart';
import 'platform_model.dart';
import 'android_adb_pairing_model.dart';
import '../common.dart' show showToast;

/// Per-window remote ownership. No ADB privileges or approvals are persisted.
class AndroidModeModel extends ChangeNotifier {
  AndroidModeModel(this.parent) : pairing = AndroidAdbPairingModel(parent);
  final WeakReference<FFI> parent;
  final AndroidAdbPairingModel pairing;
  Map<String, dynamic> state = {};
  Map<String, dynamic>? _presentation;
  Timer? _heartbeat;
  Timer? _deadline;
  Timer? _statusRefresh;
  DateTime? _lastAdbDecodedAt;
  DateTime? _lastRenderedAt;
  bool _displayedAdb = false;
  bool _lastReportedFramePresent = false;

  bool get adbFramePresent {
    final decoded = _lastAdbDecodedAt;
    final rendered = _lastRenderedAt;
    final now = DateTime.now();
    return _displayedAdb && decoded != null && rendered != null &&
        now.difference(decoded).inMilliseconds < 5000 &&
        now.difference(rendered).inMilliseconds < 5000;
  }
  bool get adbVideoPresent => adbFramePresent && state['adbVideoPresent'] == true;
  bool get adbSpecialPresent => adbFramePresent && state['adbSpecialPresent'] == true;

  void _notifyFramePresence() {
    final present = adbFramePresent;
    if (present == _lastReportedFramePresent) return;
    _lastReportedFramePresent = present;
    notifyListeners();
  }

  int _generation = 0;
  int _epoch = 0;
  int _revision = 0;
  int _frameSequence = 0;
  bool inputFrozen = false;
  bool usingAdb = false;
  bool busy = false;
  bool adbActionsVisible = true;
  int _lastRecoveryGeneration = -1;
  final Map<String, Timer> _actionDeadlines = {};
  final Map<String, String> _actionKeys = {};
  bool _canRestartStoppedVideo = false;
  String _stopOperation = '';
  void _finishAction(String operation) {
    _actionDeadlines.remove(operation)?.cancel();
    _actionKeys.remove(operation);
  }
  void setAdbActionsVisible(bool visible) {
    adbActionsVisible = visible;
    notifyListeners();
  }
  void _report(String message) {
    reason = message;
    notifyListeners();
    showToast(reasonText);
  }
  String phase = 'UNKNOWN';
  String reason = '';
  String get reasonText {
    final channelMessage = AndroidAdbPairingModel.channelErrorText(reason);
    if (channelMessage != null) return '$channelMessage ($reason)';
    final permissionMessage = AndroidAdbPairingModel.permissionErrorText(reason);
    if (permissionMessage != null) return '$permissionMessage ($reason)';
    const messages = <String, String>{
      'HELPER_ASSET_INVALID': '手机投屏组件缺失或版本不匹配，请更新 APK。',
      'HELPER_DIRECTORY_FAILED': '无法创建手机投屏组件目录。',
      'HELPER_PUSH_FAILED': '投屏组件传送到手机失败。',
      'HELPER_HASH_INVALID': '手机投屏组件校验失败，请更新 APK。',
      'HELPER_LISTENER_FAILED': '无法建立手机本地投屏通道。',
      'HELPER_PROCESS_START_FAILED': '手机投屏进程启动失败。',
      'HELPER_BOOTSTRAP_FAILED': '手机投屏组件初始化失败。',
      'HELPER_VIDEO_HANDSHAKE_FAILED': '手机视频通道连接失败。',
      'HELPER_CONTROL_HANDSHAKE_FAILED': '手机控制通道连接失败。',
      'HELPER_VIDEO_CHANNEL_FAILED': '手机视频通道已中断。',
      'FRAME_ACQUIRE_FAILED': '手机暂时无法生成截图或穿透画面，正在重试。',
      'BITMAP_ENCODER_UNSUPPORTED': '手机截图或穿透画面的图形编码失败，正在重试。',
      'ENCODER_OUTPUT_STALLED': '手机视频编码暂时没有输出，正在重建编码器。',
      'ENCODER_FAILED': '手机视频编码失败，正在重试。',
      'SESSION_ADB_AUTHORIZATION_REQUIRED': '请先打开远程 ADB 窗口连接并授权。',
      'ADB_BUSY': '手机正在处理另一项配对或连接请求，请稍后重试。',
      'ACTION_BUSY': '手机正在执行上一项操作，请稍后重试。',
      'ACTION_CANCELLED': '操作已随上一个 ADB 实例结束，可重新点击执行。',
      'ACCESSIBILITY_UNAVAILABLE': '手机无障碍服务尚未运行，请先在手机设置中开启。',
      'VIDEO_START_REQUIRED': '请先开启 ADB 投屏后使用此功能。',
      'ADB_VIDEO_REQUIRED': '请先开启 ADB 投屏后使用此功能。',
      'ACCESSIBILITY_NOT_READY': '手机无障碍服务尚未运行，请先开启无障碍权限。',
      'LOCAL_ACTION_REQUIRED': '手机需要确认系统权限，请在手机上完成当前授权提示。',
      'CAPTURE_PERMISSION_REQUIRED': '已在手机发起普通屏幕共享，请在手机上确认系统录屏授权。',
      'LOCAL_ADB_BUSY': '手机正在回收旧控制进程或通道已满，请稍后重试。',
      'ADB_CONTROL_STARTING': 'ADB 控制通道正在准备，请稍后重试此操作。',
      'VIDEO_TASK_FAILED': '本次 ADB 画面任务已结束，可直接重新开启投屏。',
      'VIDEO_TASK_REJECTED': '本次画面请求未能启动，可直接重新开启投屏。',
      'INPUT_OPERATION_TIMEOUT': '本次输入未完成，请重新操作。',
      'INPUT_REJECTED': '手机未接受本次输入，请重新操作。',
      'LOCAL_ADB_REQUIRED': '手机 ADB 连接已失效，请重新连接已配对设备。',
      'OWNER_BUSY': 'ADB 功能由另一个远程会话使用，请先结束该会话操作。',
      'OVERLAY_UNAVAILABLE': '手机未能创建 ADB 黑屏遮罩，请检查手机端状态。',
      'ADB_ACTION_FAILED': '手机执行 ADB 命令失败，请检查本地 LADB 连接。',
      'OPERATION_UNSUPPORTED': '当前手机投屏组件尚不支持此操作。',
    };
    final message = messages[reason];
    return message == null ? reason : '$message ($reason)';
  }
  String _startOperation = '';
  String _pendingOperation = '';
  String _statusOperation = '';
  int _presentationToken = 0;
  bool _observingOtherWindow = false;
  bool _statusRequested = false;
  /// Authorization belongs to this connection; no duration timer.
  bool? consentActive;

  void _updateConsent(Map<String, dynamic> update) {
    final active = update['consentActive'];
    if (active is bool) {
      if (active && consentActive != true) adbActionsVisible = true;
      consentActive = active;
    }
  }

  void ensureStatus() {
    if (_statusRequested) return;
    _statusRequested = true;
    WidgetsBinding.instance.addPostFrameCallback((_) => _send('status'));
    // One timer per remote window: expiry is based on real decoded/rendered
    // frames, while status refresh also works with no active video heartbeat.
    var tick = 0;
    _statusRefresh ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (parent.target?.closed != false) return;
      _notifyFramePresence();
      if (++tick % 3 == 0 && !_observingOtherWindow) _send('status');
    });
  }

  Future<void> _send(String op, {Map<String, dynamic> payload = const {},
      String? operation, int? sequence}) async {
    final ffi = parent.target;
    if (ffi == null || ffi.closed) return;
    final operationId = operation ?? Uuid().v4();
    if (op == 'status') _statusOperation = operationId;
    try {
      await bind.sessionPeerOption(sessionId: ffi.sessionId,
        name: 'android-control', value: jsonEncode({
          'v': 1, 'op': op, 'operationId': operationId,
          'generation': _generation, 'epoch': _epoch, 'revision': _revision,
          'sequence': sequence ?? _frameSequence, 'inputFrozen': inputFrozen,
          'payload': payload,
        }));
    } catch (_) {
      _finishAction(operationId);
      // A failed independent side request must not finish a video transaction.
      if ((op == 'start' || op == 'stop') && operationId == _pendingOperation) {
        busy = false;
        _deadline?.cancel();
      }
      _report('请求未送达，请检查连接');
    }
  }

  Future<void> request(String op, {Map<String, dynamic> payload = const {}, bool automaticRecovery = false}) async {
    final ffi = parent.target;
    final action = op == 'side_action' || op == 'accessibility_action' ||
        op.startsWith('accessibility_');
    if ((op == 'start' || (action && op != 'accessibility_action')) && consentActive != true) {
      _report('请先通过远程 ADB 配对窗口连接并授权本连接');
      return;
    }
    // Side controls are independent of video transitions and carry their own
    // request deadlines; the endpoint serializes conflicting native work.
    if (action) {
      final actionName = (payload['action'] ?? '').toString();
      final feature = actionName.replaceFirst(RegExp(r'_(on|off)$'), '');
      final key = '$op:$feature';
      // A retry or opposite toggle supersedes its previous UI deadline. Native
      // feature versions serialize the actual effect; stale callbacks cannot
      // keep a newly requested toggle disabled for the old 25-second timeout.
      final superseded = _actionKeys.entries.where((entry) => entry.value == key)
          .map((entry) => entry.key).toList();
      for (final previous in superseded) { _finishAction(previous); }
      if (_actionDeadlines.length >= 32) {
        _report('待处理操作过多，请等待手机返回结果后重试');
        return;
      }
      final operation = Uuid().v4();
      _actionKeys[operation] = key;
      final startsHelper = op == 'side_action' &&
          payload['action'] == 'overlay_black_on';
      _actionDeadlines[operation] = Timer(Duration(seconds: startsHelper ? 70 : 25), () {
        _finishAction(operation);
        _report('手机未返回侧按钮操作结果，请刷新状态后重试');
      });
      await _send(op, payload: payload, operation: operation);
      return;
    }
    if (op == 'start' && ffi != null &&
        bind.peerGetSessionsCount(id: ffi.id, connType: ffi.connType.index) > 1) {
      _report('请只保留此设备的一个远控窗口后请求 ADB');
      return;
    }
    if (_observingOtherWindow && op != 'status') {
      _report('ADB 投屏由其他窗口控制，请在该窗口操作');
      return;
    }
    // Pairing owns a separate bounded client. It must not stall an already
    // authorized controller's video/effect requests or helper recovery.
    final operation = Uuid().v4();
    if (op != 'status') {
      busy = true;
      reason = '';
      _pendingOperation = operation;
      _deadline?.cancel();
      _deadline = Timer(Duration(seconds: op == 'start' ? 70 : 20), () {
        busy = false;
        reason = '手机未确认本次视频请求，可重新点击开启 ADB 投屏；其他控制功能仍可使用';
        notifyListeners();
        _send('status');
      });
    }
    if (op == 'start') {
      if (!automaticRecovery) _lastRecoveryGeneration = -1;
      // A new generation supersedes an unfinished video attempt. Stale frame
      // callbacks and heartbeats must not acknowledge the replacement source.
      _presentation = null;
      _presentationToken++;
      _heartbeat?.cancel();
      _heartbeat = null;
      _canRestartStoppedVideo = false;
      state['videoStopped'] = false;
      _stopOperation = '';
      _generation++;
      _startOperation = operation;
      phase = 'REQUESTING';
    } else if (op == 'stop') {
      _generation++;
      _presentation = null;
      _presentationToken++;
      _heartbeat?.cancel();
      _heartbeat = null;
      _stopOperation = operation;
      _freeze();
    }
    notifyListeners();
    await _send(op, payload: payload, operation: operation);
  }

  void _freeze() {
    // This records an unconfirmed video presentation only. Input authorization
    // and geometry validation belong to the independent endpoint input channel.
    inputFrozen = true;
  }

  void onEvent(Map<String, dynamic> event) {
    Map<String, dynamic> update;
    try {
      final raw = event['status'];
      if (raw is String && raw.length > 16384) return;
      update = Map<String, dynamic>.from(raw is String ? jsonDecode(raw) : raw);
    } catch (_) { return; }
    if (update['v'] != 1) return;
    if (update['kind'] == 'video_frame') {
      // These events originate from the local decoder, never the Android
      // request state. They must not reset a pending presentation handshake.
      if (update['adb'] == true) {
        if (update['generation'] != _generation ||
            update['epoch'] is! int || (update['epoch'] as int) < _epoch) return;
        _lastAdbDecodedAt = DateTime.now();
        _displayedAdb = true;
      } else if (update['adb'] == false) {
        _displayedAdb = false;
        _lastAdbDecodedAt = null;
      } else {
        return;
      }
      _notifyFramePresence();
      return;
    }
    void updateEffects() {
      for (final key in const ['overlayBlack', 'overlayBlackRequested',
          'localAdbReady']) {
        if (update[key] is bool) state[key] = update[key];
      }
      // Independent effect replies can have waited in transit while a newer
      // picture request was issued. They cannot restore the old source choices.
      final sourceGeneration = update['sourceGeneration'] ?? update['generation'];
      if (sourceGeneration is int && sourceGeneration >= _generation) {
        for (final key in const ['baseLiveRequested', 'snapshotEnabled',
            'hierarchyEnabled', 'adbVideoPresent', 'adbSpecialPresent']) {
          if (update[key] is bool) state[key] = update[key];
        }
      }
    }
    // Recovery is also advertised by status polling, so a dropped action
    // notification cannot permanently strand an enabled source. Require both
    // current local consent and an explicit matching endpoint assertion.
    if (update['resumeVideo'] == true &&
        update['resumeGeneration'] is int && update['resumeGeneration'] == _generation &&
        (update['generation'] == null || update['generation'] == 0 || update['generation'] == _generation) &&
        update['baseLiveRequested'] is bool && update['consentActive'] == true &&
        consentActive == true && _startOperation.isNotEmpty && !_observingOtherWindow &&
        _lastRecoveryGeneration != _generation) {
      _lastRecoveryGeneration = _generation;
      updateEffects();
      final completedAction = update['requestOperationId'] ?? update['operationId'];
      if (completedAction is String && update['operationComplete'] != false &&
          _actionDeadlines.containsKey(completedAction)) _finishAction(completedAction);
      // Android owns the single helper and retry backoff. Start establishes a
      // new generation synchronously; do not apply the old status after it.
      request('start', payload: const {'sourceAction': 'resume'},
        automaticRecovery: true);
      return;
    }
    final actionId = update['requestOperationId'] ?? update['operationId'];
    if (actionId is String && _actionDeadlines.containsKey(actionId)) {
      _updateConsent(update);
      updateEffects();
      if (update['operationComplete'] == false) {
        notifyListeners();
        return;
      }
      _finishAction(actionId);
      reason = update['code']?.toString() ?? '';
      notifyListeners();
      if (reason.isNotEmpty) showToast(reasonText);
      return;
    }
    if (update['kind'] == 'action' || update['phase'] == 'ACTION_RESULT') {
      _updateConsent(update);
      updateEffects();
      notifyListeners();
      return;
    }
    final previousPairPhase = pairing.phase;
    if (pairing.onEvent(update)) {
      if (pairing.lastEventAccepted) {
        if (pairing.localAdbReady != null) state['localAdbReady'] = pairing.localAdbReady;
        _updateConsent(update);
        if (pairing.phase == 'VERIFIED' && previousPairPhase != 'VERIFIED') _send('status');
        if (pairing.errorCode == 'CONSENT_REVOKED') _send('status');
        notifyListeners();
      }
      return;
    }
    int? number(String key) {
      final value = update[key];
      return value is int && value >= 0 && value <= 9007199254740991 ? value : null;
    }
    final epoch = number('epoch') ?? 0;
    final generation = number('generation') ?? 0;
    final next = update['phase']?.toString() ?? phase;
    // Stopping an already idle endpoint has no lease epoch. Only the matching
    // explicit stop acknowledgement may release an uncertain local transition.
    if (next == 'NORMAL' && epoch == 0 &&
        (generation == 0 || generation == _generation) &&
        _stopOperation.isNotEmpty &&
        (update['operationId'] == _stopOperation ||
          update['requestOperationId'] == _stopOperation)) {
      _updateConsent(update);
      state.addAll(update);
      _canRestartStoppedVideo = false;
      _stopOperation = '';
      _startOperation = '';
      _observingOtherWindow = false;
      _heartbeat?.cancel();
      _heartbeat = null;
      _deadline?.cancel();
      _presentation = null;
      _presentationToken++;
      inputFrozen = false;
      usingAdb = false;
      busy = false;
      phase = 'NORMAL';
      reason = update['code']?.toString() ?? '';
      notifyListeners();
      return;
    }
    final freshStatus = _statusOperation.isNotEmpty &&
        update['requestOperationId'] == _statusOperation;
    final observedLease = _observingOtherWindow &&
        generation == state['generation'] && epoch >= (state['epoch'] as int? ?? 0);
    // A status requested after reconnect may describe an older, idle endpoint.
    // Read its capabilities without restoring that endpoint's ownership.
    if (generation != 0 && generation < _generation && !observedLease) {
      // A poll can be sent before a source toggle and arrive after it. Its
      // operation still matches _statusOperation, but its source belongs to the
      // previous request. Never mistake that source for another window, restore
      // its flags or let it acknowledge the replacement transaction.
      if (freshStatus) {
        _updateConsent(update);
        if (update['localAdbReady'] is bool) state['localAdbReady'] = update['localAdbReady'];
        notifyListeners();
      }
      return;
    }
    if (const {'CANDIDATE_READY', 'PRESENT_FRAME', 'COMMITTED', 'NORMAL', 'ROLLING_BACK'}.contains(next) &&
        (epoch == 0 || generation == 0 || update['operationId'] is! String)) return;
    if (const {'CANDIDATE_READY', 'PRESENT_FRAME'}.contains(next) &&
        ((number('sequence') ?? 0) == 0 || (number('revision') ?? 0) == 0)) return;
    final sourceOperation = update['sourceOperationId'] ?? update['operationId'];
    if (const {'PREPARING', 'READY', 'CANDIDATE_READY', 'PRESENT_FRAME', 'COMMITTED'}.contains(next) &&
        epoch > 0 && (_startOperation.isEmpty ||
          (next != 'PRESENT_FRAME' && sourceOperation != _startOperation))) {
      // Multiple Flutter windows may share a Rust connection. Only the window
      // that initiated the video transaction may acknowledge its presentation.
      _freeze();
      _observingOtherWindow = true;
      usingAdb = false;
      busy = false;
      _heartbeat?.cancel();
      _heartbeat = null;
      state.addAll(update);
      phase = 'OTHER_WINDOW';
      reason = 'ADB 视频请求来自其他窗口';
      notifyListeners();
      return;
    }
    if (_observingOtherWindow) {
      if (next == 'NORMAL' && observedLease) {
        _canRestartStoppedVideo = false;
        _stopOperation = '';
        _updateConsent(update);
        _observingOtherWindow = false;
        state.addAll(update);
        inputFrozen = false;
        usingAdb = false;
        busy = false;
        _deadline?.cancel();
        _presentation = null;
        _presentationToken++;
        phase = 'NORMAL';
        reason = '';
        notifyListeners();
      }
      return;
    }
    if (generation != _generation) {
      // A fresh status may describe an idle endpoint or another owner. It must
      // never resurrect an old lease, heartbeat or presentation after reconnect.
      if (freshStatus && !usingAdb && !busy) {
        state.addAll(update);
        _updateConsent(update);
        reason = update['code']?.toString() ?? '';
        phase = 'IDLE';
        notifyListeners();
      } else if (next == 'ERROR' && generation == 0 &&
          (update['operationId'] == _pendingOperation ||
            update['requestOperationId'] == _pendingOperation)) {
        reason = update['code']?.toString() ?? 'ADB 请求失败';
        busy = false;
        _deadline?.cancel();
        notifyListeners();
      } else if (next == 'ERROR' && generation == 0 && freshStatus) {
        reason = update['code']?.toString() ?? '状态查询失败';
        notifyListeners();
      }
      return;
    }
    if (epoch != 0 && epoch < _epoch) return;
    if (epoch > _epoch) {
      // Invalidate an already queued Flutter post-frame callback before a new
      // source transaction can reuse the current send metadata.
      _presentation = null;
      _presentationToken++;
    }
    if (epoch != 0) _epoch = epoch;
    _revision = number('revision') ?? _revision;
    _frameSequence = number('sequence') ?? _frameSequence;
    state.addAll(update);
    _updateConsent(update);
    if (update['videoStopped'] == true &&
        const {'ROLLING_BACK', 'FROZEN'}.contains(next)) {
      // The endpoint has stopped ADB, even if no ordinary MediaProjection
      // frame exists. It permits a new ADB transaction without thawing input.
      _canRestartStoppedVideo = true;
      usingAdb = false;
      busy = false;
      _deadline?.cancel();
    }
    if (next == 'PREPARING' && _epoch > 0 && _heartbeat == null) {
      _heartbeat = Timer.periodic(const Duration(seconds: 3), (_) => _send('heartbeat'));
    }
    if (next == 'CANDIDATE_READY') {
      if (update['operationId'] != _startOperation) return;
      _freeze();
      phase = next;
      _send('activate', operation: _startOperation);
    } else if (next == 'PRESENT_FRAME') {
      _freeze();
      _presentation = update;
      _presentationToken++;
      phase = next;
    } else if (next == 'COMMITTED' || next == 'NORMAL') {
      if (next == 'NORMAL') {
        _canRestartStoppedVideo = false;
        _stopOperation = '';
      }
      phase = next;
      usingAdb = next == 'COMMITTED';
      inputFrozen = usingAdb && update['inputReady'] != true;
      _presentation = null;
      _presentationToken++;
      busy = false;
      _deadline?.cancel();
      _heartbeat?.cancel();
      _heartbeat = null;
      if (update.containsKey('code')) reason = update['code']?.toString() ?? '';
      if (usingAdb) {
        _heartbeat = Timer.periodic(const Duration(seconds: 3), (_) => _send('heartbeat'));
      }
    } else if (next == 'ERROR') {
      reason = update['code']?.toString() ?? 'ADB 操作失败';
      if (update['operationId'] == _pendingOperation || !usingAdb) {
        busy = false;
        _deadline?.cancel();
      }
      // Only an endpoint NORMAL acknowledgement may thaw an uncertain transition.
    } else {
      phase = next;
      if (const {'ROLLING_BACK', 'WAITING_PRESENTED', 'RECONFIGURE', 'STOPPING', 'FAILED', 'FROZEN'}.contains(next) ||
          (next == 'PREPARING' && usingAdb)) _freeze();
      if (update['operationId'] == _pendingOperation && !const {
        'PREPARING', 'READY', 'WAITING_PRESENTED', 'ROLLING_BACK'
      }.contains(next)) {
        busy = false;
        _deadline?.cancel();
      }
      reason = update['code']?.toString() ?? '';
    }
    if (next == 'FROZEN') {
      busy = false;
      _deadline?.cancel();
      showToast(_canRestartStoppedVideo ? 'ADB 投屏已关闭；可在手机启动普通共享，或重新开启 ADB 投屏' :
          reasonText.isEmpty ? 'ADB 视频暂不可用，可重新点击开启；其他控制功能仍可使用' : reasonText);
    }
    notifyListeners();
  }

  /// Called after a matching Rust presentation marker and actual image/texture delivery.
  int? get presentationToken => _presentation == null ? null : _presentationToken;

  void onFrameAvailable(int? deliveredToken) {
    _lastRenderedAt = DateTime.now();
    _notifyFramePresence();
    if (deliveredToken == null || deliveredToken != _presentationToken) return;
    final pending = _presentation;
    if (pending == null) return;
    _presentation = null;
    final token = _presentationToken;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (token != _presentationToken || parent.target?.closed != false) return;
      _send('presented', operation: pending['operationId'] as String,
        sequence: (pending['sequence'] as num).toInt());
    });
    WidgetsBinding.instance.scheduleFrame();
  }

  Future<bool> sideAction(String type, String argument) async {
    if (consentActive != true) {
      _report('本连接尚未获得 ADB 授权，请先连接并授权');
      return true;
    }
    final enable = argument.contains('开') || argument == '1';
    final actions = {
      'wheelblank': enable ? 'overlay_black_on' : 'overlay_black_off',
      'wheelanalysis': enable ? 'hierarchy_on' : 'hierarchy_off',
      'wheelback': enable ? 'ignore_on' : 'ignore_off',
      'wheelstart': enable ? 'share_start' : 'share_stop',
    };
    if (type == 'wheeldevselector') {
      reason = 'ADB 模式不支持旧无障碍节点选择器';
      notifyListeners();
      return true;
    }
    if (type == 'wheelbrowser') {
      final url = argument.startsWith(RegExp(r'https?://')) ? argument : 'https://$argument';
      await request('side_action', payload: {'action': 'open_url', 'url': url});
      return true;
    }
    final action = actions[type];
    if (action == null) return false;
    if (type == 'wheelback' || type == 'wheelanalysis') {
      // Every source selection is a new last-request-wins video transaction;
      // an off action removes only its own source at the Android selector.
      await request('start', payload: {'sourceAction': action});
      return true;
    }
    await request('side_action', payload: {'action': action});
    return true;
  }

  void reset() {
    _lastRecoveryGeneration = -1;
    _statusRefresh?.cancel();
    _statusRefresh = null;
    _lastAdbDecodedAt = null;
    _lastRenderedAt = null;
    _displayedAdb = false;
    _lastReportedFramePresent = false;
    pairing.reset();
    for (final timer in _actionDeadlines.values) { timer.cancel(); }
    _actionDeadlines.clear();
    _actionKeys.clear();
    _canRestartStoppedVideo = false;
    _stopOperation = '';
    adbActionsVisible = true;
    consentActive = null;
    _heartbeat?.cancel();
    _heartbeat = null;
    _deadline?.cancel();
    _presentationToken++;
    _presentation = null;
    _generation++;
    _epoch = 0;
    _revision = 0;
    _frameSequence = 0;
    _startOperation = '';
    _pendingOperation = '';
    _statusOperation = '';
    _observingOtherWindow = false;
    _statusRequested = false;
    state = {};
    inputFrozen = false;
    usingAdb = false;
    busy = false;
    phase = 'UNKNOWN';
    reason = '';
    notifyListeners();
  }

  @override
  void dispose() {
    for (final timer in _actionDeadlines.values) { timer.cancel(); }
    _actionDeadlines.clear();
    _actionKeys.clear();
    _heartbeat?.cancel();
    _deadline?.cancel();
    _statusRefresh?.cancel();
    pairing.dispose();
    super.dispose();
  }
}
