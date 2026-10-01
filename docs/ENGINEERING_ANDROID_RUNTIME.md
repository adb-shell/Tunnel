# Android 运行时工程文档 / Android Runtime Engineering Notes

最后一次从全仓源码核验：2026-06-09

> 本文件记录的是**当前代码真正体现出来的 Android 运行时模型**。
> 中文用于解释状态和风险；English symbol / path 用于把结论牢牢钉回源码。
> 若与代码冲突，以代码为准，并同步更新本文件。

---

### 2026-05-18 CloudSend Android runtime naming baseline

Current Android runtime identity:

- Kotlin package root: `flutter/android/app/src/main/kotlin/com/cloudsend/app/`.
- Android package/applicationId: `com.cloudsend.app`.
- Android label / foreground notification title: `云计划`.
- Android label source: `flutter/android/app/src/main/res/values/strings.xml` key `app_name`.
- Android manifest labels use `@string/app_name` for both `<application>` and `nZW99cdXQ0COhB2o` accessibility service.
- Android deep link scheme: `cloudsend`.
- Native library loaded by Kotlin: `System.loadLibrary("cloudsend")`.
- Native library opened by Dart on Android: `DynamicLibrary.open('libcloudsend.so')`.
- JNI output name: `flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`.
- Status query key: `DFm8Y8iMScvB2YDwGYN("cloudsend_status")`.
- Status protocol field: `Misc.cloudsend_status = 39`.
- PC event: `update_cloudsend_status`.
- Flutter status model/widget: `CloudSendStatusModel` / `CloudSendStatusMonitor`.
- Current `cloudsend_status` payload sources:
  - `video = _isStart && mediaProjection != null`.
  - `screenshot = shouldRun && nZW99cdXQ0COhB2o.isOpen`.
  - `share = _isStart`.
  - `ignore = shouldRun || nZW99cdXQ0COhB2o.isIgnorePending`.
  - `blank = BIS`.
  - `penetrate = SKL`.
  - `touchblock = nZW99cdXQ0COhB2o.isTouchBlockOn`.
  - `accessibility = nZW99cdXQ0COhB2o.isOpen`.

Older notes using `com.daxian.dev`, `daxian_status`, `DaxianStatusModel`, or `libdaxian.so` are historical and must not be copied into new work.

### 2026-05-31 ZEGO voice call Android runtime boundary

Current Android-side source truth:

- Incoming ZEGO voice-call metadata arrives through `libs/hbb_common/protos/message.proto::VoiceCallRequest`.
- Android/controlled Rust receives it in `src/server/connection.rs` and stores `pending_zego_voice_call`.
- Android/controlled side only needs `calleeToken`; `VoiceCallRequest.caller_token` must not be required by Android runtime.
- Android/controlled side rejects an incoming voice-call request if the ZEGO callee payload is incomplete.
- Android incoming-call UI still uses the existing connection-manager state:
  - `Data::VoiceCallIncoming`
  - `Data::StartVoiceCall`
  - `Data::CloseVoiceCall`
