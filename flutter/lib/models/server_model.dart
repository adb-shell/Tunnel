import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_hbb/consts.dart';
import 'package:flutter_hbb/main.dart';
import 'package:flutter_hbb/mobile/pages/settings_page.dart';
import 'package:flutter_hbb/models/chat_model.dart';
import 'package:flutter_hbb/models/platform_model.dart';
import 'package:get/get.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:window_manager/window_manager.dart';

import '../common.dart';
import '../common/formatter/id_formatter.dart';
import '../desktop/pages/server_page.dart' as desktop;
import '../desktop/widgets/tabbar_widget.dart';
import '../mobile/pages/server_page.dart';
import 'model.dart';

const kLoginDialogTag = "LOGIN";
const kVoiceCallDialogTag = "VOICE_CALL";
const kAndroidVoiceCallAutoAcceptSeconds = 3;

const kUseTemporaryPassword = "use-temporary-password";
const kUsePermanentPassword = "use-permanent-password";
const kUseBothPasswords = "use-both-passwords";

class _NativeVoiceCallState {
  final bool disconnected;
  final bool inVoiceCall;
  final bool incomingVoiceCall;

  const _NativeVoiceCallState({
    required this.disconnected,
    required this.inVoiceCall,
    required this.incomingVoiceCall,
  });

  bool get hasLiveVoiceCall =>
      !disconnected && (inVoiceCall || incomingVoiceCall);
}

bool _jsonBool(dynamic value) {
  if (value is bool) return value;
  return value?.toString() == 'true';
}

class ServerModel with ChangeNotifier {
  bool _isStart = false; // Android screen sharing status
  bool _coreServiceStarted = false; // Android connection/id service status
  bool _mediaOk = false;
  bool _inputOk = false;
  bool _audioOk = false;
  bool _fileOk = false;
  bool _clipboardOk = false;
  bool _showElevation = false;
  bool hideCm = false;
  int _connectStatus = 0; // Rendezvous Server status
  String _verificationMethod = "";
  String _temporaryPasswordLength = "";
  bool _allowNumericOneTimePassword = false;
  String _approveMode = "";
  int _zeroClientLengthCounter = 0;

  late String _emptyIdShow;
  late final IDTextEditingController _serverId;
  final _serverPasswd =
      TextEditingController(text: translate("Generating ..."));

  final tabController = DesktopTabController(tabType: DesktopTabType.cm);

  final List<Client> _clients = [];
  final Map<int, Timer> _voiceCallAutoAcceptTimers = {};
  final Set<int> _voiceCallAutoAcceptSubmitting = {};
  Future<void> _voiceCallStateUpdateQueue = Future.value();

  Timer? cmHiddenTimer;

  bool get isStart => _isStart;

  bool get mediaOk => _mediaOk;

  bool get inputOk => _inputOk;

  bool get audioOk => _audioOk;

  bool get fileOk => _fileOk;

  bool get clipboardOk => _clipboardOk;

  bool get showElevation => _showElevation;

  int get connectStatus => _connectStatus;

  String get verificationMethod {
    final index = [
      kUseTemporaryPassword,
      kUsePermanentPassword,
      kUseBothPasswords
    ].indexOf(_verificationMethod);
    if (index < 0) {
      return kUseBothPasswords;
    }
    return _verificationMethod;
  }

  String get approveMode => _approveMode;

  setVerificationMethod(String method) async {
    await bind.mainSetOption(key: kOptionVerificationMethod, value: method);
    /*
    if (method != kUsePermanentPassword) {
      await bind.mainSetOption(
          key: 'allow-hide-cm', value: bool2option('allow-hide-cm', false));
    }
    */
  }

  String get temporaryPasswordLength {
    final lengthIndex = ["6", "8", "10"].indexOf(_temporaryPasswordLength);
    if (lengthIndex < 0) {
      return "6";
    }
    return _temporaryPasswordLength;
  }

  setTemporaryPasswordLength(String length) async {
    await bind.mainSetOption(key: "temporary-password-length", value: length);
  }

  setApproveMode(String mode) async {
    await bind.mainSetOption(key: kOptionApproveMode, value: mode);
    /*
    if (mode != 'password') {
      await bind.mainSetOption(
          key: 'allow-hide-cm', value: bool2option('allow-hide-cm', false));
    }
    */
  }

  bool get allowNumericOneTimePassword => _allowNumericOneTimePassword;
  switchAllowNumericOneTimePassword() async {
    await mainSetBoolOption(
        kOptionAllowNumericOneTimePassword, !_allowNumericOneTimePassword);
  }

  TextEditingController get serverId => _serverId;

  TextEditingController get serverPasswd => _serverPasswd;

  List<Client> get clients => _clients;

  final controller = ScrollController();

  WeakReference<FFI> parent;

