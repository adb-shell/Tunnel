# Changelog

## [v5.2.1-android-screen-share-first-connect-26] Android first-connect projection stability - 2026-06-13

### Android Runtime / Screen Sharing
- `DFm8Y8iMScvB2YDw.updateScreenInfo(...)` no longer stops and restarts active capture when screen metrics change. It now resizes/rebinds the existing `VirtualDisplay` surface only, preserving the active `MediaProjection` token and preventing Android 14+ one-shot projection invalidation.
- Remote `start_capture2` open/close commands are ignored during the short authorized-connection settle window only when a live/starting/in-flight projection already exists. This prevents PC first-connect event noise from closing a manually authorized share or reopening the ordinary projection dialog.
- PC first-connect / reconnect / status-refresh paths remain non-authorizing: they may request normal video refresh while sharing is active, but they must not create a new screen-share prompt.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-android-screen-share-prompt-25] Android screen-share prompt stability - 2026-06-12

### Android Runtime / Screen Sharing
- `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)` no longer unbinds/rebinds the active `VirtualDisplay` surface during PC `add_connection` / waiting-for-image refresh. It now stays a server-side normal video refresh, preventing some ROMs from invalidating the existing `MediaProjection` and reopening the screen-share authorization dialog.
- `DFm8Y8iMScvB2YDw.startCapture()` now treats `VirtualDisplay` creation as the success gate. `_isStart` is set only after the virtual display is created/attached successfully, preventing false active screen-share state after `createVirtualDisplay()` failure.
- `DFm8Y8iMScvB2YDw.createOrSetVirtualDisplay(...)` now handles `SecurityException` / virtual-display creation errors as screen-share loss only: release projection resources, keep the core service alive, and do not call `requestMediaProjection()` from the failure path. New authorization prompts remain limited to explicit `start_screen_share` / side-button `开共享` actions.
- `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` without `EXT_MEDIA_PROJECTION_RES_INTENT` now keeps only the core service alive instead of requesting projection permission. This prevents boot/start/recovery/service-init paths from opening the screen-share dialog without a user action.
- Legacy Flutter `init_service` now only ensures/binds `MainService`; legacy Flutter `start_capture` is non-authorizing and only refreshes an already-active normal video path. New `MediaProjection` prompts are limited to explicit `start_screen_share` and side-button `开共享` / `start_capture2`.
- `restoreMediaProjection(...)` now blocks new Android projection prompts by default. PC connect/reconnect/first-frame refresh paths can no longer reopen screen-share authorization unless an explicit Android UI start or side-button `开共享` passes `allowPermissionPrompt = true`.
- `forceVideoFrameRefresh(...)` is now a no-op unless normal screen sharing is fully active, preventing PC first-connect refresh from nudging the Rust video service while Android projection is incomplete or inactive.
- Screen-share permission requests are guarded by an in-flight flag while the Android projection dialog is active, so duplicate start events cannot stack multiple permission dialogs. There is no post-result cooldown; explicit close-share then open-share can request projection again immediately.
- MediaProjection activity launch / permission-result consumption failures now clear the in-flight guard and keep `MainService` alive instead of crashing or leaving a stale prompt lock.
- `DFm8Y8iMScvB2YDw.startCapture()` now keeps a short `captureStarting` first-frame acceptance window while `VirtualDisplay` is being created. This prevents fast initial `ImageReader` frames from being dropped before `_isStart` flips true, which can leave some static Huawei Android 10-13 devices stuck on PC waiting-for-image until the user touches the screen.
- After capture starts successfully, Android schedules a side-effect-free normal `forceVideoFrameRefresh("capture-started")` nudge. This does not rebind `VirtualDisplay` and does not request a new screen-share authorization.
- When screen sharing is explicitly stopped or a saved Android 10-13 projection token is restored, stale `VirtualDisplay` instances are released instead of being reused. This keeps Huawei/Xiaomi/Vivo/OPPO ROMs on a clean display pipeline after rapid stop/start or reconnect cycles.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-zego-busy-state-cleanup-24] ZEGO stale busy-state cleanup - 2026-06-10

### ZEGO Voice Call
- Android connection removal now also clears `MainService` pending incoming voice-call cache through `remove_voice_call_state`, preventing a stale background invite from being replayed after the controlling PC disconnects.
- Android Flutter voice-call state handling now cross-checks incoming voice-call events against the native CM client list and native voice-call flags before creating or keeping local busy state. Orphaned or stale voice-call flags are cleared before `_hasLocalAndroidVoiceCall(...)` can reject a fresh invite.
- This cleanup is state-only. It does not modify ZEGO token creation, room/stream derivation, `ZegoVoiceCallModel.join/leave`, or the official SDK media sequence (`loginRoom` -> `startPublishingStream` / `startPlayingStream` -> `stop...` / `logoutRoom`).
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-doc-revert-sync-23] Reverted screen-share recovery docs sync - 2026-06-10