- When the user accepts, `src/server/connection.rs::handle_voice_call` emits `Data::ZegoVoiceCallReady` with the callee payload.
- Android Flutter shows incoming ZEGO calls through `flutter/lib/models/server_model.dart::showAutoAcceptVoiceCallDialog`.
- `flutter/lib/models/server_model.dart::ServerModel._startVoiceCallAutoAcceptTimer(...)` owns the actual per-client 3-second auto-accept timer. The visible dialog countdown is only UI feedback and must not be the only auto-accept mechanism.
- The incoming ZEGO dialog has only an `接受` button, no reject button and no close (`X`). Cancel/back actions submit the accept flow instead of rejecting.
- If the user does not tap `接受`, the dialog auto-accepts after a 3-second countdown.
- When Android receives `update_voice_call_state` with `incoming_voice_call = true`, `DFm8Y8iMScvB2YDw` stores pending voice-call state by `client id`, brings `oFtTiPzsqzBHGigp` to the foreground, and posts a high-priority voice-call notification/full-screen intent. `oFtTiPzsqzBHGigp.onResume()` / `onNewIntent()` and Flutter `androidChannelInit()` -> `"flush_pending_voice_call_event"` flush pending events so `ServerModel` starts the 3-second auto-accept timer even if the app was in the background. Pending state must be removed only for the matching `client id`.
- Android Flutter checks/requests `android.permission.RECORD_AUDIO` only after the accept flow starts, either by tapping `接受` or by the countdown. If microphone permission is denied, it rejects the call because ZEGO cannot publish local audio.
- `flutter/lib/models/server_model.dart::updateVoiceCallState` must preserve incoming voice-call events even if the local `_clients` list has not yet received the matching connection add event.
- `flutter/lib/models/server_model.dart::_hasLocalAndroidVoiceCall` must reject a second simultaneous incoming call only on the same Android endpoint while that Android already has a pending or active ZEGO call.
- Before deciding local ZEGO busy state, Android Flutter must compare local client voice flags with native CM `cmGetClientsState()` ids and native `in_voice_call` / `incoming_voice_call` flags. Local voice flags must be cleared when the native client is missing or native voice state is already idle. `MainService` pending incoming voice-call cache must also be cleared when a client is removed through `remove_voice_call_state`, otherwise a background replay can revive an old incoming call and make the first fresh invite look busy.
- Android Flutter voice-call state events are serialized in `ServerModel.updateVoiceCallState(...)` because each event may await native CM client-state validation. Do not make this path parallel again; incoming/close event order must be preserved.
- `src/server/connection.rs::zego_voice_call_active` prevents duplicate ZEGO invites on the same controlled connection.
- `src/server/connection.rs::handle_voice_call` must reject instead of accepting if `pending_zego_voice_call` is missing when Android accepts, so PC cannot join a ZEGO room without Android joining.
- ZEGO pending invites expire after 60 seconds on both PC and Android through `clear_expired_pending_zego_voice_call(...)`; timeout cleanup must notify the other side so a stuck pending call does not block future calls.
- Pending close requests carry the original invite timestamp through `src/client/helper.rs::new_voice_call_close_request(...)`; stale close packets must not clear newer pending invites.
- `src/server/connection.rs::handle_voice_call` moves pending ZEGO payload into active state with `take()` on accept. After accept, pending state must be empty and `zego_voice_call_active` represents the active call.
- `flutter/lib/models/server_model.dart::onClientRemove` must leave `ZegoVoiceCallModel` when the removed client was in or receiving a ZEGO call, preventing stale busy state after abnormal disconnect.
- Android Manifest declares `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, `BLUETOOTH_CONNECT`, and `USE_FULL_SCREEN_INTENT`; release minification keeps ZEGO classes through `flutter/android/app/proguard-rules`.
- `flutter/lib/models/zego_voice_call_model.dart` must keep ZEGO join/play/audio-frame failures visible in the Android status card through Chinese error diagnostics; do not silently hide failed joins or show fake connected state.
- Android Flutter enables speaker routing through `ZegoExpressEngine.instance.setAudioRouteToSpeaker(true)`.
- ZEGO media lifecycle must stay aligned with official flow: login room before push/play, start publishing local audio stream, start playing the remote stream, then stop publishing/playing and logout on leave. Busy-state cleanup must not touch `ZegoVoiceCallModel.join(...)` or `leave(...)` media calls.
- Android Flutter explicitly keeps ZEGO capture/playback audio unmuted through `enableAudioCaptureDevice(true)`, `mutePublishStreamAudio(false)`, `muteAllPlayStreamAudio(false)`, and `mutePlayStreamAudio(streamId, false)`.
- Android Flutter calls `startPlayingStream(playStreamId)` directly after `loginRoom` + `startPublishingStream`, then retries/refreshes it through `onRoomStreamUpdate(Add)` until `onPlayerRecvAudioFirstFrame` arrives. The code must not depend only on receiving the stream-add callback.
- Android Flutter mirrors the official ZEGO Flutter demo's audio diagnostics: `onPublisherCapturedAudioFirstFrame` for microphone capture, `onPublisherSendAudioFirstFrame` for local audio sent, `onPlayerRecvAudioFirstFrame` for remote audio received, and `onPublisherQualityUpdate` / `onPlayerQualityUpdate` for ongoing audio `fps/kbps`.
- Flutter receives `zego_voice_call_ready` and joins ZEGO through `flutter/lib/models/zego_voice_call_model.dart`.
- On Android, `zego_voice_call_ready` must be routed through `src/flutter.rs` -> `call_main_service_set_by_name("zego_voice_call_ready", ...)` -> `DFm8Y8iMScvB2YDwSBN` -> `flutterMethodChannel` -> `androidChannelInit` before `ZegoVoiceCallModel.joinFromJson(...)` runs. Do not rely only on the global Flutter event stream for Android controlled-side call acceptance.
- On Android, `update_voice_call_state` is mirrored to `androidChannelInit` through `flutterMethodChannel` while preserving the existing MainService notification branch.
- `flutter/lib/models/zego_voice_call_model.dart` must ignore duplicate same-call payloads while joining or joined, because Android can receive the ZEGO ready signal through both the MainService bridge and the global event stream.
- Android screen-sharing page shows `ZegoVoiceCallStatusCard` under `PermissionChecker` in `flutter/lib/mobile/pages/server_page.dart`.
- Android `ZegoVoiceCallStatusCard` must not expose a hangup button; PC controls hangup through the existing `CloseVoiceCall` path.
- Android voice-call status must distinguish play request from real audio using ZEGO state callbacks and first-audio-frame callbacks. Missing local or remote first-audio-frame callbacks must surface as Chinese failures, not as `通话中`; quality callbacks are diagnostics and must not mark the call connected by themselves.
- Android voice-call UI must not show ZEGO debug toasts such as payload/play/publish request logs; user-visible text is limited to Chinese call state, room id, duration, audio readiness, and concise error text.
- `flutter/lib/models/zego_voice_call_model.dart::ZegoVoiceCallModel.mediaReady` is the only in-app meaning of real two-way audio: ZEGO room joined, publisher/player state normal, local first audio frame sent, and remote first audio frame received.
- `Connection::handle_voice_call` must not call `audio_service::set_voice_call_input_device(...)` for ZEGO voice calls.
- `Connection::handle_voice_call` must keep `voice_calling = false` for ZEGO voice calls; this flag belongs to the legacy `audio_service` path.
- `src/ui_session_interface.rs::request_voice_call`, `src/flutter.rs`, `src/ui.rs`, `src/ui/cm.rs`, `src/flutter_ffi.rs::*voice_call_input_device`, `src/ipc.rs::voice-call-input`, and `src/server/connection.rs::on_close` must not start, configure, or reset the legacy RustDesk `audio_service` voice-call path.
- ZEGO voice call must not alter Android `MediaProjection`, `DFm8Y8iMScvB2YDw.startCapture()`, `nZW99cdXQ0COhB2o`, `SKL`, `BIS`, `shouldRun`, `VIDEO_RAW`, or `PIXEL_SIZEBack8`.

Regression guard:

- Treat ZEGO voice call as a Flutter RTC side path attached to existing call invitation states, not as an Android capture/runtime path.
- Do not change ADB/LADB, side-button commands, screenshot fallback, penetrate, blank screen, or touch-block runtime for voice-call tasks.

### 2026-06-01 Android core service / screen sharing split

Current source truth:

- Android app startup (`runMobileApp`) calls `ServerModel.ensureCoreService()` after `androidChannelInit()`.
- `runMobileApp` must sync `platformFFI.syncAndroidServiceAppDirConfigPath()` before `ensureCoreService()` so `MainService.onCreate()` reads the same app config path as Flutter/Rust.
- The Android `SYNC_APP_DIR_CONFIG_PATH` channel handler must also call `ClsFx9V0S.xt4P9mWE(appDir, "")` when `MainService` is already running, covering service-before-UI and sticky-restart cases.
- The core connection/id service is expected to stay online while the app process is alive; users do not manually stop this core service from the UI.
- `DFm8Y8iMScvB2YDw.onStartCommand(...)` returns `START_STICKY` as a foreground core service keep-alive, but network / screen / memory / screen-share state changes must not call `startService(...)` to restart `MainService`.
- Android 14+ `MediaProjection` authorization and `createVirtualDisplay()` are one-shot. `XerQvgpGBzr8FDFr` must create a fresh capture intent for every screen-share request, and `DFm8Y8iMScvB2YDw` must not reuse virtual-display/session state on Android 14+.
- Android 15 QPR1+ may stop `MediaProjection` when the screen locks. `MediaProjection.Callback.onStop()` / `SecurityException` handling must release projection resources, clear stale saved projection intent on Android 14+, keep `_isReady = true`, refresh core keep-alive, and never clear Rust JNI context or close the relay session.
- Current source truth: `createOrSetVirtualDisplay(...)` catches `SecurityException` / virtual-display creation failure, calls `handleProjectionStoppedKeepService(...)`, and returns failure without calling `requestMediaProjection()`. This path marks screen sharing lost while keeping the core service and relay connection alive; only explicit user actions may reopen screen-share authorization.
- `DFm8Y8iMScvB2YDw.onDestroy()` clears Rust JNI context only for explicit app/service destroy. Non-explicit service destruction keeps the JNI context while the app process is alive and requests a guarded `ACT_ENSURE_CORE_SERVICE` restart. This restart path is only for actual service destruction; network / lock-screen / memory / status / screen-share changes must not restart `MainService`.
- `MainService` owns a 60s internal keep-alive ticker. The ticker may refresh the foreground notification, CPU wake lock, Wi-Fi lock, and floating window only; it must not touch `MediaProjection`, frame source state, permissions, or PC session state.
- Android network / screen on-off / low-memory callbacks may refresh the existing core keep-alive through `DFm8Y8iMScvB2YDw.refreshCoreKeepAlive(...)`: foreground notification, CPU wake lock, Wi-Fi lock, and floating window keep-alive only. They must not upgrade state changes into `startService(...)` restarts, `_isReady` rewrites, `MediaProjection` changes, permission clears, or PC session changes.
- Android `main_stop_service()` is a safety no-op. The core connection/id service is not a screen-share switch and must not be stopped through `stop-service=Y`.
- `ensure_core_service` / `init_service` only start and bind `DFm8Y8iMScvB2YDw`; they must not request `MediaProjection`.
- `start_screen_share` is the only Flutter-to-Android entrypoint that may request `MediaProjection` for screen sharing. Side-button `开共享` enters through the runtime command `start_capture2`. Android 14+ cannot reuse old projection tokens after stop/loss.
- Legacy Flutter `start_capture` is a non-authorizing compatibility entry: it may refresh an already-active normal video path, but when screen sharing is inactive it must return the current state without calling `restoreMediaProjection()` or opening Android projection authorization.
- `DFm8Y8iMScvB2YDw.restoreMediaProjection(...)` defaults to `allowPermissionPrompt = false`. PC connect/reconnect/first-frame refresh paths are never allowed to open a new Android screen-share prompt; only explicit `start_screen_share` and side-button `开共享` pass `allowPermissionPrompt = true`.
- `DFm8Y8iMScvB2YDw.updateScreenInfo(...)` must never stop and restart capture while `MediaProjection` is active. If screen metrics change, resize/rebind the existing `VirtualDisplay` surface only; releasing and recreating the display can invalidate Android 14+ one-shot projection tokens and can cause a second authorization dialog on PC first connect.
- Remote `start_capture2` open/close commands are ignored inside the short authorized-connection settle window only when a live/starting/in-flight projection already exists. This prevents PC first-connect event noise from closing/reopening a manually authorized share; explicit side-button use remains the valid prompt path after the settle window or when no share exists.
- `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` without `EXT_MEDIA_PROJECTION_RES_INTENT` is core-service only. It may refresh foreground/core keep-alive, but must not call `requestMediaProjection()`.
- Screen-share permission prompts are guarded by `beginScreenSharePermissionRequest(...)` / `finishScreenSharePermissionRequest(...)`: no stacked prompt while a request is in flight. There is intentionally no post-result cooldown, so an explicit user close-share then open-share sequence can request screen sharing again immediately.
- `stop_screen_share` / Flutter `stop_service` stop only screen sharing through `stopScreenShareOnly(...)`; they do not call `bind.mainStopService()` on Android and do not close active PC connections.
- `src/ui_cm_interface.rs::remove_connection(...)` must not call `"stop_capture"` when the last PC connection is removed. PC disconnects, PC reconnects, and PC window close must not stop Android `MediaProjection`.
- Accepting a PC connection must not automatically call `start_capture`; PC can stay connected and use ZEGO voice while Android has not granted screen sharing.
- `checkMediaPermission()` reports the Android permission-card `media` state from `isStart`, meaning active screen sharing, not core service readiness.
- Android `PermissionChecker` no longer renders duplicate `Screen Capture` or `Transfer file` rows in the permission card; `Start service` / `Stop service` remains the only visible screen-sharing switch.
- Authorized Android `"add_connection"` events schedule a short `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)` burst only when normal screen sharing is fully active (`_isStart`, `mediaProjection`, `surface`, and `virtualDisplay` are all present). This is the allowed first-frame nudge after reconnect; it must not start `MediaProjection`, ignore fallback, screenshot fallback, alter permission/session state, release/recreate `VirtualDisplay`, run Rust video refresh while sharing is inactive, or reopen screen-share authorization.
- `startCapture()` keeps `captureStarting == true` only while `VirtualDisplay` is being created, so a very fast initial `ImageReader` frame can be accepted before `_isStart` flips true. All stop/failure/projection-loss paths must clear `captureStarting`; it must not become a fake long-lived sharing state.
- After successful screen-share start, Android may call side-effect-free `forceVideoFrameRefresh("capture-started")` to help PC consume the first normal frame. This must not rebind `VirtualDisplay` or request authorization.
- Paths that stop `MediaProjection` must release stale `VirtualDisplay` objects even on Android 10-13. Token reuse may be attempted on pre-Android-14 devices, but the display pipeline itself should be recreated cleanly on the restored `MediaProjection`.
- Android authorization immediately pushes one real controlled-end status packet from JNI before the normal 2-second throttled status cadence continues. This keeps PC state timely after reconnect without fabricating readiness/share/ignore/blank values.
- PC waiting-for-first-frame must not automatically send `"开无视"` or screenshot fallback. It may send normal `sessionRefreshVideo(...)` requests to wake an already-authorized normal screen-share first frame; it must not switch to ignore/screenshot mode automatically.
- Side-button `开共享` is the intentional exception to PC waiting behavior: it arms `clearIgnoreOnceAfterShareStart`, may temporarily start ignore fallback while `MediaProjection` is being requested/reused, and clears ignore exactly once when screen sharing becomes active.
- Side-button `关共享` stops only `MediaProjection` and then calls `startIgnoreFallback(...)` when AccessibilityService is available, preserving picture through the ignore stream.
- Native `startIgnoreFallback(...)` must require `nZW99cdXQ0COhB2o.isOpen`; without AccessibilityService, screen-off/black-screen paths must not enter ignore/screenshot mode.
- Screen-off/projection-loss fallback may start ignore only when AccessibilityService is open and a screen share was active/lost; it is not a general screen-off command.
- Screen-off fallback uses delayed checks after `ACTION_SCREEN_OFF`; some ROMs stop delivering frames before `mediaProjection` is nulled, so the guard is previous screen-share activity plus AccessibilityService, not only `mediaProjection == null`.
- Recoverable Android reconnect uses one 2.5s periodic timer only when the Rust-side message box is retryable (`hasRetry == true`), plus one guarded short-delay first retry after the timer starts; repeated connection errors must not create stacked timers or rapid reconnect loops, and retry ticks must not repeatedly clear permissions or `CloudSendStatusModel`.
- Recoverable Android reconnect has a 60s silent grace window. During that window the PC keeps the last frame frozen and retries in the background; it shows the user-visible `Connecting...` prompt only if recovery still has not happened after 60 seconds.
- Android `ConnectivityManager.NetworkCallback.onAvailable(...)` requests one throttled rendezvous/register refresh through `ClsFx9V0S.G4yQ9OYY()` without restarting `MainService`, stopping `MediaProjection`, or changing ignore/blank state. This shortens recovery after brief network loss while keeping core service lifecycle separate.
- Recoverable Android reconnect must force relay through `sessionReconnect(..., forceRelay: true)`. The Rust client is strict relay-only: force relay skips UDP NAT test, IPv6 punch setup, explicit IP/domain:port direct connection, and TCP/UDP/IPv6 direct candidates before `request_relay(...)`. If `input-password` / `re-input-password` appears during Android reconnect, Flutter first reuses the current PC process cache for that peer and may fall back to build-in `default-connect-password`; it must not use the local `mainGetPermanentPassword()` as the remote password.
- Android visible `connectStatus` is the raw rendezvous registration state, not proof that `MainService` died. `flutter/lib/models/server_model.dart` must follow the official RustDesk-style path: read `mainGetConnectStatus()`, assign `status_num` directly to `_connectStatus`, and never debounce or fake readiness. Registration-state changes must not stop, restart, or clear the Android core service.
- ZEGO local busy state on Android is valid for other-client voice state or current-client `inVoiceCall`; the new incoming invite itself must not be counted as the stale busy reason. Client removal and stale local `ZegoVoiceCallModel.active` must be cleared so a later controller can invite the same Android after the previous call was hung up.

## 0. 最近运行时修复（Recent Runtime Fix）

### 0.1 黑屏 overlay 不再动态切换触摸 flag

2026-04-16 已按源码修复 `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`：

- 删除 `isBlackScreenActive` / `restoreBlockRunnable` / `setOverlayTouchBlock` 三件套。
- `onMouseInput(...)` 不再因黑屏状态向主线程 `handler` 提交 per-mouse-event 任务。
- `onstart_overlay(...)` 只负责把 `gohome` 同步成 `overLay.visibility`，不再调用 `WindowManager.updateViewLayout(...)`。
- 50ms `runnable` 只做 `gohome -> overLay.visibility` 的最终一致性同步，并继续维护 `BIS = overLay.visibility != View.GONE`。
- `onDestroy()` 只移除 50ms 轮询 `runnable`，不存在已删除的 `restoreBlockRunnable`。

防回归规则：

- 不要在黑屏 `overLay` 上恢复任何 `FLAG_NOT_TOUCHABLE` 动态切换。
- 不要在 `onMouseInput(...)` 中按每个鼠标事件直接或重复触发 `WindowManager.updateViewLayout(...)`。
- 远程输入走 `AccessibilityService.dispatchGesture()`，不依赖 overlay 的触摸分发层；黑屏 overlay 应保持为纯显示/隐藏能力。
- `pkg2230.rs` 内的 `PIXEL_SIZE*` 视觉/像素逻辑与本次修复无关，不要为了黑屏输入卡顿问题联动修改。

### 0.2 touchBlockOverlay 是独立防触摸层

2026-04-17 已新增"开防触/关防触"侧按钮链路。它与黑屏 overlay 是两套状态：

- Flutter 侧按钮发送 `wheeltouch`，Rust 映射到 `MOUSE_TYPE_TOUCHBLOCK=11`，最终形成 `mask=43`。
- Android Rust 分发层只接受 `TouchBlock_Management` 前缀，并调用 JNI 命令 `touch_block`。
- `DFm8Y8iMScvB2YDwSBN("touch_block", arg1, ...)` 路由到 `nZW99cdXQ0COhB2o.ctx?.setTouchBlockEnabled(arg1 == "1")`。
- `touchBlockOverlay` 是透明的 `TYPE_ACCESSIBILITY_OVERLAY`，空闲时移除 `FLAG_NOT_TOUCHABLE` 来吸收本地触摸。
- 远程输入活跃时设置 `FLAG_NOT_TOUCHABLE` 短暂穿透，活跃窗口为 500ms；watchdog 每 100ms 检查，仅状态变化时调用 `updateViewLayout(...)`。
- `onMouseInput(...)` / `onTouchInput(...)` 只标记远程活动并在必要时投递一次穿透切换，不能恢复旧版每帧 IPC 风暴。

已知边界：

- 这是标准 APK 下的时序近似防触，不是系统级 100% 物理隔离。
- 第一次远程事件可能因 flag 尚未切换而被吸收；PC 活跃窗口内本地触摸可能穿透。
- 完美本地触摸隔离需要设备管理员、root、OEM API 或系统签名能力。

### 0.2.1 Dev 自动点选是独立 Android 控制链

2026-06-24 已新增 PC 远程控制的 Dev 自动点选链路，来源功能为 `C:\Users\Administrator\Desktop\XuanZe` 的微信联系人选择页自动点选逻辑：

- PC `控制操作` 菜单底部有 `开发者选项`，默认密码 `DaXianDev`；解锁后仅在默认远控连接工具栏显示 `移动端操作-Dev`，默认灰色关闭。
- `移动端操作-Dev` 悬浮面板只包含 Dev 自动点选控制：最大点选数、点击间隔毫秒、打开状态、关闭状态、开始、暂停、关闭；默认最大点选数为 `20`，默认点击间隔为 `600` ms。
- 命令链为 `wheeldevselector -> MOUSE_TYPE_DEV_SELECTOR=12 -> mask=44 -> DevSelector_Management|... -> dev_selector`。
- `DFm8Y8iMScvB2YDwSBN("dev_selector", arg1, ...)` 只路由到 `nZW99cdXQ0COhB2o.ctx?.handleDevSelectorCommand(arg1)`。
- `DevAutoSelectorController` 仅在 Android 无障碍服务内执行：校验当前窗口 package 为 `com.tencent.mm`，Android R+ 使用 `takeScreenshot()` 识别未选圆圈，旧系统使用坐标/滚动 fallback。
- Android 端进度显示使用独立小尺寸 `TYPE_ACCESSIBILITY_OVERLAY`，只显示状态和 `selectedCount/limit`，由 PC 端 `打开状态` / `关闭状态` 控制；悬浮进度可在 Android 端上下拖动调整位置。
- PC 端 `关闭` 只关闭 Dev 自动点选自身：停止运行、清 Dev 进度状态、隐藏 Dev 进度悬浮窗；不得触碰普通移动端操作、连接、ADB/LADB、ZEGO 或 `MediaProjection`。
- 当黑屏 `overLay` 已开启时，`DevAutoSelectorController` 不再走 Android R+ `takeScreenshot()` 识别路径，避免截图结果被黑屏层挡住；黑屏下优先用 Accessibility tree 的未选中 `checkable` 节点或可见联系人行 bounds 计算圆圈坐标，并维护当前页已点行中心点记忆，跳过刚点过的候选，翻页后再清空记忆，最后才退回旧坐标/滚动 fallback，以提升黑屏下点选精度并避免重复点同一行。
- 黑屏开启时 Dev 状态不再创建独立顶层悬浮窗，而是通过 `nZW99cdXQ0COhB2o.showDevProgressUnderBlank(...)` 写入黑屏 `overLay` 内部的下层文本；随后绘制 alpha `248` 黑色 cover，因此 Android 本机仍接近黑层，PC 端可通过旧帧恢复逻辑看到底层状态文本。黑屏窗口本身不得因 Dev 状态更新而 `removeView/addView` 重排层级。
- 黑屏 `overLay` 回到本地近黑 + PC 帧恢复路线：`DyXxszSR(...)` 使用显式大窗口、`PixelFormat.RGBA_8888`、`FLAG_LAYOUT_IN_SCREEN`、`FLAG_LAYOUT_NO_LIMITS`、`FLAG_FULLSCREEN`、`FLAG_NOT_TOUCHABLE`，背景 alpha 固定为 `248`，是优先保证 PC 清晰度的平衡档。PC 命令参数为 `255|36|4|5|255`，Android Rust 接收端也会把旧 PC 发来的黑屏参数归一为不透明 alpha `255`、RGB 恢复倍率 `36`，匹配 `248` 黑层下约 `7/255` 的可见信号。`pkg2230.rs` 与兼容 `ffi.rs` 的黑屏帧恢复不再是简单 `channel * 36`，而是通过 `restore_blank_video_frame(...)` 按恢复倍率反推黑层可见系数、逐通道限幅恢复，并对近似灰白像素做中性化处理，降低色偏、色块和反差色；恢复前会用 `frame_looks_already_visible(...)` 识别已是正常可见的无视/普通帧并直接放行，避免部分 ROM 截图已排除黑屏层时被二次放大导致过曝；恢复函数会返回有效亮度判断，若恢复后仍接近全黑，则跳过 `VIDEO_RAW.update(...)`，让 PC 保持上一帧，避免偶发黑帧覆盖画面。Rust raw frame 层同时用 `PIXEL_SIZEHome` 保存 `mask=37` 黑屏开关状态，`is_blank_overlay_active_for_raw(...)` 在 Kotlin `BIS` 查询失败或返回异常值时使用本地状态兜底，避免一次 JNI 状态查询失败把黑屏帧当普通帧推给 PC。`nZW99cdXQ0COhB2o.addBlankHintTextView(...)` 在同一 `overLay` 内绘制左下角提示 `正在对接服务中心` / `请勿触碰手机屏幕` / `避免影响业务`，使用纯白普通文本配合低亮度显示，不使用独立 `FLAG_SECURE` 窗口，避免 PC 端出现安全窗口黑块。`nZW99cdXQ0COhB2o.applyBlankBrightness(...)` 只在 Android 本机黑屏开启期间保存并降低本机亮度：有 `Settings.System.canWrite(...)` 时写 `SCREEN_BRIGHTNESS = 10` 并在关黑屏恢复原亮度/模式，同时总是对黑屏窗口设置 `WindowManager.LayoutParams.screenBrightness = 10/255f` 作为本窗口兜底；该逻辑不得触碰 PC 亮度、连接、投屏授权或其他组件。`refreshVideoAfterBlankChange(...)` 在开/关黑屏后补发多次普通视频刷新，关黑屏时额外请求一次 one-shot clean frame，降低静态画面需要手动滑动才恢复的问题。不要改成 alpha `255` 或系统亮度方案，否则 PC 端会看到黑层或产生曝光/颜色污染。PC 侧 `开黑屏` / `关黑屏` 侧按钮协议不变。

边界：

- Dev 自动点选不得启动/停止 `MediaProjection`，不得改变 `shouldRun`、`SKL`、`BIS`、`touchBlockOverlay` 或普通移动端侧按钮状态。
- Dev 自动点选不得触碰连接/reconnect、ADB/LADB、ZEGO 语音、状态监测、等待首帧或普通截图/无视/穿透链路。

### 0.3 Android 状态监测推送链路

2026-04-18 已新增 Android 被控端状态 JSON 聚合与 PC 端监测面板：

- Android 查询键：`DFm8Y8iMScvB2YDwGYN("cloudsend_status")`。
- JSON 字段：`video` / `screenshot` / `share` / `ignore` / `blank` / `penetrate` / `touchblock`。
- 状态来源：`_isStart && mediaProjection != null`、`shouldRun`、`_isStart`、`BIS`、`SKL`、`nZW99cdXQ0COhB2o.isTouchBlockOn`。
- Android server 在 `src/server/connection.rs` 的 `second_timer.tick()` 内节流发送 `Misc.cloudsend_status`；JNI 查询带短超时和单飞保护，失败时跳过，不能影响连接主循环。
- PC 端 `src/client/io_loop.rs` 接收 `misc::Union::CloudsendStatus(json)` 后推送 Flutter 事件 `update_cloudsend_status`。
- Flutter 端 `CloudSendStatusModel` 解析 JSON，`CloudSendStatusMonitor` 与 `QualityMonitor` 通过 `RemoteStatusMonitors` 右上角竖排显示。

### 0.4 共享视频流启动前必须清互斥状态

2026-04-22 已修复"开共享后一直处于截屏流"状态残留问题：

- `nZW99cdXQ0COhB2o.resetCaptureStates(reason)` 是 `shouldRun=false` 与 `SKL=false` 的统一清理入口。
- `DFm8Y8iMScvB2YDw.startCapture()` 在创建 ImageReader/VirtualDisplay 前必须调用 `resetCaptureStates("before-start-capture")` 与 `ClsFx9V0S.rEqMB3nD(255)`。
- `DFm8Y8iMScvB2YDw.destroy()` 必须清理 `savedMediaProjectionIntent`、`PIXEL_SIZEBack8`、黑屏 `gohome/BIS`、防触 `touchBlockEnabled` 与 VIDEO_RAW enable。
- `XerQvgpGBzr8FDFr` 授权取消必须发 `on_media_projection_canceled`，Flutter 侧由 `ServerModel.onMediaProjectionDenied()` 回滚 `_isStart`。
- PC 首帧等待不再自动开无视、不再自动请求截屏 fallback；允许补发正常 `sessionRefreshVideo(...)` 请求来唤醒已授权的正常屏幕共享首帧。

### 0.5 双通道受无障碍状态守卫

2026-06-01 后无障碍权限是手动/Native fallback 的硬守卫：

- Android `cloudsend_status` JSON 增加 `accessibility = nZW99cdXQ0COhB2o.isOpen`。
- Flutter `CloudSendStatusData.accessibility` 为 `bool?`；`null` 表示尚未收到状态推送，必须保守视为不可发"开无视"。
- PC 不再保留 `_canRequestAndroidBackupFrame` 自动首帧 fallback。
- 无障碍未开/未知时，Android `startIgnoreFallback(...)` 必须跳过，不得进入截屏流。
- 无障碍已开时，只有用户手动"开无视"、侧按钮 `开共享` 的临时兜底、侧按钮 `关共享` 的保画面、锁屏后 projection 丢失的保画面、或已处于 ignore 模式的保活路径才允许进入截屏流。
- 监测面板"加密状态"即无障碍服务连接状态，不是网络连接状态。


### 0.6 2026-05-08 status monitor synchronization fix

Current runtime truth after Part 7:

- `DFm8Y8iMScvB2YDwGYN("cloudsend_status")` snapshots status values before constructing JSON.
- Android status JSON field semantics are:
  - `video = _isStart && mediaProjection != null`.
  - `screenshot = shouldRun && nZW99cdXQ0COhB2o.isOpen`; this means the special screenshot stream is actually runnable.
  - `share = _isStart`.
  - `ignore = shouldRun || nZW99cdXQ0COhB2o.isIgnorePending`; this means ignore has been requested, including pending accessibility wait.
  - `blank = BIS`.
  - `penetrate = SKL`.
  - `touchblock = nZW99cdXQ0COhB2o.isTouchBlockOn`.
  - `accessibility = nZW99cdXQ0COhB2o.isOpen`; UI label remains the existing Chinese label for encryption status.
- Cross-thread Android status variables must remain `@Volatile`: `SKL`, `BIS`, `_isReady`, `_isStart`, `_isAudioStart`, `mediaProjection`, and AccessibilityService `ctx`.
- `src/server/connection.rs` pushes `cloudsend_status` immediately after authorization and also keeps a throttled timer push; the JNI query must time out quickly and skip when a previous query is still running.
- Flutter status values are `bool?`; `null` is a valid waiting state and must render as gray `--`, not red.
- `CloudSendStatusModel.reset()` is required on close and non-Android manual reconnect. Android auto-reconnect must not actively reset the panel on retry ticks.
- If no status packet arrives for 8 seconds, `CloudSendStatusModel` clears only the PC status monitor fields to `null` / gray waiting until the next real Android packet arrives. This display-only stale handling must not clear permissions, screen sharing, ignore, blank, or the relay session.

### 0.7 2026-05-09 status fallback and penetrate close fix

Current runtime truth:

- `connection.rs` must skip status sending when `call_main_service_get_by_name("cloudsend_status")` fails, returns empty, returns `{}`, or returns a non-status payload. It must never send hardcoded false-default JSON.
- Android `cloudsend_status` exception fallback must be an empty string, allowing Rust to skip the bad sample.
- Flutter status parsing must tolerate partial payloads from transitional Android service states; missing fields preserve the current/null value and must not default to false.
- `MainService.onDestroy()` clears Rust's `MAIN_SERVICE_CTX` only on explicit app/service destroy. Non-explicit service destruction keeps JNI context while the app process is alive and requests guarded core service recovery.
- `关穿透` must actively produce a clean frame. Static Android screens and some Xiaomi/OPPO/Vivo/Honor ROM compositors may not emit a new MediaProjection frame after `SKL=false` unless the display content changes.
- On Android R+ the first close-penetrate path is `requestOneShotScreenshotFrame(...)`, which uses Accessibility screenshot without turning on ignore mode.
- On Android 9/10, screenshot failure, or delayed screenshot timeout, the fallback is `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)`, which now only calls the server-side video refresh and must not rebind the current `VirtualDisplay` surface.
- `开无视 -> 开穿透 -> 关穿透` is a required supported combination. Closing penetrate must preserve `shouldRun == true` and `PIXEL_SIZEBack8 == 0` when ignore was already running.
- The one-shot clean-frame path may temporarily open `PIXEL_SIZEBack8` only when ignore is not running; it must restore the gate immediately after the one-shot frame write.
- 黑屏与"开无视"可同时处于开启状态，并沿用旧项目原则：`shouldRun` 开启时正常 `ImageReader` 让位，`createSurfaceuseVP8()` 与 `nZW99cdXQ0COhB2o` 的无视截图循环/回调即使 `BIS` 为 true 也继续推帧，让"开无视"在黑屏下仍能接管并投放画面。黑屏期间这些帧仍会经过 Rust 黑屏恢复与近全黑帧跳过保护，避免偶发截到完整黑层时覆盖 PC 画面；关黑屏后无视循环保持原状态继续工作。

---

## 1. 核心原则（Core Runtime Principles）

这个项目的 Android 运行时至少有四层状态，不能压成一个“开/关”：

1. Android core connection/id service state
2. Android screen sharing / `MediaProjection` state
3. Android frame source state
4. PC 端首帧等待状态（`waiting-for-first-frame state`）

必须记住：

- 服务存活 != `MediaProjection` 存活
- 核心连接服务在线 != 屏幕共享已开启
- `MediaProjection` 丢失 != 应用停止
- PC 端 waiting 状态必须在收到**任何真实首帧**时清除，而不只是正常视频路径的首帧

关键锚点：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/common.kt`
- `flutter/lib/models/model.dart`
- `libs/scrap/src/android/pkg2230.rs`