  ServerModel(this.parent) {
    _emptyIdShow = translate("Generating ...");
    _serverId = IDTextEditingController(text: _emptyIdShow);

    /*
    // initital _hideCm at startup
    final verificationMethod =
        bind.mainGetOptionSync(key: kOptionVerificationMethod);
    final approveMode = bind.mainGetOptionSync(key: kOptionApproveMode);
    _hideCm = option2bool(
        'allow-hide-cm', bind.mainGetOptionSync(key: 'allow-hide-cm'));
    if (!(approveMode == 'password' &&
        verificationMethod == kUsePermanentPassword)) {
      _hideCm = false;
    }
    */

    timerCallback() async {
      final connectionStatus =
          jsonDecode(await bind.mainGetConnectStatus()) as Map<String, dynamic>;
      final statusNum = connectionStatus['status_num'] as int;
      if (statusNum != _connectStatus) {
        _connectStatus = statusNum;
        notifyListeners();
      }

      if (desktopType == DesktopType.cm) {
        final res = await bind.cmCheckClientsLength(length: _clients.length);
        if (res != null) {
          debugPrint("clients not match!");
          updateClientState(res);
        } else {
          if (_clients.isEmpty) {
            hideCmWindow();
            if (_zeroClientLengthCounter++ == 12) {
              // 6 second
              windowManager.close();
            }
          } else {
            _zeroClientLengthCounter = 0;
            if (!hideCm) showCmWindow();
          }
        }
      }

      updatePasswordModel();
    }

    if (!isTest) {
      Future.delayed(Duration.zero, () async {
        if (await bind.optionSynced()) {
          await timerCallback();
        }
      });
      Timer.periodic(Duration(milliseconds: 500), (timer) async {
        await timerCallback();
      });
    }

    // Initial keyboard status is off on mobile
    if (isMobile) {
      bind.mainSetOption(key: kOptionEnableKeyboard, value: 'N');
    }
  }

  /// 1. check android permission
  /// 2. check config
  /// audio true by default (if permission on) (false default < Android 10)
  /// file true by default (if permission on)
  checkAndroidPermission() async {
    // audio
    if (androidVersion < 30 ||
        !await AndroidPermissionManager.check(kRecordAudio)) {
      _audioOk = false;
      bind.mainSetOption(key: kOptionEnableAudio, value: "N");
    } else {
      final audioOption = await bind.mainGetOption(key: kOptionEnableAudio);
      _audioOk = audioOption != 'N';
    }

    // file
    //if (!await AndroidPermissionManager.check(kManageExternalStorage)) {
    _fileOk = false;
    bind.mainSetOption(key: kOptionEnableFileTransfer, value: "N");
    /* } else {
      final fileOption =
          await bind.mainGetOption(key: kOptionEnableFileTransfer);
      _fileOk = fileOption != 'N';
    }*/

    // clipboard
    bind.mainSetOption(key: kOptionEnableClipboard, value: defaultOptionYes);
    _clipboardOk = true;

    notifyListeners();
  }

  updatePasswordModel() async {
    var update = false;
    final temporaryPassword = await bind.mainGetTemporaryPassword();
    final verificationMethod =
        await bind.mainGetOption(key: kOptionVerificationMethod);
    final temporaryPasswordLength =
        await bind.mainGetOption(key: "temporary-password-length");
    final approveMode = await bind.mainGetOption(key: kOptionApproveMode);
    final numericOneTimePassword =
        await mainGetBoolOption(kOptionAllowNumericOneTimePassword);
    /*
    var hideCm = option2bool(
        'allow-hide-cm', await bind.mainGetOption(key: 'allow-hide-cm'));
    if (!(approveMode == 'password' &&
        verificationMethod == kUsePermanentPassword)) {
      hideCm = false;
    }
    */
    if (_approveMode != approveMode) {
      _approveMode = approveMode;
      update = true;
    }
    var stopped = await mainGetBoolOption(kOptionStopService);
    final oldPwdText = _serverPasswd.text;
    if (stopped ||
        verificationMethod == kUsePermanentPassword ||
        _approveMode == 'click') {
      _serverPasswd.text = '-';
    } else {
      if (_serverPasswd.text != temporaryPassword &&
          temporaryPassword.isNotEmpty) {
        _serverPasswd.text = temporaryPassword;
      }
    }
    if (oldPwdText != _serverPasswd.text) {
      update = true;
    }
    if (_verificationMethod != verificationMethod) {
      _verificationMethod = verificationMethod;
      update = true;
    }
    if (_temporaryPasswordLength != temporaryPasswordLength) {
      if (_temporaryPasswordLength.isNotEmpty) {
        bind.mainUpdateTemporaryPassword();
      }
      _temporaryPasswordLength = temporaryPasswordLength;
      update = true;
    }
    if (_allowNumericOneTimePassword != numericOneTimePassword) {
      _allowNumericOneTimePassword = numericOneTimePassword;
      update = true;
    }
    /*
    if (_hideCm != hideCm) {
      _hideCm = hideCm;
      if (desktopType == DesktopType.cm) {
        if (hideCm) {
          await hideCmWindow();
        } else {
          await showCmWindow();
        }
      }
      update = true;
    }
    */
    if (update) {
      notifyListeners();
    }
  }

  toggleAudio() async {
    if (clients.isNotEmpty) {
      await showClientsMayNotBeChangedAlert(parent.target);
    }
    if (!_audioOk && !await AndroidPermissionManager.check(kRecordAudio)) {
      final res = await AndroidPermissionManager.request(kRecordAudio);
      if (!res) {
        showToast(translate('Failed'));
        return;
      }
    }

    _audioOk = !_audioOk;
    bind.mainSetOption(
        key: kOptionEnableAudio, value: _audioOk ? defaultOptionYes : 'N');
    notifyListeners();
  }