### Documentation
- Synced engineering docs after reverting the prior Android screen-share recovery change.
- Superseded by `v5.2.1-android-screen-share-prompt-25`: `DFm8Y8iMScvB2YDw.createOrSetVirtualDisplay(...)` no longer calls `requestMediaProjection()` from the `SecurityException` / virtual-display failure path.
- Superseded by `v5.2.1-android-screen-share-prompt-25`: `BootReceiver.kt` still starts `DFm8Y8iMScvB2YDw` with `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE`, but that action is core-only when it has no `EXT_MEDIA_PROJECTION_RES_INTENT`.
- No code, build, clean, or git commit was executed by Codex.

## [v5.2.1-zego-platform-relax-22] ZEGO platform gating and stale busy cleanup - 2026-06-10

### ZEGO Voice Call
- Removed the PC-side non-closing prompt that said `安卓屏幕共享已停止，请在被控安卓端点击启动服务并重新授权屏幕共享。`.
- PC desktop ZEGO voice-call toolbar/chat-menu entries no longer depend on `PeerInfo.platform == kPeerPlatformAndroid`; the current connected session may attempt a ZEGO invite even if the Android platform string was not recognized.
- `src/client/io_loop.rs::Data::NewVoiceCall` no longer rejects non-Android platform strings before token creation. It still creates an isolated 1v1 ZEGO room using the current PC id, current remote id, and request timestamp.
- Android controlled-side voice-call busy checks now clear stale `inVoiceCall` / `incomingVoiceCall` flags from disconnected clients before rejecting a new incoming invite. This lets PC2/PC3 call Android after PC1 hangs up or disconnects, while still preserving one active 1v1 call at a time.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-android-relay-reconnect-zego-21] Android relay reconnect and ZEGO stale-state cleanup - 2026-06-09

### Updated

- Android auto reconnect retry interval is now 2.5 seconds while keeping the 60-second silent grace window. Android network-available callbacks request a rendezvous/register refresh without restarting `MainService`.
- PC waiting / reconnect / peer-info paths may send normal `sessionRefreshVideo(...)` requests to wake an already-authorized normal screen-share first frame. This does not start ignore mode or screenshot fallback.
- Android authorization now pushes one immediate real status packet from JNI, so PC state catches up right after reconnect instead of waiting for the next periodic status tick.
- Android status periodic pushes are now throttled to 2 seconds after the immediate authorization packet, with 200ms JNI timeout and single-flight protection so status sampling cannot block the connection loop.
- Android auto reconnect password continuation now reuses the remote password entered/passed for the peer in the current PC process, and falls back to build-in `default-connect-password` when the session cache is empty. The cache survives session object recreation and covers both `input-password` and `re-input-password`; it still never uses local `mainGetPermanentPassword()` as a remote password.
- `CloudSendStatusModel` stale detection now clears the PC status panel back to the gray waiting state when no real Android status packet arrives for 8 seconds. This is display-only and does not clear permissions, screen sharing, ignore mode, blank mode, or the relay session.

### Android Runtime / PC Reconnect / ZEGO
- `DFm8Y8iMScvB2YDw.refreshCoreKeepAlive(...)` now refreshes the existing foreground notification, CPU wake lock, Wi-Fi lock, and floating window keep-alive on screen/network/memory events without restarting `MainService`, changing `_isReady`, or touching `MediaProjection`.
- Android 14+ screen sharing now follows one-shot `MediaProjection` semantics: `reuseVirtualDisplay` is disabled for Android 14+, stale saved projection intents are cleared on stop/loss/security failure, and each new share request uses a fresh capture intent.
- Lock-screen/system `MediaProjection` stop is now treated as screen-share loss only. `handleProjectionStoppedKeepService(...)`, `stopCapture2()`, `killMediaProjection()`, and `stopCaptureKeepService()` keep `_isReady = true`, refresh foreground/CPU/Wi-Fi/floating-window keep-alive, and do not close the core relay session.
- Non-explicit `MainService.onDestroy()` no longer clears Rust JNI context while the app process is alive; it requests a guarded `ACT_ENSURE_CORE_SERVICE` restart. Explicit destroy still clears the context immediately.
- Android `connectStatus` now follows the official RustDesk-style raw rendezvous state again: `mainGetConnectStatus()` `status_num` is assigned directly to `_connectStatus`, with no UI debounce and no fake readiness.
- Android auto reconnect now forces relay, performs one guarded early retry shortly after the timer starts, and reuses the current PC process cache or build-in `default-connect-password` if a password prompt appears during reconnect, avoiding manual `123` entry.
- Superseded by `v5.2.1-android-screen-share-prompt-25`: Android authorized remote connections still trigger a small normal-video refresh burst through `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)`, but that refresh no longer rebinds the active `VirtualDisplay` surface or reopens screen-share authorization.
- `LoginConfigHandler.initialize(...)` now defaults CloudSend client sessions to strict relay-only mode. Force relay skips UDP NAT test, IPv6 punch setup, explicit IP/domain:port direct connection, and direct TCP/UDP/IPv6 candidate creation before `request_relay(...)`.
- Android ZEGO state cleanup now clears voice-call flags on disconnected clients and ignores stale local `ZegoVoiceCallModel.active` when a new incoming invite is the only current signal, allowing PC2 to call Android after PC1 hangs up or disconnects.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-zego-background-incoming-call-20] Android ZEGO incoming-call foregrounding - 2026-06-09