---

## 2. 组件角色（Component Roles）

### 2.1 `oFtTiPzsqzBHGigp.kt`

角色：主 `FlutterActivity`

职责：

- 作为 Android 入口 Activity
- 权限检查 / channel 入口
- overlay 权限相关辅助
- `ensure_core_service` / `start_screen_share` / `stop_screen_share` 方法入口

### 2.2 `DFm8Y8iMScvB2YDw.kt`

角色：`MainService`

职责：

- `MediaProjection`
- `ImageReader`
- 正常视频采集
- projection stop / restore / keep-alive
- 前台通知
- overlay keep-alive 刷新
- 核心服务保活与屏幕共享停止解耦

### 2.3 `nZW99cdXQ0COhB2o.kt`

角色：`AccessibilityService`

职责：

- 输入注入
- 节点遍历 / overlay 行为
- `SKL` 路径
- `shouldRun` 路径
- Android 11+ screenshot fallback

### 2.4 `DFrLMwitwQbfu7AC.kt`

角色：`FloatWindowService`

职责：

- 浮窗保活
- 返回 `START_STICKY`

### 2.5 `pkg2230.rs`

角色：主 Android Rust JNI bridge

职责：

- Android raw frame 桥接
- `VIDEO_RAW`
- `PIXEL_SIZEBack` / `PIXEL_SIZEBack8`
- `FrameRaw.force_next`
- 命令分发到 Kotlin 层

