import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:zego_express_engine/zego_express_engine.dart';

class ZegoVoiceCallPayload {
  final String rtcProvider;
  final int appId;
  final String roomId;
  final String userId;
  final String userName;
  final String token;
  final String publishStreamId;
  final String playStreamId;
  final String role;
  final int expiresAt;

  ZegoVoiceCallPayload({
    required this.rtcProvider,
    required this.appId,
    required this.roomId,
    required this.userId,
    required this.userName,
    required this.token,
    required this.publishStreamId,
    required this.playStreamId,
    required this.role,
    required this.expiresAt,
  });

  factory ZegoVoiceCallPayload.fromJson(Map<String, dynamic> json) {
    return ZegoVoiceCallPayload(
      rtcProvider: json['rtcProvider']?.toString() ?? '',
      appId: int.tryParse(json['appId']?.toString() ?? '') ?? 0,
      roomId: json['roomId']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      userName: json['userName']?.toString() ?? '',
      token: json['token']?.toString() ?? '',
      publishStreamId: json['publishStreamId']?.toString() ?? '',
      playStreamId: json['playStreamId']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
      expiresAt: int.tryParse(json['expiresAt']?.toString() ?? '') ?? 0,
    );
  }

  bool get isValid =>
      rtcProvider == 'zego' &&
      appId > 0 &&
      roomId.isNotEmpty &&
      userId.isNotEmpty &&
      token.isNotEmpty &&
      publishStreamId.isNotEmpty &&
      playStreamId.isNotEmpty;
}

enum ZegoVoiceCallPhase {
  idle,
  preparing,
  joiningRoom,
  publishing,
  waitingPeerStream,
  playing,
  connected,
  failed,
  leaving,
}

class ZegoVoiceCallModel extends ChangeNotifier {
  static bool _engineCreated = false;
  static int? _engineAppId;
  static String? _engineUserId;
  static String? _engineUserName;
  static ZegoVoiceCallModel? _activeModel;
  static final Map<String, DateTime> _recentlyClosedCallKeys =
      <String, DateTime>{};

  static const int _commonUserNotSameErrorCode = 1000020;
  static const Duration _stalePayloadIgnoreWindow = Duration(seconds: 10);

  ZegoVoiceCallPayload? _payload;
  ZegoVoiceCallPhase _phase = ZegoVoiceCallPhase.idle;
  bool _joining = false;
  bool _joined = false;
  bool _playStarted = false;
  bool _peerStreamOnline = false;
  bool _muted = false;
  String _roomState = 'idle';
  String _publisherState = 'idle';
  String _playerState = 'idle';
  String _lastErrorText = '';
  int _roomErrorCode = 0;
  int _publisherErrorCode = 0;
  int _playerErrorCode = 0;
  bool _publisherAudioFirstFrameCaptured = false;
  bool _publisherAudioFirstFrameSent = false;
  bool _playerAudioFirstFrameReceived = false;
  double _publishAudioFps = 0;
  double _publishAudioKbps = 0;
  double _playAudioFps = 0;
  double _playAudioKbps = 0;
  DateTime? _connectedAt;
  Timer? _durationTimer;
  Timer? _playRetryTimer;
  Timer? _publishWatchdogTimer;
  Future<void>? _leaveFuture;
  int _playAttempt = 0;

  bool get joined => _joined;
  bool get active =>
      _payload != null || _joining || _joined || _lastErrorText.isNotEmpty;
  bool get muted => _muted;
  bool get peerStreamOnline => _peerStreamOnline;
  bool get hasError =>
      errorText.isNotEmpty || _phase == ZegoVoiceCallPhase.failed;
  String get roomState => _roomState;
  String get publisherState => _publisherState;
  String get playerState => _playerState;
  bool get publisherAudioFirstFrameCaptured =>
      _publisherAudioFirstFrameCaptured;
  bool get publisherAudioFirstFrameSent => _publisherAudioFirstFrameSent;
  bool get playerAudioFirstFrameReceived => _playerAudioFirstFrameReceived;
  String get roomStateText => _zegoStateText(_roomState);
  String get publisherStateText => _zegoStateText(_publisherState);
  String get playerStateText => _zegoStateText(_playerState);
  String get streamStateText => '$publisherStateText / $playerStateText';
  String get peerStreamText =>
      _peerStreamOnline ? '\u5df2\u5728\u7ebf' : '\u7b49\u5f85\u5bf9\u7aef';
  String get localAudioText {
    if (_muted) return '\u9ea6\u514b\u98ce\u5df2\u5173\u95ed';
    if (_publisherAudioFirstFrameSent) return '\u5df2\u63a8\u9001';
    if (_publisherAudioFirstFrameCaptured) return '\u5df2\u91c7\u96c6';
    return '\u7b49\u5f85\u9ea6\u514b\u98ce';
  }