### ZEGO Voice Call
- Android incoming ZEGO calls now bring the app UI foreground from `MainService`, post a high-priority call notification/full-screen intent, and replay pending state through `onResume` / `onNewIntent` plus Flutter `flush_pending_voice_call_event`, so the existing 3-second auto-accept dialog runs even when the app was backgrounded.
- Android background incoming-call pending state is stored by `client id`, so clearing one cancelled/rejected invite cannot erase another pending invite from a different controlling PC.
- `ServerModel` now owns a per-client 3-second auto-accept timer independent of dialog rendering. If Android cannot show the incoming-call dialog immediately, the pending call can still auto-accept once the Flutter model receives the incoming state.
- Voice-call auto-accept timers are cleared on manual accept/reject, PC hangup/cancel, client removal, close-all, non-incoming state updates, and ZEGO failure cleanup, preventing stale timers from accepting a later invite.
- PC ZEGO business prompts now use `custom-nook-nocancel-hasclose-*` dialog types, so token-service failures, duplicate-call prompts, and process-owner-busy prompts do not close the remote-control session.
- Active ZEGO calls now retain a call timestamp on both PC and Android; delayed close packets older than the current active call are ignored so rapid hangup/reinvite sequences cannot close a newer room.
- Added `android.permission.USE_FULL_SCREEN_INTENT` for background incoming-call foregrounding compatibility.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-android-connection-stability-19] Android core service and PC reconnect hardening - 2026-06-09

### Android Runtime / PC Reconnect
- Android `MainService` is kept as a foreground `START_STICKY` core service with a 60-second internal keep-alive ticker for foreground notification, CPU wake lock, Wi-Fi lock, and floating window keep-alive only.
- Network changes, screen on/off, low-memory callbacks, and screen-share state changes must not restart `MainService`, rewrite `_isReady`, or stop Android `MediaProjection`.
- `src/ui_cm_interface.rs::remove_connection(...)` no longer sends `"stop_capture"` when the last PC connection is removed. PC disconnect, reconnect, and window close now only remove the connection record and do not stop Android screen sharing.
- Android automatic reconnect on PC uses one 2.5-second timer and a 60-second silent grace window before showing `Connecting...`; retry ticks do not clear permissions or reset `CloudSendStatusModel`.
- Android `cloudsend_status` push is throttled and guarded by a short JNI timeout / single-flight query so status sampling cannot block the connection loop.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-adb-hardening-docsync-18] ADB/LADB compatibility memory sync - 2026-06-04

### Android Local ADB
- Updated ADB engineering memory to match the current source: manual pair/connect now use endpoint fallback (`localhost`, `127.0.0.1`, and current Wi-Fi IPv4 when available), not a single `localhost:<port>` path.
- Documented `CloudSendAdbDnsDiscover` behavior: retry `NsdManager.FAILURE_ALREADY_ACTIVE`, prefer local-host matches, and keep non-local hosts as fallback for OEM ROMs.
- Documented `CloudSendAdbRunner` behavior: poll `adb devices` after connect, store `preferredSerial`, cap shell restart attempts, and reject pairing output containing failure keywords even if process status is misleading.
- Documented that failed manual pairing clears `paired_before`, while the ADB page `Auto` / `自动` action only scans/connects an already paired wireless-debugging endpoint.
- Clarified that automatic extraction of pairing port/code from Settings into `CloudSendAdbManager.pair(...)` is still future work.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-android-core-share-split-17] Android 核心服务与屏幕共享拆分 - 2026-06-01

### Android Runtime
- Android app 启动后默认拉起核心连接/id 服务，PC 可在无屏幕共享时连接 Android 并发起 ZEGO 语音通话。
- `Start service` / `Stop service` 改为仅控制屏幕共享 `MediaProjection`，停止屏幕共享不再关闭核心服务或已建立连接。
- Android 启动顺序调整为先同步 app config path，再启动核心服务；若 `MainService` 已运行，同步目录时会刷新 native config path。
- PC 等待首帧时不再自动发送"开无视"或截屏 fallback；允许补发正常 `sessionRefreshVideo(...)` 唤醒已授权的正常屏幕共享首帧，只保持等待状态与 Android 操作浮层。
- 侧按钮 `开共享` 恢复为一次性无视兜底：每次点击都会重新武装一次清理，屏幕共享真正恢复后只清一次无视状态，不影响后续手动 `开无视`。
- 侧按钮 `关共享` 恢复为停止 `MediaProjection` 后自动进入无视保画面；锁屏导致 projection 丢失时，仅在无障碍已开启且之前有共享时自动切无视。
- 锁屏保画面补强为延迟双检查：锁屏前已有共享且无障碍已开启时，即使 ROM 尚未立刻清空 `mediaProjection`，也会切入 ignore fallback 保持画面。
- Android 小圆球悬浮窗默认透明但保留点击能力，菜单入口 `Show RustDesk` 改为 `进入软件`。
- Android 共享页移除 ZEGO 通话卡片标题图标，并将权限卡片中的 `Transfer file` 调整到权限项末尾。
- `startIgnoreFallback(...)` 增加无障碍硬守卫；无障碍未开启时，熄屏/黑屏/保活路径不会进入无视截屏流。
- Android 掉线自动重连改为单个 2.5 秒定时器，避免重复错误事件造成请求风暴；当前显示策略已在 2026-06-09 调整为前 60 秒静默恢复，超过 60 秒仍未恢复才显示连接提示。
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-adb-ui-control-16] ADB page start/stop and pairing dialog semantics - 2026-05-21