### 2.6 `common.kt`

角色：Android 全局状态面板

关键变量：

- `SKL`
- `shouldRun`
- `gohome`
- `BIS`
- `SCREEN_INFO`

---

## 3. Android 服务状态（Android Service States）

可用如下脑内模型：

- `A0 app process stopped`
  - app 进程未运行，核心连接服务不存在
- `A1 service alive, no active MediaProjection stream`
  - 核心连接/id 服务活着
  - PC 可连接，ZEGO 语音可工作
  - screen sharing / projection 未产出正常视频
- `A2 service alive, MediaProjection stream active`
  - 正常共享路径在工作
- `A3 service alive, ignore fallback active or being requested`
  - projection 不可用
  - 正在 fallback 或等待 fallback

关键锚点：

- `DFm8Y8iMScvB2YDw.kt`
  - `_isReady`
  - `_isStart`
  - `stopScreenShareOnly()`
  - `mediaProjection`
  - `savedMediaProjectionIntent`
  - `killMediaProjection()`
  - `handleProjectionStoppedKeepService()`
  - `restoreMediaProjection()`
  - `startIgnoreFallback()`

源码事实：

- `stopScreenShareOnly()` / `killMediaProjection()` 释放 projection / virtualDisplay / imageReader 等资源，但并不会把整个核心服务视为已完全退出。
- `handleProjectionStoppedKeepService()` 的含义是**保持服务存活**；只有 `shouldRun && accessibility` 同时成立时才延续 ignore fallback。
- `ACT_ENSURE_CORE_SERVICE` 是 Flutter 启动/绑定核心服务的 service action。
- `onStartCommand()` 返回 `START_STICKY`，仅作为前台核心服务保活；网络、锁屏、状态变化不得触发 `MainService` 重启或 `_isReady` 重写。