  toggleFile() async {
    if (clients.isNotEmpty) {
      await showClientsMayNotBeChangedAlert(parent.target);
    }
    /*
    if (!_fileOk &&
        !await AndroidPermissionManager.check(kManageExternalStorage)) {
      final res =
          await AndroidPermissionManager.request(kManageExternalStorage);
      if (!res) {
        showToast(translate('Failed'));
        return;
      }
    }*/

    _fileOk = !_fileOk;
    bind.mainSetOption(
        key: kOptionEnableFileTransfer,
        value: _fileOk ? defaultOptionYes : 'N');
    notifyListeners();
  }

  toggleClipboard() async {
    _clipboardOk = !clipboardOk;
    bind.mainSetOption(
        key: kOptionEnableClipboard,
        value: clipboardOk ? defaultOptionYes : 'N');
    notifyListeners();
  }

  toggleInput() async {
    if (clients.isNotEmpty) {
      await showClientsMayNotBeChangedAlert(parent.target);
    }
    if (_inputOk) {
      parent.target?.invokeMethod("stop_input");
      bind.mainSetOption(key: kOptionEnableKeyboard, value: 'N');
    } else {
      if (parent.target != null) {
        /// the result of toggle-on depends on user actions in the settings page.
        /// handle result, see [ServerModel.changeStatue]
        showInputWarnAlert(parent.target!);
      }
    }
  }

  Future<bool> checkRequestNotificationPermission() async {
    debugPrint("androidVersion $androidVersion");
    if (androidVersion < 33) {
      return true;
    }
    if (await AndroidPermissionManager.check(kAndroid13Notification)) {
      debugPrint("notification permission already granted");
      return true;
    }
    var res = await AndroidPermissionManager.request(kAndroid13Notification);
    debugPrint("notification permission request result: $res");
    return res;
  }

  Future<bool> checkFloatingWindowPermission() async {
    debugPrint("androidVersion $androidVersion");
    // return false;
    if (androidVersion < 23) {
      return false;
    }
    if (await AndroidPermissionManager.check(kSystemAlertWindow)) {
      debugPrint("alert window permission already granted");
      return true;
    }
    var res = await AndroidPermissionManager.request(kSystemAlertWindow);
    debugPrint("alert window permission request result: $res");
    return res;
  }

  Future<void> ensureCoreService() async {
    if (!isAndroid) return;
    final ffi = parent.target;
    ffi?.ffiModel.updateEventListener(ffi.sessionId, "");
    await ffi?.invokeMethod("ensure_core_service");
    if (_coreServiceStarted) {
      return;
    }
    await bind.mainStartService();
    _coreServiceStarted = true;
    updateClientState();
    notifyListeners();
  }

  /// Toggle Android screen sharing. The core connection/id service stays alive.
  toggleService() async {
    if (_isStart || _mediaOk) {
      final res = await parent.target?.dialogManager
          .show<bool>((setState, close, context) {
        submit() => close(true);
        return CustomAlertDialog(
          title: Row(children: [
            const Icon(Icons.warning_amber_sharp,
                color: Colors.redAccent, size: 28),
            const SizedBox(width: 10),
            Text(translate("Warning")),
          ]),
          content: Text(translate("android_stop_service_tip")),
          actions: [
            TextButton(onPressed: close, child: Text(translate("Cancel"))),
            TextButton(onPressed: submit, child: Text(translate("OK"))),
          ],
          onSubmit: submit,
          onCancel: close,
        );
      });
      if (res == true) {
        stopService();
      }
    } else {
      startService();
    }
  }

  /// Start screen sharing only. Core service is started separately and kept alive.
  Future<void> startService() async {
    if (!isAndroid) {
      _isStart = true;
      notifyListeners();
      parent.target?.ffiModel.updateEventListener(parent.target!.sessionId, "");
      await bind.mainStartService();
      updateClientState();
      return;
    }
    await ensureCoreService();
    await parent.target?.invokeMethod("start_screen_share");
    updateClientState();
  }

  void onMediaProjectionDenied() {
    if (_isStart || _mediaOk) {
      debugPrint("MediaProjection denied by user, rollback screen sharing");
    }
    _isStart = false;
    _mediaOk = false;
    notifyListeners();
    updateClientState();
    if (isAndroid) {
      androidUpdatekeepScreenOn();
    }
  }

  /// Stop screen sharing only. Do not stop the core connection/id service.
  Future<void> stopService() async {
    if (!isAndroid) {
      _isStart = false;
      closeAll();
      await parent.target?.invokeMethod("stop_service");
      await bind.mainStopService();
      notifyListeners();
      if (!isLinux) {
        WakelockPlus.disable();
      }
      return;
    }
    _isStart = false;
    _mediaOk = false;
    await parent.target?.invokeMethod("stop_screen_share");
    notifyListeners();
    updateClientState();
    androidUpdatekeepScreenOn();
  }

  Future<bool> setPermanentPassword(String newPW) async {
    await bind.mainSetPermanentPassword(password: newPW);
    await Future.delayed(Duration(milliseconds: 500));
    final pw = await bind.mainGetPermanentPassword();
    if (newPW == pw) {
      return true;
    } else {
      return false;
    }
  }