### ADB Page
- Changed the first ADB card button into a dynamic action: `Start service` while ADB shell is not ready, and a red `Stop service` while ADB shell is ready.
- Added `Cancel` to the pairing dialog. `Cancel` closes the dialog and performs no ADB/local-shell action.
- Changed `Skip` semantics: it now skips manual port/code input and directly attempts the ADB scan/connect/shell flow for an already paired wireless-debugging device. It no longer starts a non-ADB local shell.
- If `Skip`/auto-scan successfully reaches ADB shell, CloudSend records `paired_before=true`, so the next `Start service` does not show the pairing dialog again.
- Added `cloudsend_adb_stop` through Flutter constants, Flutter `AndroidAdbManager`, Android MethodChannel routing, `CloudSendAdbManager.stop`, and `CloudSendAdbRunner.stopServer`.
- `Stop service` closes the current shell, suppresses shell auto-restart, runs `adb kill-server`, clears the active connected/shell-ready state, and preserves stored pairing memory.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-adb-code-research-15] ADB-CODE automation research memory - 2026-05-21

### ADB-CODE Review
- Expanded `docs/ADB_LADB_INTEGRATION_MEMORY.md` with a deeper source-level review of the local `ADB-CODE/` project.
- Documented the useful reference areas: accessibility state machine, OEM Settings keyword strategy, node-tree extraction, click fallbacks, pairing-code/port parsing, progress logging, timeout handling, and manual fallback.
- Documented the high-risk areas that must not be copied into CloudSend by default: `/data/local/tmp` daemon deployment, boot helper, HTTP `/exec`, watchdog permission recovery, notification listener, overlay fallback, silent accessibility re-enable, broad port scanning, and Java libadb fallback.
- Defined the recommended future CloudSend automation boundary: reuse the existing CloudSend accessibility service, add a short-lived ADB automation controller, report progress into the ADB terminal card, and keep screen-share/side-button/monitor-panel logic isolated.
- No feature code, build script, clean command, build command, or git commit was executed by Codex.

## [v5.2.1-adb-ladb-local-14] Android local ADB/LADB integration - 2026-05-21

### Android Local ADB
- Added an isolated ADB page in the mobile home PageView. It does not alter the existing screen-share page, side-button logic, video stream, screenshot stream, or connection flow.
- Added Android local ADB runtime module under `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/`: `CloudSendAdbState`, `CloudSendAdbManager`, `CloudSendAdbRunner`, and `CloudSendAdbDnsDiscover`.
- Packaged `libadb.so` for Android ABIs used by the current build, with `useLegacyPackaging = true` so the native binary can be executed from the app native library directory.
- Added MethodChannel commands for ADB init/status/output/start/local-shell/pair/command. Flutter calls these through a direct `MethodChannel('mChannel')`, not `gFFI.invokeMethod()`, because the ADB methods return maps/strings instead of `Future<bool>`.

### LADB-Aligned Runtime Behavior
- `Start service` initializes ADB state. If `paired_before` is stored, it attempts automatic mDNS scan, ADB connect, and shell entry. If automatic startup fails, it falls back to the manual pairing dialog.
- Superseded by `v5.2.1-adb-hardening-docsync-18`: manual pairing now uses endpoint fallback (`localhost`, `127.0.0.1`, and current Wi-Fi IPv4 when available), then starts ADB server/connect/shell on success.
- Superseded by `v5.2.1-adb-ui-control-16`: `Skip` now skips manual input and attempts the ADB scan/connect/shell flow; it no longer enters non-ADB local shell mode.
- mDNS discovery scans `_adb-tls-connect._tcp`, uses a MulticastLock, prefers current/newer local services, and falls back safely when no port is found.
- Terminal output is bounded, user-visible, and polled by the ADB page. Shell restart is capped to avoid uncontrolled infinite restart loops.

### Boundaries
- Superseded by later ADB automation work: accessibility-assisted wireless-debugging automation now exists as a best-effort, cancellable flow inside `nZW99cdXQ0COhB2o.wirelessDebugAutomation*`.
- PC remote ADB command transport is not implemented yet; future work must use explicit request/response messages, authorization, timeout, whitelist policy, output truncation, and audit logging.
- Current `cloudsend_adb_command` is an interactive terminal write into the long-lived local shell. It is not a complete remote RPC result path because it does not provide per-command completion, exit code, stdout/stderr separation, or response boundaries.
- Future PC remote command/script execution should add a dedicated bounded request/response executor instead of treating the terminal stream as a reliable command result.
- Existing Android monitor-panel fields remain independent from ADB state. ADB failures must not make screen-share/side-button status turn red.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-docsync-13] Current source-truth documentation sync - 2026-05-18