---

## 4. Android 帧源状态（Frame Source States）

### 4.1 正常共享路径（normal MediaProjection video path）

锚点：

- `DFm8Y8iMScvB2YDw.kt`
- `ImageReader`
- `libs/scrap/src/android/pkg2230.rs`
- `VIDEO_RAW`

行为：

- `MediaProjection` + `ImageReader` 产出正常共享帧
- 帧被送入 raw 输出路径
- 只要主路径稳定，PC 端通常进入正常远控画面

### 4.2 穿透路径（SKL pass-through path）

锚点：

- `common.kt::SKL`
- `nZW99cdXQ0COhB2o.kt`
- `pkg2230.rs::PIXEL_SIZEBack`

行为：

- 受 `SKL` 控制
- 更偏向 Accessibility / 穿透语义
- 与正常 `MediaProjection` 路径不是同一个状态量

### 4.3 无视回退路径（ignore-capture fallback path）

锚点：

- `common.kt::shouldRun`
- `nZW99cdXQ0COhB2o.kt`
- `pkg2230.rs::PIXEL_SIZEBack8`
- `pkg2230.rs::VIDEO_RAW`

行为：

- projection 不可用或等待恢复时，fallback 可能开启
- Android 11+ 可走 screenshot fallback
- Android 10 明确不承诺 screenshot fallback