  String get remoteAudioText => _playerAudioFirstFrameReceived
      ? '\u5df2\u63a5\u6536'
      : '\u7b49\u5f85\u8fdc\u7aef';
  String get localAudioSummaryText =>
      '$localAudioText / $localAudioQualityText';
  String get remoteAudioSummaryText =>
      '$remoteAudioText / $remoteAudioQualityText';
  String get localAudioQualityText =>
      _formatAudioQuality(_publishAudioFps, _publishAudioKbps);
  String get remoteAudioQualityText =>
      _formatAudioQuality(_playAudioFps, _playAudioKbps);
  String get roomId => _payload?.roomId ?? '';
  Duration get callDuration => _connectedAt == null
      ? Duration.zero
      : DateTime.now().difference(_connectedAt!);
  bool get mediaReady =>
      _joined &&
      _publisherAudioFirstFrameSent &&
      _playerAudioFirstFrameReceived;

  String get errorText {
    if (_lastErrorText.isNotEmpty) return _lastErrorText;
    if (_roomErrorCode != 0) {
      return '\u623f\u95f4\u9519\u8bef\u7801 $_roomErrorCode';
    }
    if (_publisherErrorCode != 0) {
      return '\u63a8\u6d41\u9519\u8bef\u7801 $_publisherErrorCode';
    }
    if (_playerErrorCode != 0) {
      return '\u62c9\u6d41\u9519\u8bef\u7801 $_playerErrorCode';
    }
    return '';
  }

  String get statusText {
    if (hasError) return '\u5f02\u5e38';
    if (_payload == null && !_joined) return '\u7a7a\u95f2';
    if (mediaReady) return '\u901a\u8bdd\u4e2d';
    switch (_phase) {
      case ZegoVoiceCallPhase.preparing:
        return '\u6b63\u5728\u521d\u59cb\u5316';
      case ZegoVoiceCallPhase.joiningRoom:
        return '\u6b63\u5728\u52a0\u5165\u623f\u95f4';
      case ZegoVoiceCallPhase.publishing:
        return '\u6b63\u5728\u63a8\u9001\u672c\u7aef\u97f3\u9891';
      case ZegoVoiceCallPhase.waitingPeerStream:
        return '\u7b49\u5f85\u5bf9\u7aef\u63a8\u6d41';
      case ZegoVoiceCallPhase.playing:
        if (!_publisherAudioFirstFrameSent) {
          return '\u7b49\u5f85\u672c\u7aef\u9ea6\u514b\u98ce';
        }
        return '\u7b49\u5f85\u8fdc\u7aef\u97f3\u9891';
      case ZegoVoiceCallPhase.connected:
        return '\u901a\u8bdd\u4e2d';
      case ZegoVoiceCallPhase.leaving:
        return '\u6b63\u5728\u6302\u65ad';
      case ZegoVoiceCallPhase.failed:
        return '\u5f02\u5e38';
      case ZegoVoiceCallPhase.idle:
        return _joined
            ? '\u6b63\u5728\u5efa\u7acb\u5a92\u4f53'
            : '\u7a7a\u95f2';
    }
  }