### Current Naming / Version Truth
- Android visible app label and foreground notification title are now `云计划`, sourced from `flutter/android/app/src/main/res/values/strings.xml` and referenced by AndroidManifest/MainService notification code.
- Runtime product name remains `CloudSend`; do not confuse it with the Android launcher label.
- Current version is `5.2.1` in `Cargo.toml`, `libs/portable/Cargo.toml`, packaging metadata, and PC build defaults; Flutter package version is `5.2.1+59`.
- Root `Cargo.lock` and `libs/portable/Cargo.lock` project package entries are synchronized to `cloudsend` / `cloudsend-portable-packer` version `5.2.1`; third-party dependency versions are intentionally unchanged.
- Android SO name is `libcloudsend.so`; Windows DLL name is `cloudsend.dll`.
- Current Windows build entry is `new-build.cmd`, and completed PC portable artifacts are copied to `PC-Bulid`.

### Documentation Guardrails
- `README.md` and `PC-Build.md` keep inherited upstream/environment background, but their top notes now state the current project source truth.
- Added `docs/SOURCE_TRUTH_AUDIT_2026_05_18.md` as the clean full-Markdown/source-anchor audit record.
- Updated `terminal.md` to describe the subsystem as CloudSend terminal service while noting its upstream RustDesk inheritance.
- Do not treat old `RustDesk`, `rustdesk-1.4.6`, `librustdesk.dll`, `libdaxian.so`, or `PC.cmd` references in historical/background sections as current project facts.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-12] Android status monitor no-fake-red fallback - 2026-05-11

### Status Monitor Correctness
- Removed all hardcoded false-default `cloudsend_status` fallbacks. If `call_main_service_get_by_name("cloudsend_status")` fails, returns empty, returns `{}`, or returns a non-status payload, `connection.rs` now skips that push instead of sending fake red values.
- `cloudsend_status_message()` now returns `Option<Message>`; both the immediate-after-authorization push and the throttled timer push send only when a valid Android status JSON exists.
- `DFm8Y8iMScvB2YDwGYN("cloudsend_status")` now returns an empty string on exception, allowing Rust to skip the bad sample and let Flutter keep the waiting `null` state.
- `CloudSendStatusModel.updateFromEvent()` preserves the current/null value when a JSON key is missing; it no longer uses `current ?? false`.

### Android ROM Lifecycle Hardening
- `MainService.onDestroy()` now calls `ClsFx9V0S.VHsFQTvK()` to clear Rust's `MAIN_SERVICE_CTX` GlobalRef, avoiding stale service references after OEM ROM service kills/restarts.
- Added the matching `Java_pkg2230_ClsFx9V0S_VHsFQTvK` JNI export and Kotlin `external fun VHsFQTvK()` declaration.
- Confirmed `nZW99cdXQ0COhB2o.ctx` remains `@Volatile`; no ffi mirror was added because `ffi.rs` / `ffi.kt` do not contain the `ygmLIEQ5` MainService registration path.

### Guardrails
- Never reintroduce a hardcoded all-false status JSON as a fallback. Unknown/unavailable status must remain `null` and render gray `--`.
- Gray `--` is the correct display for waiting/unknown; red is reserved for a real false value from Android.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-11] Android status fallback and penetrate/ignore combination safety - 2026-05-09

### Status Monitor Compatibility
- Superseded by hotfix-12: status delivery must skip invalid/JNI-failed samples rather than sending false-default JSON.
- `DFm8Y8iMScvB2YDwGYN("cloudsend_status")` exception fallback must not produce fake false values.
- `CloudSendStatusModel.updateFromEvent()` must preserve current/null values for missing fields.

### Penetrate Close / Frame Refresh
- Fixed `关穿透` on static screens and slow/OEM Android compositors: closing penetrate now requests a one-shot clean frame to overwrite the last penetrate frame instead of waiting for a local screen movement.
- Superseded by `v5.2.1-android-screen-share-prompt-25`: Android 9/10 fallback may call `forceVideoFrameRefresh()`, but that function no longer rebinds the current `VirtualDisplay` surface.
- Added delayed fallbacks for Android R+ screenshot delays/failures, so `关穿透` can recover even when `takeScreenshot()` is slow or rejected by a ROM.

### Combination Guardrails
- `开无视 -> 开穿透 -> 关穿透` must preserve ignore mode:
  - `关穿透` only clears `SKL` and one-shot penetrate cleanup state.
  - It must not call `stopIgnoreCaptureLoop()`.
  - It must not set `PIXEL_SIZEBack8` back to 255 while `shouldRun == true`.