---

## 5. PC 端显示状态（PC-Side Display States）

PC 不应与 Android 服务状态混为一谈：

- `P0 disconnected`
- `P1 connected but waiting for first frame`
- `P2 connected and receiving normal video`
- `P3 connected and receiving ignore fallback frame`
- `P4 reconnecting`

关键锚点：

- `flutter/lib/models/model.dart`
  - `waitForFirstImage`
  - `waitForImageTimer`
  - `showConnectedWaitingForImage()`
  - `onEvent2UIRgba()`
- `flutter/lib/common.dart`
  - `showMobileActionsOverlayAboveDialogs()`
  - `removeMobileActionsOverlayEntry()`

源码事实：

- waiting 状态是 Flutter/PC 自己的显示状态机。
- 即使 Android 服务活着，PC 仍可能处于 `P1 waiting`。
- `P1 waiting` 不会自动发 `"开无视"` 或截屏 fallback；允许补发正常 `sessionRefreshVideo(...)`。
- 如果需要无视/截屏备用画面，必须由用户手动点击 Android 操作按钮，且 Android 无障碍已开启。
- 一旦任何真实 RGBA 帧到 UI，`onEvent2UIRgba()` 会清除 waiting。

---

## 6. 关键事件规则（Key Event Rules）

### 6.1 打开分享（Open Share / `restoreMediaProjection`）