  fetchID() async {
    final id = await bind.mainGetMyId();
    if (id != _serverId.id) {
      _serverId.id = id;
      notifyListeners();
    }
  }

  changeStatue(String name, bool value) {
    debugPrint("changeStatue value $value");
    switch (name) {
      case "media":
        _mediaOk = value;
        _isStart = value;
        break;
      case "input":
        if (_inputOk != value) {
          bind.mainSetOption(
              key: kOptionEnableKeyboard,
              value: value ? defaultOptionYes : 'N');
        }
        _inputOk = value;
        break;
      default:
        return;
    }
    notifyListeners();
  }

  // force
  updateClientState([String? json]) async {
    if (isTest) return;
    var res = await bind.cmGetClientsState();
    List<dynamic> clientsJson;
    try {
      clientsJson = jsonDecode(res);
    } catch (e) {
      debugPrint("Failed to decode clientsJson: '$res', error $e");
      return;
    }

    final oldClientLenght = _clients.length;
    _clients.clear();
    tabController.state.value.tabs.clear();

    for (var clientJson in clientsJson) {
      try {
        final client = Client.fromJson(clientJson);
        _clients.add(client);
        _addTab(client);
      } catch (e) {
        debugPrint("Failed to decode clientJson '$clientJson', error $e");
      }
    }
    _clearDisconnectedVoiceCallState();
    final hasLiveVoiceCall = _clients.any((client) =>
        !client.disconnected &&
        (client.inVoiceCall || client.incomingVoiceCall));
    if (!hasLiveVoiceCall) {
      unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
    }
    if (desktopType == DesktopType.cm) {
      if (_clients.isEmpty) {
        hideCmWindow();
      } else if (!hideCm) {
        showCmWindow();
      }
    }
    if (_clients.length != oldClientLenght) {
      notifyListeners();
      if (isAndroid) androidUpdatekeepScreenOn();
    }
  }

  void addConnection(Map<String, dynamic> evt) {
    try {
      final client = Client.fromJson(jsonDecode(evt["client"]));
      if (client.authorized) {
        parent.target?.dialogManager.dismissByTag(getLoginDialogTag(client.id));
        final index = _clients.indexWhere((c) => c.id == client.id);
        if (index < 0) {
          _clients.add(client);
        } else {
          _clients[index].authorized = true;
        }
      } else {
        if (_clients.any((c) => c.id == client.id)) {
          return;
        }
        _clients.add(client);

        sendLoginResponse(client, true);
      }
      _addTab(client);
      // remove disconnected
      final index_disconnected = _clients
          .indexWhere((c) => c.disconnected && c.peerId == client.peerId);
      if (index_disconnected >= 0) {
        _clients.removeAt(index_disconnected);
        tabController.remove(index_disconnected);
      }
      if (desktopType == DesktopType.cm && !hideCm) {
        showCmWindow();
      }
      scrollToBottom();
      notifyListeners();
      // if (isAndroid && !client.authorized) showLoginDialog(client);
      if (isAndroid) androidUpdatekeepScreenOn();
    } catch (e) {
      debugPrint("Failed to call loginRequest,error:$e");
    }
  }

  void _addTab(Client client) {
    tabController.add(TabInfo(
        key: client.id.toString(),
        label: client.name,
        closable: false,
        onTap: () {},
        page: desktop.buildConnectionCard(client)));
    Future.delayed(Duration.zero, () async {
      if (!hideCm) windowOnTop(null);
    });
    // Only do the hidden task when on Desktop.
    if (client.authorized && isDesktop) {
      cmHiddenTimer = Timer(const Duration(seconds: 3), () {
        if (!hideCm) windowManager.minimize();
        cmHiddenTimer = null;
      });
    }
    parent.target?.chatModel
        .updateConnIdOfKey(MessageKey(client.peerId, client.id));
  }

  void showLoginDialog(Client client) {
    // showClientDialog(
    //   client,
    //   client.isFileTransfer
    //       ? "Transfer file"
    //       : client.isViewCamera
    //           ? "View camera"
    //           : client.isTerminal
    //               ? "Terminal"
    //               : "Share screen",
    //   'Do you accept?',
    //   'android_new_connection_tip',
    //   () => sendLoginResponse(client, false),
    //   () => sendLoginResponse(client, true),
    // );
  }

  Client? _findClientById(int id) {
    for (final client in _clients) {
      if (client.id == id) {
        return client;
      }
    }
    return null;
  }

  bool _isIncomingZegoVoiceCallPending(int clientId) {
    final client = _findClientById(clientId);
    return client != null && client.incomingVoiceCall && !client.inVoiceCall;
  }

  void _cancelVoiceCallAutoAcceptTimer(int clientId) {
    _voiceCallAutoAcceptTimers.remove(clientId)?.cancel();
    _voiceCallAutoAcceptSubmitting.remove(clientId);
  }

  void _cancelAllVoiceCallAutoAcceptTimers() {
    for (final timer in _voiceCallAutoAcceptTimers.values) {
      timer.cancel();
    }
    _voiceCallAutoAcceptTimers.clear();
    _voiceCallAutoAcceptSubmitting.clear();
  }