- The one-shot clean-frame gate only opens `PIXEL_SIZEBack8` temporarily when ignore is not already running, then restores it immediately after the frame write.
- Quick `关穿透 -> 开穿透` toggles clear any stale one-shot marker before starting penetrate rendering.

### Guardrails
- Do not replace the one-shot clean-frame logic with a plain `video_service::refresh()` only; static Android screens may not produce a new frame.
- Do not let penetrate close reset `shouldRun`, `pendingIgnoreCapture`, or user-requested ignore state.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-10] CloudSend status monitor synchronization fix — 2026-05-08

### Status Panel Correctness
- Fixed first-connection status flicker: Android now pushes one `cloudsend_status` packet immediately after authorization, instead of waiting for the next throttled timer tick.
- Changed `CloudSendStatusData` fields to nullable booleans so the monitor can render an explicit waiting state (`—`) instead of showing all-red false defaults before the first packet arrives.
- Added an 8s stale-status watchdog in `CloudSendStatusModel`; if status packets stop arriving, the panel returns to the gray waiting state until the next real Android packet arrives. The watchdog only changes PC display state and does not send any Android control command.
- Reset the monitor on session close and non-Android manual reconnect; Android auto-reconnect keeps status intact.

### Android Status Semantics
- Added `@Volatile` visibility protection for cross-thread Android status fields: `SKL`, `BIS`, `_isReady`, `_isStart`, `_isAudioStart`, `mediaProjection`, and AccessibilityService `ctx`.
- `cloudsend_status` now snapshots Android values before building JSON to avoid mixed-state reads.
- Split status meanings:
  - `screenshot`: special screenshot stream is actually running (`shouldRun && accessibility`).
  - `ignore`: ignore switch is logically on (`shouldRun || pendingIgnoreCapture`), including pending wait for accessibility.
- Added `nZW99cdXQ0COhB2o.isIgnorePending` for status aggregation.
- Kept the UI label "加密状态" unchanged; it still means Android accessibility service connection state.

### Guardrails
- Do not restore false-default status UI for the monitor; `null` means waiting/unknown and must render gray.
- Do not collapse `screenshot` and `ignore` back to the same source; they intentionally represent actual running stream vs requested switch state.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-9] CloudSend final residual cleanup — 2026-05-06

### Cleanup
- Renamed Android build environment variables from `RUSTDESK_*` to `CLOUDSEND_*` in `env.sh` and `build.sh`, while intentionally keeping the `/opt/rustdesk-toolchain` path and existing signing file locations.
- Replaced remaining desktop UI labels with `CloudSend`, including the desktop tab title and desktop settings About card.
- Renamed login provider sentinel value from `daxian` to `cloudsend`.
- Renamed internal string values: clipboard owner UTI, printer temp file prefix, heartbeat public-domain check, plugin callback target, plugin local data directory segment, and debug close log.
- Removed obsolete `migrate_package.sh`.

### Manual action required
- Existing Linux signing/profile files must be migrated by the user, not Codex:
  `RUSTDESK_ANDROID_* -> CLOUDSEND_ANDROID_*` in signing.env and
  `RUSTDESK_TOOLCHAIN_ROOT -> CLOUDSEND_TOOLCHAIN_ROOT` in `/etc/profile.d/rustdesk-toolchain.sh`.

### Guardrails
- Keep `/opt/rustdesk-toolchain`, `/etc/profile.d/rustdesk-toolchain.sh`, existing keystore filename, and existing keystore alias unless the build environment is intentionally rebuilt.
- No build, clean, server-side sed, or git commit was executed by Codex.

## [v5.2.1-hotfix-8] PC CloudSend DLL / portable startup fix — 2026-05-05

### P0: Fix Windows startup after CloudSend rename
- Fixed Windows Dart FFI loading: `native_model.dart` now opens `cloudsend.dll` instead of `librustdesk.dll`.
- Kept Linux Dart FFI naming aligned with the renamed Rust library by opening `libcloudsend.so`.
- Updated portable packer defaults from `rustdesk.exe` to `cloudsend.exe`.
- The portable extractor now renames both legacy `rustdesk.exe` and current `cloudsend.exe` to `CloudSend.exe`, and falls back to the packaged executable path if the renamed file is unavailable.
- Renamed the portable app-name runtime environment key from `RUSTDESK_APPNAME` to `CLOUDSEND_APPNAME` across the packer, Rust core, and Flutter constant.
- Updated Windows portable build output names from `rustdesk_portable.exe` / `rustdesk-{version}-install.exe` to `cloudsend_portable.exe` / `cloudsend-{version}-install.exe`.
- Renamed the privacy-mode RuntimeBroker helper from `RuntimeBroker_rustdesk.exe` to `RuntimeBroker_cloudsend.exe` across portable packaging, runtime privacy mode, and MSI cleanup.
- Renamed Android wake-lock tags from `daxian:*` to `cloudsend:*`.
- Renamed the Windows app-name export from `get_rustdesk_app_name` to `get_cloudsend_app_name` and synchronized the runner lookup.