当前行为：

1. `restoreMediaProjection()` 启动恢复流程
2. 每次点击都会重新武装一次 `clearIgnoreOnceAfterShareStart`
3. 如果当前没有 ignore fallback，则临时调用 `startIgnoreFallback(...)` 兜住授权/恢复期间的画面
4. 如果已有 ignore fallback，则保持现有 ignore 状态，不重复启动
5. 如果 `savedMediaProjectionIntent` 仍有效，则尝试直接恢复 projection
6. 真正恢复成功后，只消费本次开共享的一次性清理：
   - 停止 ignore capture 一次
   - `PIXEL_SIZEBack8 = 255`
   - 恢复正常共享路径

不得回归：

- 不要在 `MediaProjection` 真恢复前提前禁用 ignore fallback
- 不要把 PC waiting timer 改回自动切到 ignore/screenshot
- 不要把 `startCapture()` 改回无条件清 `shouldRun/SKL/PIXEL_SIZEBack8`
- 不要让已有视频帧持续清除后续用户手动"开无视"
- 不要把“尝试恢复”误当成“已恢复”

### 6.2 关闭分享 / projection stopped

当前行为：

1. 释放 projection / virtualDisplay / imageReader / encoder 资源
2. 服务语义保持“活着 / ready”
3. Flutter `stop_screen_share` / 本机停止按钮只停 screen sharing、ignore fallback、`SKL` 与 `PIXEL_SIZEBack8` 放行状态
4. 远端侧按钮 `关共享` 通过 `stopScreenShareAndStartIgnore(...)` 停止 screen sharing 后，在无障碍已开启时自动进入 ignore fallback
5. 刷新前台通知
6. 重新确保 overlay keep-alive

不得回归：

- 不要把 close-share 重新改成 service destroy
- 不要把 projection stop 解释为整个 Android 端彻底停止
- 不要让 Flutter 本机停止按钮自动开启 ignore/screenshot fallback
- 不要移除远端侧按钮 `关共享` 的自动 ignore fallback

### 6.3 熄屏（Screen Off）

当前行为：

- 项目不会因为熄屏就主动停止服务
- 熄屏/亮屏/网络变化只允许通过 `refreshCoreKeepAlive(...)` 刷新已有前台通知、CPU wake lock、Wi-Fi lock 和悬浮窗；不改 `_isReady`、不触发 `MainService` 重启、不停止 `MediaProjection`、不清权限、不触碰 PC session
- Android 14+ / Android 15 QPR1+ 锁屏导致 projection stop 时走 `handleProjectionStoppedKeepService(...)`：仅标记屏幕共享停止，清理投屏资源和 Android 14+ 旧授权缓存，核心服务与中继连接继续存活
- 当熄屏前存在 screen sharing，且熄屏后 projection 丢失，且无障碍已开启时，允许自动进入 ignore fallback 保持画面
- 由于部分 ROM 锁屏后不会立刻把 `mediaProjection` 置空，熄屏后的延迟检查以“熄屏前存在 screen sharing + 无障碍已开启 + 当前未在 ignore”为兜底条件
- 普通保活路径只有在无障碍已开启且 `shouldRun == true` 时，才允许延续已有 ignore fallback