  void _clearDisconnectedVoiceCallState() {
    var changed = false;
    for (final client in _clients) {
      if (!client.disconnected ||
          (!client.inVoiceCall && !client.incomingVoiceCall)) {
        continue;
      }
      _cancelVoiceCallAutoAcceptTimer(client.id);
      parent.target?.dialogManager
          .dismissByTag(getVoiceCallDialogTag(client.id));
      parent.target?.invokeMethod("cancel_notification", client.id);
      client.inVoiceCall = false;
      client.incomingVoiceCall = false;
      changed = true;
    }
    if (!changed) {
      return;
    }
    final hasLiveVoiceCall = _clients.any((client) =>
        !client.disconnected &&
        (client.inVoiceCall || client.incomingVoiceCall));
    if (!hasLiveVoiceCall) {
      unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
    }
    notifyListeners();
  }

  Future<Map<int, _NativeVoiceCallState>?> _loadNativeVoiceCallState() async {
    try {
      final res = await bind.cmGetClientsState();
      final clientsJson = jsonDecode(res);
      if (clientsJson is! List) {
        return null;
      }
      final states = <int, _NativeVoiceCallState>{};
      for (final clientJson in clientsJson) {
        if (clientJson is! Map) {
          continue;
        }
        final rawId = clientJson['id'];
        final id = rawId is int ? rawId : int.tryParse(rawId?.toString() ?? '');
        if (id != null) {
          states[id] = _NativeVoiceCallState(
            disconnected: _jsonBool(clientJson['disconnected']),
            inVoiceCall: _jsonBool(clientJson['in_voice_call']),
            incomingVoiceCall: _jsonBool(clientJson['incoming_voice_call']),
          );
        }
      }
      return states;
    } catch (e) {
      debugPrint("_loadNativeVoiceCallState failed: $e");
      return null;
    }
  }

  void _clearStaleVoiceCallState(
      Map<int, _NativeVoiceCallState> nativeVoiceState) {
    var changed = false;
    for (final client in _clients) {
      final nativeState = nativeVoiceState[client.id];
      if ((nativeState?.hasLiveVoiceCall ?? false) ||
          (!client.inVoiceCall && !client.incomingVoiceCall)) {
        continue;
      }
      _cancelVoiceCallAutoAcceptTimer(client.id);
      parent.target?.dialogManager
          .dismissByTag(getVoiceCallDialogTag(client.id));
      parent.target?.invokeMethod("cancel_notification", client.id);
      client.inVoiceCall = false;
      client.incomingVoiceCall = false;
      changed = true;
    }
    if (!changed) {
      return;
    }
    final hasLiveVoiceCall =
        nativeVoiceState.values.any((state) => state.hasLiveVoiceCall);
    if (!hasLiveVoiceCall) {
      unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
    }
    notifyListeners();
  }

  void _submitZegoVoiceCallAccept(Client client) {
    final clientId = client.id;
    if (_voiceCallAutoAcceptSubmitting.contains(clientId) ||
        !_isIncomingZegoVoiceCallPending(clientId)) {
      return;
    }
    _voiceCallAutoAcceptSubmitting.add(clientId);
    unawaited(_acceptZegoVoiceCall(client).whenComplete(() {
      _voiceCallAutoAcceptSubmitting.remove(clientId);
    }));
  }

  void _startVoiceCallAutoAcceptTimer(Client client) {
    if (!isAndroid || _voiceCallAutoAcceptTimers.containsKey(client.id)) {
      return;
    }
    final clientId = client.id;
    _voiceCallAutoAcceptTimers[clientId] = Timer(
        const Duration(seconds: kAndroidVoiceCallAutoAcceptSeconds), () {
      _voiceCallAutoAcceptTimers.remove(clientId);
      final current = _findClientById(clientId);
      if (current == null || !_isIncomingZegoVoiceCallPending(clientId)) {
        return;
      }
      _submitZegoVoiceCallAccept(current);
    });
  }

  handleVoiceCall(Client client, bool accept) {
    _cancelVoiceCallAutoAcceptTimer(client.id);
    parent.target?.invokeMethod("cancel_notification", client.id);
    parent.target?.dialogManager.dismissByTag(getVoiceCallDialogTag(client.id));
    bind.cmHandleIncomingVoiceCall(id: client.id, accept: accept);
  }

  showVoiceCallDialog(Client client) {
    parent.target?.dialogManager.dismissByTag(getVoiceCallDialogTag(client.id));
    showAutoAcceptVoiceCallDialog(client);
  }