### Root cause
- Part 4 changed the Windows Rust cdylib output to `cloudsend.dll`, and `flutter/windows/CMakeLists.txt` installs `cloudsend.dll`.
- The Flutter runtime still tried to open `librustdesk.dll`, causing FFI initialization failure and a white-screen startup.
- The self-extracting portable wrapper still had old `rustdesk.exe` defaults, making the extracted startup path fragile after the executable rename.

### Guardrails
- Windows Flutter builds must keep these three names aligned: `flutter/windows/CMakeLists.txt` installs `cloudsend.dll`, `flutter/windows/runner/main.cpp` loads `cloudsend.dll`, and `flutter/lib/models/native_model.dart` opens `cloudsend.dll`.
- Portable packages should use `cloudsend.exe` as the metadata startup executable and may normalize the extracted visible executable to `CloudSend.exe`.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-7] CloudSend identity migration Parts 1-4 — 2026-05-05

### Branding / Android identity
- Android package changed from `com.daxian.dev` to `com.cloudsend.app`.
- Android visible app label and notification title changed to `CloudSend` at this stage; current Android visible label is superseded to `云计划` by `v5.2.1-docsync-13`.
- Android deep link scheme changed to `cloudsend://`.
- Kotlin package directory changed to `flutter/android/app/src/main/kotlin/com/cloudsend/app/`.

### Rust runtime / protocol / Flutter UI
- Rust `APP_NAME` changed to `CloudSend`.
- Version check type values changed to `cloudsend-client` / `cloudsend-server`; RustDesk public version URL was disabled with `https://127.0.0.1/version/latest`.
- Android status protocol field renamed from `daxian_status` to `cloudsend_status` while keeping field number 39.
- Flutter event renamed to `update_cloudsend_status`; model/widget renamed to `CloudSendStatusModel` / `CloudSendStatusMonitor`.
- Session option renamed to `show_cloudsend_status_monitor` / `show-cloudsend-status-monitor`.
- Virtual display platform addition key renamed to `cloudsend_virtual_displays`.

### Android SO / FFI
- Cargo crate renamed to `cloudsend`; `[lib] name = "cloudsend"` now builds `libcloudsend.so`.
- Android build script copies `target/<target>/release/libcloudsend.so` to `flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`.
- Kotlin now uses `System.loadLibrary("cloudsend")`.
- Dart Android FFI now opens `libcloudsend.so`.
- Exported FFI symbols changed from `rustdesk_core_main*` to `cloudsend_core_main*` and generated bridge lookup strings were synchronized.

### Guardrails
- Do not revive `com.daxian.dev`, `daxian_status`, `DaxianStatusModel`, `libdaxian.so`, or `rustdesk_core_main` in new Android work.
- `Cargo.lock` was intentionally not edited by Codex; it should update when the user builds.
- No build, clean, or git commit was executed by Codex.

## [v5.2.1-hotfix-6] 无障碍权限感知的双通道控制 — 2026-04-17

### 核心特性

- PC 以 Android 无障碍（网络加密）服务状态为权威，动态决定是否启用双通道
- 无障碍未开或状态未知时，PC 只刷新/等待视频流，不发送"开无视"命令
- 无障碍已开时，PC 才允许视频流丢失 fallback 到截屏流
- 支持运行时动态切换：`cloudsend_status` 随当前节流状态推送同步 `accessibility` 字段
- 监测面板新增"加密状态"行

### 实现方式

- `DFm8Y8iMScvB2YDw.kt`: `cloudsend_status` JSON 增加 `accessibility`
- `model.dart`: `CloudSendStatusData.accessibility` 使用 `bool?`，`null` 表示尚未收到状态推送
- `model.dart`: 新增 `_canRequestAndroidBackupFrame`，作为所有自动"开无视"命令的守卫
- `model.dart`: 首帧 3s/10s fallback 在无障碍未知或未开时只执行 `sessionRefreshVideo`
- `overlay.dart`: 安卓状态监测显示"加密状态"

## [v5.2.1-hotfix-5] 修复 "开共享后一直卡在截屏流" 深度状态管理问题 — 2026-04-15

### P0: 状态残留 Bug 修复

- 修复用户取消 MediaProjection 授权后 Flutter `_isStart` 不回滚导致后续状态错乱
- 修复停服务再开服务时 `SKL` / `PIXEL_SIZEBack8` / `savedMediaProjectionIntent` / 黑屏 / 防触等状态残留
- 修复 `startCapture()` 启动前未强制清除三模式互斥状态，导致视频帧可能被 `SKL || shouldRun` 早退逻辑丢弃
- 修复 PC 端首帧 fallback 与 Android 授权流程的时序竞争：首次自动开无视从 500ms 延长到 3000ms

### 代码改动

- `nZW99cdXQ0COhB2o.kt`: 新增 `resetCaptureStates()`，统一清理 `shouldRun` / `SKL`
- `DFm8Y8iMScvB2YDw.kt`: `startCapture()` 开头强制重置；`destroy()` 补齐静态变量、黑屏、防触、token 清理
- `XerQvgpGBzr8FDFr.kt`: 授权失败时主动通知 Flutter `on_media_projection_canceled`
- `server_model.dart`: 新增 `onMediaProjectionDenied()` 回滚方法
- `server_page.dart`: 授权失败回调改为回滚服务状态
- `model.dart`: 首帧 fallback 延迟 500ms -> 3000ms