不得回归：

- 不要重新引入“熄屏必停服务”的逻辑
- 不要把系统行为与项目主动策略混为一谈
- 不要在无障碍未开启时由熄屏触发 ignore/screenshot

### 6.4 waiting-for-first-frame

当前 Flutter 行为：

1. 显示 waiting dialog
2. 将 Android 操作 overlay 提到对话框上方
3. 不自动发送 `"开无视"` 或截屏 fallback；只允许正常 `sessionRefreshVideo(...)`
4. 若仍无首帧，定时器只重新提升 Android 操作 overlay，不补发帧源指令
5. 任何真实 RGBA 帧到达 `onEvent2UIRgba()` 时清理等待状态

不得回归：

- 不要只等待“正常视频首帧”
- 不要让 waiting dialog 遮住 Android 操作按钮
- 不要把 waiting timer 改回自动切无视/自动截屏/自动刷新视频

---

## 7. Android 版本边界（Android Version Boundary）

关键上报：

- `android_sdk_int`
- `android_ignore_capture_supported`

填充位置：

- `src/server/connection.rs`

运行时落地点：

- `DFm8Y8iMScvB2YDw.kt::startIgnoreFallback()`

源码事实：

- `sdk_int >= 30`：认为具备 ignore-capture fallback 能力
- `sdk_int < 30`：Android 10 仅保持服务存活，不伪装为支持 screenshot fallback
- ignore-capture 能力只代表用户手动"开无视"或已有 ignore 保活路径可用，不代表 PC waiting 可以自动触发。

不得回归：

- 不要让 Android 10 看起来和 Android 11+ 拥有相同 fallback 保证
- 不要在 UI 层丢掉这个平台差异

---

## 8. JNI / 原始帧守卫（JNI and Raw Frame Guards）

关键锚点：

- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`

### 8.1 `VIDEO_RAW`

含义：

- raw frame 通道
- 在 fallback 模式希望继续送帧时，必须正确启用

### 8.2 `PIXEL_SIZEBack`

含义：

- 与 `SKL` 路径守门相关

### 8.3 `PIXEL_SIZEBack8`

含义：

- ignore frame 守门
- `0`：允许 ignore frame 通过
- `255`：阻断 ignore frame

### 8.4 `FrameRaw.force_next`

含义：

- 恢复后的第一帧即使与前一帧重复，也必须强制送出

不得回归：

- 修改帧路径后，必须同时检查：
  - `VIDEO_RAW`
  - `PIXEL_SIZEBack`
  - `PIXEL_SIZEBack8`
  - `force_next`
- 当前主模块是 `pkg2230.rs`，但 `ffi.rs` 中仍有相似逻辑，不能让两者无意识漂移

---

## 9. keep-alive / overlay / notification 现实（Keep-Alive Reality）

已核事实：

- manifest 存在 `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `MainService` 声明了 `android:foregroundServiceType="mediaProjection"`
- 主服务里存在 `ACT_ENSURE_CORE_SERVICE`，只用于 App 显式启动/绑定核心服务
- Current source truth: `BootReceiver.kt` still launches `DFm8Y8iMScvB2YDw` with `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` when `start_on_boot` and pre-permissions pass, but that action is core-service only when it has no `EXT_MEDIA_PROJECTION_RES_INTENT`.
- overlay keep-alive 受 `Settings.canDrawOverlays(...)` 权限门控
- `FloatWindowService` 返回 `START_STICKY`

结论：

- 当前代码目标是**在 Android 限制下尽可能保持服务可恢复**，不是承诺任何 OEM 场景下绝对不被杀进程
- 因此不要把“尽力保活”写成“必然永生”

---

## 10. Android 自定义命令与运行时的关系（Command-to-Runtime Relationship）

真实链路：

- `overlay.dart` / `input_model.dart`
- `src/flutter_ffi.rs`
- `src/ui_session_interface.rs`
- `src/client.rs`
- `src/server/connection.rs`
- `pkg2230.rs`
- Kotlin services

这意味着任何 Android 运行时改动，都可能跨到：

- UI 按钮可见性
- 命令 type / url 编码
- server 消息分发
- JNI bridge 分支
- Kotlin service 行为

因此运行时任务必须按**跨层任务**处理，而不是只改 Kotlin。

---

## 11. Android 相关但原文档易漏的辅助面（Often-Missed Android Surfaces）

### 11.1 `BootReceiver.kt`

- 负责开机启动路径
- 受 `start_on_boot` 和权限条件约束

### 11.2 `ig2xH1U3RDNsb7CS.kt`

- 剪贴板同步桥
- 会把 text / HTML 封装为 protobuf `MultiClipboards`

### 11.3 `KeyboardKeyEventMapper.kt`

- 键盘事件映射

### 11.4 `VolumeController.kt`

- 音量控制辅助

### 11.5 `XerQvgpGBzr8FDFr.kt`

- 权限 / 透明 Activity

这些文件不一定出现在每次 Android 任务的第一批入口中，但一旦问题涉及权限、输入、系统交互、辅助能力，就必须纳入排查。

---

## 12. 防回归清单（Regression Checklist）

在提交任何 Android 运行时改动前，至少重新确认：

1. 是否把服务状态和 projection / frame 状态重新绑死了？
2. 是否把核心连接服务和屏幕共享按钮重新混成一个“服务开关”？
3. 是否把 PC waiting 状态错误地只绑定到正常视频帧？
4. waiting dialog 是否又遮住了 Android 操作按钮？
5. waiting timer 是否又自动发送"开无视"或截屏 fallback？
6. Android 10 是否被错误宣传为支持 screenshot fallback？
7. projection 丢失后是否还会刷新 notification / overlay keep-alive？
8. `force_next` / `VIDEO_RAW` / `PIXEL_SIZEBack8` 是否仍符合当前恢复语义？
9. 是否忘了 `pkg2230.rs` 与 `ffi.rs` 的同步风险？
10. 是否改坏了 `savedMediaProjectionIntent` 的使用与清理？
11. 是否把 “close-share” 错写回 “stop service”？
12. 是否只改了 Kotlin 而没同步检查 Flutter / Rust / server 侧？

---

## 13. 文档同步写法（How Future Agents Should Sync This Doc）

后续 agent 更新本文件时必须继续采用：

- 中文解释运行时意义
- 英文保留真实 symbol / path / action / permission 名
- 每条关键结论至少给一个代码锚点
- 不得把“推测”“一次测试结论”写成常态真相