  Future<void> joinFromJson(String payloadJson) async {
    try {
      final decoded = jsonDecode(payloadJson);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('payload is not a JSON object');
      }
      final payload = ZegoVoiceCallPayload.fromJson(decoded);
      if (!payload.isValid) {
        throw const FormatException('invalid ZEGO voice-call payload');
      }
      await join(payload);
    } catch (e) {
      _setFailure(_friendlyError(e));
    }
  }

  Future<void> join(ZegoVoiceCallPayload payload) async {
    if (_wasRecentlyClosed(payload)) {
      _log('recently closed payload ignored room=${payload.roomId}');
      return;
    }
    final leaving = _leaveFuture;
    if (leaving != null) {
      final leavingPayload = _payload;
      if (leavingPayload != null && _payloadMatches(leavingPayload, payload)) {
        _log('payload ignored while leaving room=${payload.roomId}');
        return;
      }
      await leaving;
      if (_wasRecentlyClosed(payload)) {
        _log(
            'recently closed payload ignored after leave room=${payload.roomId}');
        return;
      }
    }
    if ((_joined || _joining) && _isSameCall(payload)) {
      _log('duplicate payload ignored room=${payload.roomId}');
      return;
    }
    if (_activeModel != null && _activeModel != this && _activeModel!.active) {
      _setFailure('\u672c\u673a\u5df2\u6709\u8bed\u97f3\u901a\u8bdd');
      return;
    }
    if (_joined || _joining) {
      await leave();
    }

    _activeModel = this;
    _resetForPayload(payload);
    _joining = true;
    _phase = ZegoVoiceCallPhase.preparing;
    notifyListeners();

    try {
      await _ensureEngine(
        payload.appId,
        userId: payload.userId,
        userName: payload.userName,
      );
      _installCallbacks();
      await _configureAudioOnlyEngine();

      _phase = ZegoVoiceCallPhase.joiningRoom;
      notifyListeners();
      final result = await _loginRoomWithIdentityRepair(payload);
      _roomErrorCode = result.errorCode;
      if (result.errorCode != 0) {
        throw StateError('loginRoom ${result.errorCode}');
      }

      _joined = true;
      _connectedAt = DateTime.now();
      _durationTimer?.cancel();
      _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (_joined) notifyListeners();
      });

      _phase = ZegoVoiceCallPhase.publishing;
      notifyListeners();
      await ZegoExpressEngine.instance
          .startPublishingStream(payload.publishStreamId);
      _publisherState = 'requested';
      _schedulePublishWatchdog();
      _phase = ZegoVoiceCallPhase.waitingPeerStream;
      await _startPlayingStream(payload.playStreamId,
          allowBeforePeerStream: true);
      _schedulePlayRetry(allowBeforePeerStream: true);
      notifyListeners();
    } catch (e) {
      _setFailure(_friendlyError(e));
    } finally {
      _joining = false;
      notifyListeners();
    }
  }

  Future<void> leave() async {
    final inFlight = _leaveFuture;
    if (inFlight != null) {
      await inFlight;
      return;
    }
    final future = _leaveInternal();
    _leaveFuture = future;
    try {
      await future;
    } finally {
      if (_leaveFuture == future) {
        _leaveFuture = null;
      }
    }
  }

  Future<void> _leaveInternal() async {
    final payload = _payload;
    if (payload == null && !_joined && _lastErrorText.isEmpty) return;

    _phase = ZegoVoiceCallPhase.leaving;
    notifyListeners();
    try {
      if (_engineCreated && payload != null) {
        if (_playStarted) {
          await ZegoExpressEngine.instance
              .stopPlayingStream(payload.playStreamId);
        }
        await ZegoExpressEngine.instance.stopPublishingStream();
        await ZegoExpressEngine.instance.logoutRoom(payload.roomId);
      }
    } catch (e) {
      _log('leave failed: $e');
    } finally {
      if (payload != null) {
        _rememberClosedPayload(payload);
      }
      _clearRuntimeState();
      if (_activeModel == this) {
        _activeModel = null;
      }
      notifyListeners();
    }
  }

  Future<void> setMuted(bool muted) async {
    if (!_joined) return;
    await ZegoExpressEngine.instance.muteMicrophone(muted);
    await ZegoExpressEngine.instance.mutePublishStreamAudio(muted);
    _muted = muted;
    notifyListeners();
  }

  Future<void> toggleMuted() => setMuted(!_muted);

  bool _isSameCall(ZegoVoiceCallPayload payload) {
    final current = _payload;
    return current != null && _payloadMatches(current, payload);
  }

  bool _payloadMatches(ZegoVoiceCallPayload left, ZegoVoiceCallPayload right) {
    return left.appId == right.appId &&
        left.roomId == right.roomId &&
        left.userId == right.userId &&
        left.publishStreamId == right.publishStreamId &&
        left.playStreamId == right.playStreamId;
  }

  String _callKey(ZegoVoiceCallPayload payload) {
    return [
      payload.appId,
      payload.roomId,
      payload.userId,
      payload.publishStreamId,
      payload.playStreamId,
    ].join('|');
  }

  void _rememberClosedPayload(ZegoVoiceCallPayload payload) {
    _pruneRecentlyClosedPayloads();
    _recentlyClosedCallKeys[_callKey(payload)] = DateTime.now();
  }

  bool _wasRecentlyClosed(ZegoVoiceCallPayload payload) {
    _pruneRecentlyClosedPayloads();
    return _recentlyClosedCallKeys.containsKey(_callKey(payload));
  }

  void _pruneRecentlyClosedPayloads() {
    final now = DateTime.now();
    _recentlyClosedCallKeys.removeWhere(
      (_, closedAt) => now.difference(closedAt) > _stalePayloadIgnoreWindow,
    );
  }

  void _resetForPayload(ZegoVoiceCallPayload payload) {
    _payload = payload;
    _phase = ZegoVoiceCallPhase.idle;
    _joined = false;
    _playStarted = false;
    _peerStreamOnline = false;
    _muted = false;
    _roomState = 'idle';
    _publisherState = 'idle';
    _playerState = 'idle';
    _lastErrorText = '';
    _roomErrorCode = 0;
    _publisherErrorCode = 0;
    _playerErrorCode = 0;
    _publisherAudioFirstFrameCaptured = false;
    _publisherAudioFirstFrameSent = false;
    _playerAudioFirstFrameReceived = false;
    _publishAudioFps = 0;
    _publishAudioKbps = 0;
    _playAudioFps = 0;
    _playAudioKbps = 0;
    _connectedAt = null;
    _durationTimer?.cancel();
    _durationTimer = null;
    _playRetryTimer?.cancel();
    _playRetryTimer = null;
    _publishWatchdogTimer?.cancel();
    _publishWatchdogTimer = null;
    _playAttempt = 0;
  }

  void _clearRuntimeState() {
    _payload = null;
    _joining = false;
    _joined = false;
    _playStarted = false;
    _peerStreamOnline = false;
    _muted = false;
    _phase = ZegoVoiceCallPhase.idle;
    _roomState = 'idle';
    _publisherState = 'idle';
    _playerState = 'idle';
    _roomErrorCode = 0;
    _publisherErrorCode = 0;
    _playerErrorCode = 0;
    _lastErrorText = '';
    _publisherAudioFirstFrameCaptured = false;
    _publisherAudioFirstFrameSent = false;
    _playerAudioFirstFrameReceived = false;
    _publishAudioFps = 0;
    _publishAudioKbps = 0;
    _playAudioFps = 0;
    _playAudioKbps = 0;
    _connectedAt = null;
    _durationTimer?.cancel();
    _durationTimer = null;
    _playRetryTimer?.cancel();
    _playRetryTimer = null;
    _publishWatchdogTimer?.cancel();
    _publishWatchdogTimer = null;
    _playAttempt = 0;
  }

  Future<ZegoRoomLoginResult> _loginRoomWithIdentityRepair(
      ZegoVoiceCallPayload payload) async {
    final firstResult = await _loginRoom(payload);
    if (firstResult.errorCode != _commonUserNotSameErrorCode) {
      return firstResult;
    }

    _log('loginRoom 1000020, recreating engine with current user identity');
    await _destroyEngine();
    await _ensureEngine(
      payload.appId,
      userId: payload.userId,
      userName: payload.userName,
    );
    _installCallbacks();
    await _configureAudioOnlyEngine();
    return _loginRoom(payload);
  }

  Future<ZegoRoomLoginResult> _loginRoom(ZegoVoiceCallPayload payload) {
    final config = ZegoRoomConfig.defaultConfig();
    config.isUserStatusNotify = true;
    config.token = payload.token;
    return ZegoExpressEngine.instance.loginRoom(
      payload.roomId,
      ZegoUser(payload.userId, payload.userName),
      config: config,
    );
  }

  Future<void> _ensureEngine(
    int appId, {
    required String userId,
    required String userName,
  }) async {
    final userChanged = _engineUserId != userId || _engineUserName != userName;
    if (_engineCreated && _engineAppId == appId && !userChanged) return;
    if (_engineCreated) {
      await _destroyEngine();
    }

    final profile = ZegoEngineProfile(
      appId,
      ZegoScenario.StandardVoiceCall,
      appSign: '',
    );
    await ZegoExpressEngine.createEngineWithProfile(profile);
    _engineCreated = true;
    _engineAppId = appId;
    _engineUserId = userId;
    _engineUserName = userName;
  }

  Future<void> _destroyEngine() async {
    if (!_engineCreated) return;
    await ZegoExpressEngine.destroyEngine();
    _engineCreated = false;
    _engineAppId = null;
    _engineUserId = null;
    _engineUserName = null;
  }

  Future<void> _configureAudioOnlyEngine() async {
    final engine = ZegoExpressEngine.instance;
    await engine.enableCamera(false);
    await engine.enableAudioCaptureDevice(true);
    await engine.mutePublishStreamVideo(true);
    await engine.mutePublishStreamAudio(false);
    await engine.muteAllPlayStreamAudio(false);
    await engine.muteMicrophone(false);
    await engine.muteSpeaker(false);
    if (defaultTargetPlatform == TargetPlatform.android) {
      await engine.setAudioRouteToSpeaker(true);
    }
  }

  void _installCallbacks() {
    ZegoExpressEngine.onRoomStateChanged =
        (roomId, reason, errorCode, extendedData) {
      final payload = _payload;
      if (payload == null || roomId != payload.roomId) return;
      _roomState = _stateName(reason);
      _roomErrorCode = errorCode;
      if (errorCode != 0) {
        _phase = ZegoVoiceCallPhase.failed;
      } else {
        _lastErrorText = '';
      }
      notifyListeners();
    };
    ZegoExpressEngine.onPublisherStateUpdate =
        (streamId, state, errorCode, extendedData) {
      final payload = _payload;
      if (payload == null || streamId != payload.publishStreamId) return;
      _publisherState = _stateName(state);
      _publisherErrorCode = errorCode;
      if (errorCode != 0) {
        _phase = ZegoVoiceCallPhase.failed;
      } else {
        _lastErrorText = '';
      }
      notifyListeners();
    };
    ZegoExpressEngine.onPlayerStateUpdate =
        (streamId, state, errorCode, extendedData) {
      final payload = _payload;
      if (payload == null || streamId != payload.playStreamId) return;
      _playerState = _stateName(state);
      if (errorCode == 0 ||
          _peerStreamOnline ||
          _playerAudioFirstFrameReceived) {
        _playerErrorCode = errorCode;
      }
      final normalized = _playerState.toLowerCase();
      if (errorCode == 0 && normalized.contains('playing')) {
        _lastErrorText = '';
        _phase = _publisherAudioFirstFrameSent && _playerAudioFirstFrameReceived
            ? ZegoVoiceCallPhase.connected
            : ZegoVoiceCallPhase.playing;
      } else if (errorCode != 0 ||
          normalized.contains('no') ||
          normalized.contains('stop')) {
        _playStarted = false;
        if (_peerStreamOnline || _playerAudioFirstFrameReceived) {
          _phase = ZegoVoiceCallPhase.failed;
        } else {
          _phase = ZegoVoiceCallPhase.waitingPeerStream;
          _schedulePlayRetry(allowBeforePeerStream: true);
        }
      }
      notifyListeners();
    };
    ZegoExpressEngine.onPublisherQualityUpdate = (streamId, quality) {
      final payload = _payload;
      if (payload == null || streamId != payload.publishStreamId) return;
      _publishAudioFps = quality.audioSendFPS;
      _publishAudioKbps = quality.audioKBPS;
      notifyListeners();
    };
    ZegoExpressEngine.onPlayerQualityUpdate = (streamId, quality) {
      final payload = _payload;
      if (payload == null || streamId != payload.playStreamId) return;
      _playAudioFps = quality.audioRecvFPS;
      _playAudioKbps = quality.audioKBPS;
      notifyListeners();
    };
    ZegoExpressEngine.onPublisherCapturedAudioFirstFrame = () {
      if (_payload == null || !_joined) return;
      _publisherAudioFirstFrameCaptured = true;
      _lastErrorText = '';
      notifyListeners();
    };
    ZegoExpressEngine.onPublisherSendAudioFirstFrame = (channel) {
      if (_payload == null || !_joined) return;
      _publisherAudioFirstFrameCaptured = true;
      _publisherAudioFirstFrameSent = true;
      _publisherErrorCode = 0;
      _lastErrorText = '';
      _publishWatchdogTimer?.cancel();
      _publishWatchdogTimer = null;
      if (_playerAudioFirstFrameReceived) {
        _phase = ZegoVoiceCallPhase.connected;
      }
      notifyListeners();
    };
    ZegoExpressEngine.onPlayerRecvAudioFirstFrame = (streamId) {
      final payload = _payload;
      if (payload == null || streamId != payload.playStreamId) return;
      _playerAudioFirstFrameReceived = true;
      _peerStreamOnline = true;
      _playerErrorCode = 0;
      _lastErrorText = '';
      _playRetryTimer?.cancel();
      _playRetryTimer = null;
      if (_publisherAudioFirstFrameSent) {
        _phase = ZegoVoiceCallPhase.connected;
      }
      notifyListeners();
    };
    ZegoExpressEngine.onRoomStreamUpdate =
        (roomId, updateType, streamList, extendedData) {
      final payload = _payload;
      if (payload == null || roomId != payload.roomId) return;
      final hasPeerStream =
          streamList.any((stream) => stream.streamID == payload.playStreamId);
      if (!hasPeerStream) return;

      if (updateType == ZegoUpdateType.Add) {
        _peerStreamOnline = true;
        _playerErrorCode = 0;
        _playAttempt = 0;
        _phase = ZegoVoiceCallPhase.playing;
        notifyListeners();
        unawaited(_startPlayingStream(payload.playStreamId));
      } else if (updateType == ZegoUpdateType.Delete) {
        _peerStreamOnline = false;
        _playStarted = false;
        _playerAudioFirstFrameReceived = false;
        _playerState = 'peer stream deleted';
        _phase = ZegoVoiceCallPhase.waitingPeerStream;
        _playRetryTimer?.cancel();
        _playRetryTimer = null;
        unawaited(ZegoExpressEngine.instance
            .stopPlayingStream(payload.playStreamId)
            .catchError((e) => _log('stop play failed: $e')));
        notifyListeners();
      }
    };
  }

  Future<void> _startPlayingStream(String streamId,
      {bool allowBeforePeerStream = false}) async {
    if (_playStarted ||
        !_joined ||
        streamId.isEmpty ||
        (!allowBeforePeerStream && !_peerStreamOnline)) {
      return;
    }
    try {
      await ZegoExpressEngine.instance.startPlayingStream(streamId);
      await ZegoExpressEngine.instance.mutePlayStreamAudio(streamId, false);
      _playStarted = true;
      _playerState = 'requested';
      _phase = _peerStreamOnline
          ? ZegoVoiceCallPhase.playing
          : ZegoVoiceCallPhase.waitingPeerStream;
      _schedulePlayRetry(allowBeforePeerStream: allowBeforePeerStream);
      notifyListeners();
    } catch (e) {
      _playStarted = false;
      if (_peerStreamOnline) {
        _lastErrorText = _friendlyError(e);
        _phase = ZegoVoiceCallPhase.failed;
      } else {
        _phase = ZegoVoiceCallPhase.waitingPeerStream;
        _schedulePlayRetry(allowBeforePeerStream: true);
      }
      notifyListeners();
    }
  }

  void _schedulePlayRetry({bool allowBeforePeerStream = false}) {
    if (!_joined ||
        (!allowBeforePeerStream && !_peerStreamOnline) ||
        _playerAudioFirstFrameReceived ||
        _phase == ZegoVoiceCallPhase.failed) {
      return;
    }
    _playRetryTimer?.cancel();
    if (_playAttempt >= 30) {
      _playRetryTimer = null;
      _lastErrorText = _peerStreamOnline
          ? '\u672a\u6536\u5230\u8fdc\u7aef\u97f3\u9891'
          : '\u672a\u53d1\u73b0\u5bf9\u7aef\u63a8\u6d41';
      _phase = ZegoVoiceCallPhase.failed;
      notifyListeners();
      return;
    }
    _playRetryTimer = Timer(const Duration(seconds: 2), () {
      final payload = _payload;
      if (payload == null ||
          !_joined ||
          (!allowBeforePeerStream && !_peerStreamOnline) ||
          _phase == ZegoVoiceCallPhase.failed ||
          _playerAudioFirstFrameReceived) {
        return;
      }
      _playAttempt += 1;
      _playStarted = false;
      unawaited(_startPlayingStream(
        payload.playStreamId,
        allowBeforePeerStream: allowBeforePeerStream,
      ));
    });
  }

  void _schedulePublishWatchdog() {
    _publishWatchdogTimer?.cancel();
    _publishWatchdogTimer = Timer(const Duration(seconds: 20), () {
      if (!_joined ||
          _publisherAudioFirstFrameSent ||
          _phase == ZegoVoiceCallPhase.failed) {
        return;
      }
      _lastErrorText = _publisherAudioFirstFrameCaptured
          ? '\u672c\u7aef\u97f3\u9891\u672a\u53d1\u9001'
          : '\u672a\u91c7\u96c6\u5230\u672c\u7aef\u97f3\u9891';
      _phase = ZegoVoiceCallPhase.failed;
      _playRetryTimer?.cancel();
      _playRetryTimer = null;
      notifyListeners();
    });
  }

  void _setFailure(String text) {
    _lastErrorText = text;
    _phase = ZegoVoiceCallPhase.failed;
    _joining = false;
    _playRetryTimer?.cancel();
    _playRetryTimer = null;
    _publishWatchdogTimer?.cancel();
    _publishWatchdogTimer = null;
    if (_activeModel == this && _payload == null) {
      _activeModel = null;
    }
    _log(text);
    notifyListeners();
  }

  String _friendlyError(Object error) {
    final text = error.toString();
    final code = RegExp(r'(\d{5,})').firstMatch(text)?.group(1);
    if (code == '1002033') {
      return 'ZEGO Token \u9274\u6743\u5931\u8d25';
    }
    if (code == _commonUserNotSameErrorCode.toString()) {
      return 'ZEGO \u7528\u6237\u8eab\u4efd\u4e0d\u4e00\u81f4';
    }
    if (code != null) return 'ZEGO \u9519\u8bef\u7801 $code';
    if (text.contains('MissingPluginException')) {
      return 'ZEGO \u63d2\u4ef6\u672a\u6ce8\u518c';
    }
    if (text.toLowerCase().contains('permission')) {
      return '\u9ea6\u514b\u98ce\u6743\u9650\u4e0d\u53ef\u7528';
    }
    return 'ZEGO \u901a\u8bdd\u5efa\u7acb\u5931\u8d25';
  }

  String _stateName(Object? state) {
    final text = state.toString();
    final dot = text.lastIndexOf('.');
    return dot >= 0 ? text.substring(dot + 1) : text;
  }

  String _zegoStateText(String state) {
    final normalized = state.toLowerCase();
    if (normalized == 'idle') return '\u7a7a\u95f2';
    if (normalized.contains('requested') || normalized.contains('requesting')) {
      return '\u8bf7\u6c42\u4e2d';
    }
    if (normalized.contains('failed') || normalized.contains('kickout')) {
      return '\u5f02\u5e38';
    }
    if (normalized.contains('success')) {
      return '\u6b63\u5e38';
    }
    if (normalized.contains('connecting') ||
        normalized.contains('logging') ||
        normalized.contains('login') ||
        normalized.contains('reconnect')) {
      return '\u8fde\u63a5\u4e2d';
    }
    if (normalized.contains('connected') ||
        normalized.contains('publishing') ||
        normalized.contains('playing')) {
      return '\u6b63\u5e38';
    }
    if (normalized.contains('disconnected') || normalized.contains('logout')) {
      return '\u5df2\u65ad\u5f00';
    }
    if (normalized.contains('deleted')) {
      return '\u5bf9\u7aef\u5df2\u505c\u6b62';
    }
    if (normalized.contains('no')) return '\u65e0\u72b6\u6001';
    return state.isEmpty ? '\u672a\u77e5' : state;
  }

  String _formatAudioQuality(double fps, double kbps) {
    if (fps <= 0 && kbps <= 0) return '--';
    return '${fps.toStringAsFixed(1)}fps / ${kbps.toStringAsFixed(1)}kbps';
  }

  void _log(String message) {
    if (kDebugMode) {
      debugPrint('CloudSendZegoVoice $message');
    }
  }
}