  showAutoAcceptVoiceCallDialog(Client client) {
    Timer? countdownTimer;
    var remainingSeconds = kAndroidVoiceCallAutoAcceptSeconds;
    var submitted = false;

    bool isStillIncoming() {
      return _isIncomingZegoVoiceCallPending(client.id);
    }

    final dialogManager = parent.target?.dialogManager;
    if (dialogManager == null) {
      return;
    }

    try {
      final dialogFuture =
          dialogManager.show<dynamic>((setState, close, context) {
        void submit() {
          if (submitted) return;
          submitted = true;
          countdownTimer?.cancel();
          close();
          _submitZegoVoiceCallAccept(client);
        }

        countdownTimer ??= Timer.periodic(const Duration(seconds: 1), (timer) {
          if (!isStillIncoming()) {
            timer.cancel();
            return;
          }
          remainingSeconds -= 1;
          if (remainingSeconds <= 0) {
            timer.cancel();
            submit();
            return;
          }
          setState(() {});
        });

        return CustomAlertDialog(
          title: Text(translate('\u8bed\u97f3\u901a\u8bdd')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                  '\u662f\u5426\u63a5\u53d7\u8bed\u97f3\u901a\u8bdd\uff1f'),
              ClientInfo(client),
              Text(
                translate('android_new_voice_call_tip'),
                style: Theme.of(globalKey.currentContext!).textTheme.bodyMedium,
              ),
              const SizedBox(height: 8),
              Text(
                '$remainingSeconds \u79d2\u540e\u81ea\u52a8\u63a5\u53d7\u901a\u8bdd',
                style: Theme.of(globalKey.currentContext!).textTheme.bodySmall,
              ),
            ],
          ),
          actions: [
            dialogButton('\u63a5\u53d7', onPressed: submit),
          ],
          onSubmit: submit,
          onCancel: submit,
        );
      }, tag: getVoiceCallDialogTag(client.id));
      unawaited(dialogFuture.catchError((e) {
        debugPrint("showAutoAcceptVoiceCallDialog failed: $e");
        return null;
      }));
    } catch (e) {
      debugPrint("showAutoAcceptVoiceCallDialog failed: $e");
    }
  }

  Future<void> _acceptZegoVoiceCall(Client client) async {
    try {
      if (!_isIncomingZegoVoiceCallPending(client.id)) {
        return;
      }
      if (!await AndroidPermissionManager.check(kRecordAudio)) {
        final granted = await AndroidPermissionManager.request(kRecordAudio);
        if (!granted) {
          showToast(translate('Failed'));
          handleVoiceCall(client, false);
          return;
        }
      }
      if (!_isIncomingZegoVoiceCallPending(client.id)) {
        return;
      }
      handleVoiceCall(client, true);
    } catch (e) {
      debugPrint("_acceptZegoVoiceCall failed: $e");
      if (_isIncomingZegoVoiceCallPending(client.id)) {
        handleVoiceCall(client, false);
      }
    }
  }

  bool _hasLocalAndroidVoiceCall(int clientId) {
    _clearDisconnectedVoiceCallState();
    final hasOtherClientCall = _clients.any((client) =>
        client.id != clientId &&
        !client.disconnected &&
        (client.inVoiceCall || client.incomingVoiceCall));
    if (hasOtherClientCall) {
      return true;
    }
    final model = parent.target?.zegoVoiceCallModel;
    if (!(model?.active ?? false)) {
      return false;
    }
    final hasCurrentActiveCall = _clients.any((client) =>
        client.id == clientId && !client.disconnected && client.inVoiceCall);
    if (!hasCurrentActiveCall) {
      // A previous controller may have disconnected before the final close event
      // reached Flutter. Clear the local ZEGO model so the next controller can call.
      unawaited(model?.leave() ?? Future.value());
      return false;
    }
    return true;
  }

  showClientDialog(Client client, String title, String contentTitle,
      String content, VoidCallback onCancel, VoidCallback onSubmit,
      {String? tag, bool? showSubmit, bool closeAsCancel = false}) {
    parent.target?.dialogManager.show((setState, close, context) {
      cancel() {
        onCancel();
        close();
      }

      submit() {
        onSubmit();
        close();
      }

      return CustomAlertDialog(
        title:
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text(translate(title)),
          IconButton(
              onPressed: closeAsCancel ? cancel : close,
              icon: const Icon(Icons.close))
        ]),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(translate(contentTitle)),
            ClientInfo(client),
            Text(
              translate(content),
              style: Theme.of(globalKey.currentContext!).textTheme.bodyMedium,
            ),
          ],
        ),
        actions: [
          dialogButton("Dismiss", onPressed: cancel, isOutline: true),
          if (showSubmit ?? (approveMode != 'password'))
            dialogButton("Accept", onPressed: submit),
        ],
        onSubmit: submit,
        onCancel: cancel,
      );
    }, tag: tag ?? getLoginDialogTag(client.id));
  }

  scrollToBottom() {
    if (isDesktop) return;
    Future.delayed(Duration(milliseconds: 200), () {
      controller.animateTo(controller.position.maxScrollExtent,
          duration: Duration(milliseconds: 200),
          curve: Curves.fastLinearToSlowEaseIn);
    });
  }

  void sendLoginResponse(Client client, bool res) async {
    if (res) {
      bind.cmLoginRes(connId: client.id, res: res);
      parent.target?.invokeMethod("cancel_notification", client.id);
      client.authorized = true;
      notifyListeners();
    } else {
      bind.cmLoginRes(connId: client.id, res: res);
      parent.target?.invokeMethod("cancel_notification", client.id);
      final index = _clients.indexOf(client);
      tabController.remove(index);
      _clients.remove(client);
      if (isAndroid) androidUpdatekeepScreenOn();
    }
  }

  void onClientRemove(Map<String, dynamic> evt) {
    try {
      final id = int.parse(evt['id'] as String);
      final close = (evt['close'] as String) == 'true';
      _cancelVoiceCallAutoAcceptTimer(id);
      if (_clients.any((c) => c.id == id)) {
        final index = _clients.indexWhere((client) => client.id == id);
        if (index >= 0) {
          final wasVoiceCall =
              _clients[index].inVoiceCall || _clients[index].incomingVoiceCall;
          if (wasVoiceCall) {
            _clients[index].inVoiceCall = false;
            _clients[index].incomingVoiceCall = false;
            unawaited(
                parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
          }
          if (close) {
            _clients.removeAt(index);
            tabController.remove(index);
          } else {
            _clients[index].disconnected = true;
          }
        }
        parent.target?.dialogManager.dismissByTag(getLoginDialogTag(id));
        parent.target?.dialogManager.dismissByTag(getVoiceCallDialogTag(id));
        parent.target?.invokeMethod("cancel_notification", id);
      }
      if (desktopType == DesktopType.cm && _clients.isEmpty) {
        hideCmWindow();
      }
      if (isAndroid) androidUpdatekeepScreenOn();
      notifyListeners();
    } catch (e) {
      debugPrint("onClientRemove failed,error:$e");
    }
  }

  Future<void> closeAll() async {
    _cancelAllVoiceCallAutoAcceptTimers();
    unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
    await Future.wait(
        _clients.map((client) => bind.cmCloseConnection(connId: client.id)));
    _clients.clear();
    tabController.state.value.tabs.clear();
    if (isAndroid) androidUpdatekeepScreenOn();
  }

  void jumpTo(int id) {
    final index = _clients.indexWhere((client) => client.id == id);
    tabController.jumpTo(index);
  }

  void setShowElevation(bool show) {
    if (_showElevation != show) {
      _showElevation = show;
      notifyListeners();
    }
  }

  void updateVoiceCallState(Map<String, dynamic> evt) {
    final queuedEvt = Map<String, dynamic>.from(evt);
    _voiceCallStateUpdateQueue = _voiceCallStateUpdateQueue
        .catchError((_) {})
        .then((_) => _updateVoiceCallState(queuedEvt))
        .catchError((e) {
      debugPrint("updateVoiceCallState queue failed: $e");
    });
    unawaited(_voiceCallStateUpdateQueue);
  }

  Future<void> _updateVoiceCallState(Map<String, dynamic> evt) async {
    try {
      final nativeVoiceState = await _loadNativeVoiceCallState();
      if (nativeVoiceState != null) {
        _clearStaleVoiceCallState(nativeVoiceState);
      }
      _clearDisconnectedVoiceCallState();
      final client = Client.fromJson(jsonDecode(evt["client"]));
      final nativeHasClient =
          nativeVoiceState == null || nativeVoiceState.containsKey(client.id);
      if (!nativeHasClient) {
        _cancelVoiceCallAutoAcceptTimer(client.id);
        parent.target?.dialogManager
            .dismissByTag(getVoiceCallDialogTag(client.id));
        parent.target?.invokeMethod("cancel_notification", client.id);
        return;
      }
      var index = _clients.indexWhere((element) => element.id == client.id);
      if (index == -1) {
        _clients.add(client);
        _addTab(client);
        index = _clients.length - 1;
      }

      if (client.incomingVoiceCall && _hasLocalAndroidVoiceCall(client.id)) {
        _clients[index].inVoiceCall = false;
        _clients[index].incomingVoiceCall = false;
        showToast('\u8bed\u97f3\u901a\u8bdd\u5fd9');
        handleVoiceCall(_clients[index], false);
        notifyListeners();
        return;
      }

      final wasVoiceCall =
          _clients[index].inVoiceCall || _clients[index].incomingVoiceCall;
      _clients[index].inVoiceCall = client.inVoiceCall;
      _clients[index].incomingVoiceCall = client.incomingVoiceCall;
      if (wasVoiceCall && !client.inVoiceCall && !client.incomingVoiceCall) {
        unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
      }
      if (client.incomingVoiceCall) {
        if (isAndroid) {
          _startVoiceCallAutoAcceptTimer(_clients[index]);
          showVoiceCallDialog(_clients[index]);
        } else {
          // Has incoming phone call, let's set the window on top.
          Future.delayed(Duration.zero, () {
            windowOnTop(null);
          });
        }
      } else {
        _cancelVoiceCallAutoAcceptTimer(client.id);
        parent.target?.dialogManager
            .dismissByTag(getVoiceCallDialogTag(client.id));
      }
      notifyListeners();
    } catch (e) {
      debugPrint("updateVoiceCallState failed: $e");
    }
  }

  void closeVoiceCallAfterZegoFailure() {
    var changed = false;
    unawaited(parent.target?.zegoVoiceCallModel.leave() ?? Future.value());
    for (final client in _clients) {
      if (client.inVoiceCall || client.incomingVoiceCall) {
        bind.cmCloseVoiceCall(id: client.id);
        _cancelVoiceCallAutoAcceptTimer(client.id);
        client.inVoiceCall = false;
        client.incomingVoiceCall = false;
        parent.target?.dialogManager
            .dismissByTag(getVoiceCallDialogTag(client.id));
        parent.target?.invokeMethod("cancel_notification", client.id);
        changed = true;
      }
    }
    if (changed) {
      notifyListeners();
    }
  }

  void androidUpdatekeepScreenOn() async {
    if (!isAndroid) return;
    bind.mainSetLocalOption(key: kOptionKeepScreenOn, value: 'service-on');
    var floatingWindowDisabled =
        bind.mainGetLocalOption(key: kOptionDisableFloatingWindow) == "Y" ||
            !await AndroidPermissionManager.check(kSystemAlertWindow);
    final keepScreenOn = floatingWindowDisabled
        ? KeepScreenOn.never
        : optionToKeepScreenOn(
            bind.mainGetLocalOption(key: kOptionKeepScreenOn));
    final on = ((keepScreenOn == KeepScreenOn.serviceOn) && _isStart) ||
        (keepScreenOn == KeepScreenOn.duringControlled &&
            _clients.any((e) => !e.disconnected));
    if (on != await WakelockPlus.enabled) {
      if (on) {
        WakelockPlus.enable();
      } else {
        WakelockPlus.disable();
      }
    }
  }
}