### 不受影响的功能

- 侧按钮协议和命令链路不变
- 画面传输协议、网络连接、安卓状态监测面板不变
- 黑屏、穿透、无视、防触功能本身不变，仅在关闭服务/启动共享时清理残留状态

## [v5.2.1-hotfix-4] 新增安卓状态监测面板 — 2026-04-17

### 功能新增

- PC 端右上角新增"安卓状态监测"面板，位于"显示质量监测"下方
- 显示 7 项 Android 被控端状态：视频/截屏/共享/无视/黑屏/穿透/防触
- "存在/开"绿色加粗，"丢失/关"红色加粗，标签灰色
- "显示设置"菜单新增"显示安卓状态监测"选项，默认开启
- 状态源：Android 端授权后立即推送一次真实 JSON，之后按当前节流状态周期推送

### 协议变更

- `Misc` 消息新增 field 39: `string cloudsend_status`，proto3 向后兼容
- `InvokeUiSession` trait 新增 `update_cloudsend_status(json: String)` 方法
- 新增会话配置 `show_cloudsend_status_monitor`，toolbar 使用 `show-cloudsend-status-monitor`

### 涉及文件

- `libs/hbb_common/protos/message.proto`, `libs/hbb_common/src/config.rs`
- `src/client.rs`, `src/ui_session_interface.rs`, `src/flutter.rs`, `src/ui/remote.rs`
- `src/server/connection.rs`, `src/client/io_loop.rs`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/lib/consts.dart`, `flutter/lib/common/widgets/setting_widgets.dart`, `flutter/lib/common/widgets/toolbar.dart`
- `flutter/lib/models/model.dart`, `flutter/lib/common/widgets/overlay.dart`
- `flutter/lib/desktop/pages/remote_page.dart`, `flutter/lib/desktop/pages/view_camera_page.dart`
- `flutter/lib/mobile/pages/remote_page.dart`, `flutter/lib/mobile/pages/view_camera_page.dart`

## [v5.2.1-hotfix-3] 新增开防触/关防触侧按钮 — 2026-04-16

### 功能新增

- 新增"开防触"和"关防触"两个侧按钮（蓝开红关，放在开/关穿透之后）
- 采用时序自适应策略：空闲时吸收本地触摸，远程事件活跃期短暂切换为穿透
- 新增 `MOUSE_TYPE_TOUCHBLOCK=11`，对应 `mask=43`，URL 前缀 `TouchBlock_Management`
- 新增 JNI 命令 `touch_block`，由 MainService 路由到 `AccessibilityService.setTouchBlockEnabled`
- 新增独立的透明 `touchBlockOverlay`，与黑屏 overlay 完全隔离，互不干扰
- watchdog 100ms 间隔，仅在状态转换时触发 `updateViewLayout`，IPC 开销最小化

### UI 修复

- 修复 PC 端点击"适应屏幕大小"后侧按钮随画面 scale 过度放大、显示不完整的问题
- 侧按钮 overlay 现在按 11 行真实高度计算可用空间，并在需要时自动限制 scale
- 位置修正 `tryAdjust` 使用侧按钮真实总高度，避免按钮组被屏幕底部裁切

### 已知局限（Android 系统限制，非 bug）

- 首次远程事件在 flag 切换到位前可能被吸收（概率极低）
- PC 活跃控制期间本地用户硬触摸可能穿透
- 完美拦截需设备管理员或 root，标准 APK 无法实现

### 涉及文件

- `src/common.rs`, `src/flutter_ffi.rs`
- `libs/scrap/src/android/pkg2230.rs`, `libs/scrap/src/android/ffi.rs`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/lib/models/input_model.dart`
- `flutter/lib/common.dart`
- `flutter/lib/common/widgets/overlay.dart`

## [v5.2.1-hotfix-2] 开黑屏防触摸 Bug 修复 — 2026-04-14

### P0: 移除错误的动态 FLAG_NOT_TOUCHABLE 切换逻辑

- 删除 `isBlackScreenActive` / `restoreBlockRunnable` / `setOverlayTouchBlock` 三件套
- `onMouseInput` 入口不再向主线程 `handler` 提交任务，高频远程输入不再阻塞主线程
- `onstart_overlay` 和 50ms 轮询 `runnable` 只同步 `overlay.visibility`，不再操作 flags
- `onDestroy` 同步移除已删除的 `restoreBlockRunnable` 引用
- 根本原因：防触摸逻辑 FLAG 语义反转 + 每帧 `updateViewLayout` + 50ms 轮询三重问题
- 正确机制：远程输入使用 `AccessibilityService.dispatchGesture`，不经过 overlay，无需动态切换 flag
- 文件: `nZW99cdXQ0COhB2o.kt`
