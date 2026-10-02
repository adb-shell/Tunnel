import 'dart:async';
import 'dart:convert';
import 'package:flutter/widgets.dart';
import 'package:uuid/uuid.dart';
import 'model.dart';
import 'platform_model.dart';

/// Per-window remote ownership. No ADB privileges or approvals are persisted.
class AndroidModeModel extends ChangeNotifier {
  AndroidModeModel(this.parent);
  final WeakReference<FFI> parent;
  Map<String, dynamic> state = {};
  Map<String, dynamic>? _presentation;
  Timer? _heartbeat;
  Timer? _deadline;
  int _generation = 0;
  int _epoch = 0;
  int _revision = 0;
  int _frameSequence = 0;
  bool inputFrozen = false;
  bool usingAdb = false;
  bool busy = false;
  String phase = 'UNKNOWN';
  String reason = '';
  String _startOperation = '';
  String _pendingOperation = '';
  String _statusOperation = '';
  int _presentationToken = 0;
  bool _observingOtherWindow = false;
  bool _statusRequested = false;

  void ensureStatus() {
    if (_statusRequested) return;
    _statusRequested = true;
    WidgetsBinding.instance.addPostFrameCallback((_) => _send('status'));
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
      reason = '请求未送达，请检查连接';
      busy = false;
      notifyListeners();
    }
  }

  Future<void> request(String op, {Map<String, dynamic> payload = const {}}) async {
    final ffi = parent.target;
    if (op == 'start' && ffi != null &&
        bind.peerGetSessionsCount(id: ffi.id, connType: ffi.connType.index) > 1) {
      reason = '请只保留此设备的一个远控窗口后请求 ADB';
      notifyListeners();
      return;
    }
    if (_observingOtherWindow && op != 'status') return;
    if (busy && op != 'status' && op != 'stop') return;
    final operation = Uuid().v4();
    if (op != 'status') {
      busy = true;
      reason = '';
      _pendingOperation = operation;
      _deadline?.cancel();
      _deadline = Timer(Duration(seconds: op == 'start' ? 70 : 20), () {
        busy = false;
        reason = '操作超时，请刷新状态；切换未确认时保持输入冻结';
        notifyListeners();
        _send('status');
      });
    }
    if (op == 'start') {
      _generation++;
      _startOperation = operation;
      phase = 'REQUESTING';
    } else if (op == 'stop') {
      _freeze();
    }
    notifyListeners();
    await _send(op, payload: payload, operation: operation);
  }

  void _freeze() {
    if (!inputFrozen) parent.target?.inputModel.enterOrLeave(false);
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
    int? number(String key) {
      final value = update[key];
      return value is int && value >= 0 && value <= 9007199254740991 ? value : null;
    }
    final epoch = number('epoch') ?? 0;
    final generation = number('generation') ?? 0;
    final next = update['phase']?.toString() ?? phase;
    final freshStatus = _statusOperation.isNotEmpty &&
        update['requestOperationId'] == _statusOperation;
    final observedLease = _observingOtherWindow &&
        generation == state['generation'] && epoch >= (state['epoch'] as int? ?? 0);
    // A status requested after reconnect may describe an older, idle endpoint.
    // Read its capabilities without restoring that endpoint's ownership.
    if (generation != 0 && generation < _generation && !freshStatus && !observedLease) return;
    if (const {'CANDIDATE_READY', 'PRESENT_FRAME', 'COMMITTED', 'NORMAL', 'ROLLING_BACK'}.contains(next) &&
        (epoch == 0 || generation == 0 || update['operationId'] is! String)) return;
    if (const {'CANDIDATE_READY', 'PRESENT_FRAME'}.contains(next) &&
        ((number('sequence') ?? 0) == 0 || (number('revision') ?? 0) == 0)) return;
    final sourceOperation = update['sourceOperationId'] ?? update['operationId'];
    if (const {'PREPARING', 'READY', 'CANDIDATE_READY', 'PRESENT_FRAME', 'COMMITTED'}.contains(next) &&
        epoch > 0 && (_startOperation.isEmpty ||
          (next != 'PRESENT_FRAME' && sourceOperation != _startOperation))) {
      // Multiple Flutter windows may share a Rust connection. Only the window
      // that initiated the transaction may acknowledge or inject input.
      _freeze();
      _observingOtherWindow = true;
      usingAdb = false;
      busy = false;
      _heartbeat?.cancel();
      _heartbeat = null;
      state = update;
      phase = 'OTHER_WINDOW';
      reason = 'ADB 由其他窗口控制；当前窗口输入已冻结';
      notifyListeners();
      return;
    }
    if (_observingOtherWindow) {
      if (next == 'NORMAL' && observedLease) {
        _observingOtherWindow = false;
        state = update;
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
        state = update;
        reason = update['code']?.toString() ?? '';
        phase = 'IDLE';
        notifyListeners();
      } else if (next == 'ERROR' && generation == 0) {
        reason = update['code']?.toString() ?? 'ADB 请求失败';
        busy = false;
        _deadline?.cancel();
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
      if (const {'ROLLING_BACK', 'WAITING_PRESENTED', 'RECONFIGURE', 'STOPPING', 'FAILED'}.contains(next) ||
          (next == 'PREPARING' && usingAdb)) _freeze();
      if (update['operationId'] == _pendingOperation && !const {
        'PREPARING', 'READY', 'WAITING_PRESENTED', 'ROLLING_BACK'
      }.contains(next)) {
        busy = false;
        _deadline?.cancel();
      }
      reason = update['code']?.toString() ?? '';
    }
    notifyListeners();
  }

  /// Called after a matching Rust presentation marker and actual image/texture delivery.
  int? get presentationToken => _presentation == null ? null : _presentationToken;

  void onFrameAvailable(int? deliveredToken) {
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
    if (!usingAdb) return false;
    if (inputFrozen && state['capturePaused'] != true) return true;
    final enable = argument.contains('开') || argument == '1';
    final actions = {
      'wheelblank': enable ? 'display_off' : 'display_on',
      'wheelanalysis': enable ? 'hierarchy_on' : 'hierarchy_off',
      'wheelback': enable ? 'ignore_on' : 'ignore_off',
      'wheelstart': enable ? 'share_start' : 'share_stop',
      'wheeltouch': enable ? 'touch_block_on' : 'touch_block_off',
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
    await request('side_action', payload: {'action': action});
    return true;
  }

  void reset() {
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
}