enum ClientType {
  remote,
  file,
  camera,
  portForward,
  terminal,
}

class Client {
  int id = 0; // client connections inner count id
  bool authorized = false;
  bool isFileTransfer = false;
  bool isViewCamera = false;
  bool isTerminal = false;
  String portForward = "";
  String name = "";
  String peerId = ""; // peer user's id,show at app
  bool keyboard = false;
  bool clipboard = false;
  bool audio = false;
  bool file = false;
  bool restart = false;
  bool recording = false;
  bool blockInput = false;
  bool disconnected = false;
  bool fromSwitch = false;
  bool inVoiceCall = false;
  bool incomingVoiceCall = false;

  RxInt unreadChatMessageCount = 0.obs;

  Client(this.id, this.authorized, this.isFileTransfer, this.isViewCamera,
      this.name, this.peerId, this.keyboard, this.clipboard, this.audio);

  Client.fromJson(Map<String, dynamic> json) {
    id = json['id'];
    authorized = json['authorized'];
    isFileTransfer = json['is_file_transfer'];
    // TODO: no entry then default.
    isViewCamera = json['is_view_camera'];
    isTerminal = json['is_terminal'] ?? false;
    portForward = json['port_forward'];
    name = json['name'];
    peerId = json['peer_id'];
    keyboard = json['keyboard'];
    clipboard = json['clipboard'];
    audio = json['audio'];
    file = json['file'];
    restart = json['restart'];
    recording = json['recording'];
    blockInput = json['block_input'];
    disconnected = json['disconnected'];
    fromSwitch = json['from_switch'];
    inVoiceCall = json['in_voice_call'];
    incomingVoiceCall = json['incoming_voice_call'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['id'] = id;
    data['authorized'] = authorized;
    data['is_file_transfer'] = isFileTransfer;
    data['is_view_camera'] = isViewCamera;
    data['is_terminal'] = isTerminal;
    data['port_forward'] = portForward;
    data['name'] = name;
    data['peer_id'] = peerId;
    data['keyboard'] = keyboard;
    data['clipboard'] = clipboard;
    data['audio'] = audio;
    data['file'] = file;
    data['restart'] = restart;
    data['recording'] = recording;
    data['block_input'] = blockInput;
    data['disconnected'] = disconnected;
    data['from_switch'] = fromSwitch;
    data['in_voice_call'] = inVoiceCall;
    data['incoming_voice_call'] = incomingVoiceCall;
    return data;
  }

  ClientType type_() {
    if (isFileTransfer) {
      return ClientType.file;
    } else if (isViewCamera) {
      return ClientType.camera;
    } else if (isTerminal) {
      return ClientType.terminal;
    } else if (portForward.isNotEmpty) {
      return ClientType.portForward;
    } else {
      return ClientType.remote;
    }
  }
}

String getLoginDialogTag(int id) {
  return kLoginDialogTag + id.toString();
}

String getVoiceCallDialogTag(int id) {
  return kVoiceCallDialogTag + id.toString();
}

showInputWarnAlert(FFI ffi) {
  ffi.dialogManager.show((setState, close, context) {
    submit() {
      AndroidPermissionManager.startAction(kActionAccessibilitySettings);
      close();
    }

    return CustomAlertDialog(
      title: Text(translate("How to get Android input permission?")),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(translate("android_input_permission_tip1")),
          const SizedBox(height: 10),
          Text(translate("android_input_permission_tip2")),
        ],
      ),
      actions: [
        dialogButton("Cancel", onPressed: close, isOutline: true),
        dialogButton("Open System Setting", onPressed: submit),
      ],
      onSubmit: submit,
      onCancel: close,
    );
  });
}

Future<void> showClientsMayNotBeChangedAlert(FFI? ffi) async {
  await ffi?.dialogManager.show((setState, close, context) {
    return CustomAlertDialog(
      title: Text(translate("Permissions")),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(translate("android_permission_may_not_change_tip")),
        ],
      ),
      actions: [
        dialogButton("OK", onPressed: close),
      ],
      onSubmit: close,
      onCancel: close,
    );
  });
}
