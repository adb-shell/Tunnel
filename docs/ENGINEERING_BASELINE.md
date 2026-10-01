# 工程基线 / Engineering Baseline

最后一次从全仓源码核验：2026-06-09
最近一次入口层级复核：2026-07-12

> 2026-07-12 接管说明：本文件保留 2026-07-12 前的详细工程基线；当前架构、风险和维护规则以 `docs/AI_ENGINEERING/` 与 `.codex/AI_RULES.md` 为新入口。旧事实未经重新核验时不得自动提升为当前结论。

> 本文件只记录**已经通过当前源码核验**的事实。
> 这里的中文用于解释，English symbol / path 用于保证 Codex / Claude Code 检索稳定。
> 若与源码冲突，以源码为准，并同步更新本文件。

---

## 0. 当前已确认的维护纪律（Current Maintenance Discipline）

- 后续修改只能改代码/文档，不替用户执行 `git commit`。
- 用户明确要求不由 Codex 执行编译命令；验证优先使用源码检索、静态 diff、定向 grep。
- 重要代码变更后必须同步当前 docs 文档体系；旧文档不得自动删除或整份恢复，先按 `docs/AI_ENGINEERING/LEGACY_DOCUMENT_MIGRATION_REPORT.md` 提取和映射知识。当前存在的 `docs/CHANGELOG.md` 仅作为不完整历史记录，不属于架构真相层。
- 文档永远是辅助记忆；若文档与源码冲突，以当前源码和当前 diff 为准。

### 0.1 2026-04-16 黑屏 overlay 输入性能基线

已修复 Android AccessibilityService 内黑屏 overlay 的错误动态防触摸逻辑，源码锚点：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`

当前基线：

- `onMouseInput(...)` 不能再提交黑屏触摸恢复/穿透相关 handler 任务。
- `onstart_overlay(...)` 和 50ms `runnable` 不能再通过 `WindowManager.updateViewLayout(...)` 动态切换 overlay touch flags。
- `BIS` 查询语义保留：`BIS = overLay.visibility != View.GONE`。
- 黑屏功能的运行时边界是 overlay 可见性切换；远程输入性能问题不要扩散修改到 Rust 协议、`PIXEL_SIZE*`、帧传输或侧按钮协议。

### 0.2 2026-04-17 防触摸功能基线

已新增独立透明 `touchBlockOverlay`，源码锚点：

- `src/common.rs`
- `src/flutter_ffi.rs`
- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/lib/models/input_model.dart`
- `flutter/lib/common.dart`
- `flutter/lib/common/widgets/overlay.dart`

当前基线：

- 侧按钮文本为"开防触"/"关防触"，放在"开穿透"/"关穿透"之后。
- 协议为 `wheeltouch -> MOUSE_TYPE_TOUCHBLOCK=11 -> mask=43 -> TouchBlock_Management -> touch_block`。
- 防触摸只操作独立 `touchBlockOverlay`，不得复用黑屏 `overLay`。
- 空闲默认吸收本地触摸，远程活跃时 500ms 穿透；watchdog 100ms tick，只有状态变化才更新 WindowManager。
- 此功能是标准 Android APK 能力范围内的近似防触，不承诺拦截恶意本地干扰或系统级手势。

### 0.3 2026-04-18 安卓状态监测基线

当前基线：

- 协议字段：`Misc.cloudsend_status = 39`。
- Android 查询入口：`call_main_service_get_by_name("cloudsend_status")`。
- PC Flutter 事件：`update_cloudsend_status`，payload key 为 `status`。
- UI model：`CloudSendStatusModel`，默认显示，配置键为 `show_cloudsend_status_monitor`。
- Toolbar 会话开关：`show-cloudsend-status-monitor`。
- UI Widget：`RemoteStatusMonitors` 组合 `QualityMonitor` 与 `CloudSendStatusMonitor`，只有两者同时显示时插入 6px 间距。
- Sciter UI 只保留 `update_cloudsend_status` 空实现，当前面板只在 Flutter UI 显示。

### 0.4 2026-04-22 共享/截屏流状态生命周期基线

当前基线：

- `startCapture()` 不得无条件清 `shouldRun/SKL/PIXEL_SIZEBack8`；只有侧按钮 `开共享` 通过 `restoreMediaProjection()` 武装的 `clearIgnoreOnceAfterShareStart` 可以在 screen sharing 真正恢复后消费一次并清 ignore fallback。
- 侧按钮 `开共享` 可临时进入 ignore fallback 兜住授权/恢复期间的画面；如果 ignore 已存在则保持现状，恢复 screen sharing 后只清一次。
- `destroy()` 是停服务全清入口，必须清 `savedMediaProjectionIntent`、黑屏、防触、无视/穿透残留状态。
- 授权取消回调为 `on_media_projection_canceled`，Flutter 端必须调用 `ServerModel.onMediaProjectionDenied()`。
- PC 首帧等待不再自动发送"开无视"或截屏 fallback；等待期间只显示 waiting dialog 与 Android 操作浮层，并可补发正常 `sessionRefreshVideo(...)` 请求来唤醒已授权的正常屏幕共享首帧。

### 0.5 2026-04-22 无障碍感知双通道基线

当前基线：

- Android 状态推送必须包含 `accessibility` 字段。
- PC 端不再存在自动"开无视"首帧 fallback；"开无视"只来自用户手动点击 Android 操作按钮。
- `accessibility == null` 或 `false` 时，Android native `startIgnoreFallback(...)` 必须直接跳过，不能进入截屏流。
- `accessibility == true` 时，用户手动"开无视"、侧按钮 `开共享` 的临时兜底、侧按钮 `关共享` 的保画面、以及锁屏后 projection 丢失的保画面路径才允许进入截屏流。
- 锁屏保画面的触发不只依赖 `mediaProjection == null`；部分 ROM 锁屏后会先断帧，因此延迟检查以“锁屏前有 screen sharing + 无障碍开启 + 当前未在 ignore”为兜底条件。
- 安卓状态监测面板的"加密状态"显示此字段。

---

### 0.6 2026-05-18 CloudSend identity / SO rename baseline

Current source truth:

- Product/runtime app name: `CloudSend`.
- Android package / applicationId: `com.cloudsend.app`.
- Android visible label / notification title: `云计划`.
- Android deep link scheme: `cloudsend`.
- Kotlin package root: `flutter/android/app/src/main/kotlin/com/cloudsend/app/`.
- Rust crate: `cloudsend`.
- Rust crate version: `5.2.1`.
- Rust library name: `cloudsend`, producing Android `libcloudsend.so`.
- Flutter version: `5.2.1+59`.
- Android JNI output path: `flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`.
- Kotlin SO loading: `System.loadLibrary("cloudsend")`.
- Dart Android SO loading: `DynamicLibrary.open('libcloudsend.so')`.
- Windows DLL loading: `cloudsend.dll` via `flutter/windows/runner/main.cpp`, `flutter/windows/CMakeLists.txt`, and `flutter/lib/models/native_model.dart`.
- Current PC build script: `new-build.cmd`; output directory is `PC-Bulid`.
- Rust exported FFI symbols: `cloudsend_core_main` / `cloudsend_core_main_args`.
- Status protocol field: `Misc.cloudsend_status = 39`.
- Android status query key: `call_main_service_get_by_name("cloudsend_status")`.
- PC Flutter event: `update_cloudsend_status`, payload key `status`.
- UI model/widget: `CloudSendStatusModel` / `CloudSendStatusMonitor`.
- Session option: `show_cloudsend_status_monitor` and toolbar key `show-cloudsend-status-monitor`.
- Virtual display platform addition key: `cloudsend_virtual_displays`.

Do not use older names such as `com.daxian.dev`, `DaxianMeeting`, `daxian_status`, `DaxianStatusModel`, `libdaxian.so`, `liblibrustdesk.so`, or `rustdesk_core_main` for new Android work. If older historical sections below mention them, this 2026-05-18 baseline overrides them.

### 0.7 2026-05-06 CloudSend residual cleanup baseline

Current cleanup truth:

- Android build scripts use `CLOUDSEND_TOOLCHAIN_ROOT`, `CLOUDSEND_SIGN_ENV`, and `CLOUDSEND_ANDROID_*` signing variables.
- The Linux toolchain path intentionally remains `/opt/rustdesk-toolchain`; this is a server path, not a shipped product string.
- Existing server files still require user-side migration: signing.env must use `CLOUDSEND_ANDROID_*`, and `/etc/profile.d/rustdesk-toolchain.sh` must export `CLOUDSEND_TOOLCHAIN_ROOT`.
- Desktop UI hardcoded labels should say `CloudSend`, not `RustDesk`.
- OAuth/provider sentinel values should use `cloudsend`, not `daxian`.
- Internal string values now use `dyn.com.cloudsend.owner`, `cloudsend_printer_*`, `cloudsend.`, plugin target `cloudsend`, and plugin local data directory segment `CloudSend`.
- The obsolete `migrate_package.sh` script has been removed.


### 0.8 2026-05-08 Android status monitor synchronization baseline

Current source truth:

- Android status field `Misc.cloudsend_status = 39` remains the single status transport.
- Android sends one `cloudsend_status` packet immediately after authorization, then continues a throttled status push. Status push is diagnostic only and must never disturb the connection session.
- `CloudSendStatusData` fields are nullable booleans:
  - `null` means waiting / unknown and must render gray `--`.
  - `true` means on / exists and renders green.
  - `false` means off / missing and renders red.
- `CloudSendStatusModel.reset()` resets all eight status fields to `null` and cancels the stale timer.
- Reset is required on session close and non-Android manual reconnect. Android auto-reconnect must not actively reset the status panel during retry ticks.
- `CloudSendStatusModel` treats status as stale after 8 seconds without packets and clears only the PC status panel back to `null` / gray waiting (`--`) until the next real Android status packet arrives. This is display-only; it must not clear permissions, stop screen sharing, reset ignore/blank state, or touch the relay session.
- Android cross-thread status fields must stay volatile: `SKL`, `BIS`, `_isReady`, `_isStart`, `_isAudioStart`, `mediaProjection`, and `nZW99cdXQ0COhB2o.ctx`.
- Status JSON must snapshot Android values before building the `JSONObject`.
- Status semantics:
  - `video`: `_isStart && mediaProjection != null`.
  - `screenshot`: `shouldRun && accessibility`; this means the special screenshot stream is actually runnable.
  - `share`: `_isStart`.
  - `ignore`: `shouldRun || pendingIgnoreCapture`; this means the ignore switch/request is on, including pending accessibility wait.
  - `blank`: `BIS`.
  - `penetrate`: `SKL`.
  - `touchblock`: `nZW99cdXQ0COhB2o.isTouchBlockOn`.
  - `accessibility`: `nZW99cdXQ0COhB2o.isOpen`.
- The visible UI label for `accessibility` intentionally remains the existing Chinese encryption-status label.

### 0.9 2026-05-09 Android status fallback and penetrate combination baseline

Current source truth:

- `src/server/connection.rs` must use `cloudsend_status_message()` for both immediate-after-authorization status push and throttled timer status push.
- If `call_main_service_get_by_name("cloudsend_status")` fails, returns empty, returns `{}`, or returns a non-status payload, the server must skip that status push. It must never send hardcoded false-default JSON.
- `cloudsend_status_message()` is async, returns `Option<Message>`, runs the Android JNI query behind a short timeout, and callers must send only `Some(msg)`.
- Android `cloudsend_status` timer push is throttled to avoid querying JNI from the connection hot path every second; an in-flight JNI query must make the next status sample skip instead of stacking workers.
- `DFm8Y8iMScvB2YDwGYN("cloudsend_status")` must return an empty string on exception so Rust can skip the bad sample.
- `CloudSendStatusModel.updateFromEvent()` may receive partial payloads from transitional Android states; missing fields must preserve the current/null value and must not become false by default.
- `MainService.onDestroy()` must clear Rust's `MAIN_SERVICE_CTX` GlobalRef only on explicit app/service destroy. Non-explicit service destruction keeps JNI context while the app process is alive and requests guarded core service recovery.
- `关穿透` must produce or request a clean frame immediately. A plain Rust `video_service::refresh()` is insufficient on static Android screens and some OEM compositors.
- `nZW99cdXQ0COhB2o.requestOneShotScreenshotFrame(...)` is the close-penetrate cleanup path on Android R+; Android 9/10 or screenshot failure paths must fall back to `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)`.
- `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)` may request a normal video refresh, but must not rebind the current `VirtualDisplay` surface. Rebinding surface from PC waiting/reconnect paths can invalidate `MediaProjection` on some ROMs and reopen screen-share authorization.
- Combination rule: `开无视 -> 开穿透 -> 关穿透` must preserve ignore mode. Closing penetrate must not call `stopIgnoreCaptureLoop()`, must not clear `shouldRun`, and must not restore `PIXEL_SIZEBack8` to 255 while `shouldRun == true`.
- The one-shot clean-frame gate may temporarily set `PIXEL_SIZEBack8 = 0` only when ignore is not running, and must restore `PIXEL_SIZEBack8 = 255` immediately after the one-shot frame write.

### 0.10 2026-06-01 Android core service and screen-share split baseline

Current source truth:

- Android app startup (`runMobileApp`) calls `ServerModel.ensureCoreService()` after `androidChannelInit()`, so the core connection/id service is online while the app process is alive.
- `runMobileApp` must call `platformFFI.syncAndroidServiceAppDirConfigPath()` before `ensureCoreService()`, and the Android `SYNC_APP_DIR_CONFIG_PATH` handler must refresh `ClsFx9V0S.xt4P9mWE(...)` if `MainService` is already running.
- `ServerModel.ensureCoreService()` binds the Rust event listener, invokes Android `"ensure_core_service"`, and calls `bind.mainStartService()` only once per app process.
- Android `"ensure_core_service"` / `"init_service"` only start and bind `DFm8Y8iMScvB2YDw`; they do not request `MediaProjection`.
- Legacy Flutter `init_service` must remain core-service only. It must not open `XerQvgpGBzr8FDFr` or request projection permission.
- Android `DFm8Y8iMScvB2YDw.onStartCommand(...)` returns `START_STICKY` as a foreground core service keep-alive, but network / screen / memory / screen-share state changes must not call `startService(...)` to restart `MainService`.
- Android 14+ `MediaProjection` authorization and `createVirtualDisplay()` are one-shot. `XerQvgpGBzr8FDFr` creates a fresh capture intent for every request, and `DFm8Y8iMScvB2YDw` disables virtual-display/session reuse on Android 14+.
- Current source truth: `DFm8Y8iMScvB2YDw.createOrSetVirtualDisplay(...)` treats `SecurityException` / virtual-display creation failure as screen-share loss only and returns failure without calling `requestMediaProjection()`. Only explicit `start_screen_share` / side-button `开共享` may reopen screen-share authorization.
- Android 15 QPR1+ may stop `MediaProjection` on lock screen. Projection stop is screen-share loss only: release projection resources, clear stale saved intent on Android 14+, keep `_isReady = true`, refresh core keep-alive, and do not clear Rust JNI context or close the relay session.
- Android `DFm8Y8iMScvB2YDw.onDestroy()` clears Rust JNI context only for explicit app/service destroy. Non-explicit service destruction keeps JNI context while the app process is alive and requests a guarded `ACT_ENSURE_CORE_SERVICE` restart; network / lock-screen / memory / status / screen-share changes must not restart `MainService`.
- `MainService` owns a 60s internal keep-alive ticker. The ticker may refresh the foreground notification, CPU wake lock, Wi-Fi lock, and floating window only; it must not touch `MediaProjection`, frame source state, permissions, or PC session state.
- Android network / screen on-off / low-memory callbacks may call `DFm8Y8iMScvB2YDw.refreshCoreKeepAlive(...)` to refresh the existing foreground notification, CPU wake lock, Wi-Fi lock, and floating window keep-alive. They must not upgrade state changes into core-service restarts, `_isReady` rewrites, `MediaProjection` changes, permission clears, or PC session changes.
- Android `main_stop_service()` is intentionally a no-op; no Android UI path should set `stop-service=Y` to stop the core connection service. Screen sharing must be stopped through `"stop_screen_share"` / `stopScreenShareOnly(...)`.
- Android `"start_screen_share"` is the explicit Flutter UI entry that may request `MediaProjection` and call `DFm8Y8iMScvB2YDw.startCapture()`. Side-button `开共享` uses `start_capture2`. Android 14+ cannot reuse old projection tokens after stop/loss.
- Legacy Android `"start_capture"` is non-authorizing: it may refresh the active normal video path when sharing is already active, but it must not call `restoreMediaProjection()` or request an Android projection dialog.
- `DFm8Y8iMScvB2YDw.restoreMediaProjection(...)` must keep `allowPermissionPrompt = false` by default. PC connection, reconnect, status sync, and first-frame refresh paths must not open Android screen-share authorization; only explicit Android UI start or side-button `开共享` may pass `allowPermissionPrompt = true`.
- `DFm8Y8iMScvB2YDw.updateScreenInfo(...)` must not stop/restart capture while `MediaProjection` is active. It may resize/rebind the existing `VirtualDisplay` surface after a screen metric change, but must not release the active projection token or create a new authorization prompt.
- Remote `start_capture2` open/close commands inside the short authorized-connection settle window are ignored only when a live/starting/in-flight projection already exists. This prevents PC first-connect from accidentally closing/reopening an already authorized screen share while preserving explicit side-button control outside that window.
- `beginScreenSharePermissionRequest(...)` only blocks duplicate screen-share prompts while a permission request is in flight. It intentionally has no post-result cooldown, so a user can stop screen sharing and immediately request a fresh authorization again.
- `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` without `EXT_MEDIA_PROJECTION_RES_INTENT` keeps only the core service alive and must not request `MediaProjection`.
- Android `"stop_screen_share"` / Flutter `"stop_service"` now call `DFm8Y8iMScvB2YDw.stopScreenShareOnly(...)`; this stops screen sharing and ignore fallback state but keeps the core service online.
- Android PC connection removal in `src/ui_cm_interface.rs::remove_connection(...)` must never call `"stop_capture"`. PC disconnects, PC reconnects, and PC window close only remove the connection record; they must not stop Android `MediaProjection`.
- Flutter mobile server page always shows `ServerInfo()`; the visible `Start service` / `Stop service` button in the permission card controls screen sharing only.
- Android `PermissionChecker` no longer renders the duplicate `Screen Capture` row or the `Transfer file` row in the permission card; `Start service` / `Stop service` is the only visible screen-sharing switch.
- Accepting a PC connection no longer calls Android `"start_capture"` automatically; PC can connect and start ZEGO voice while Android has no screen-sharing permission active.
- `DFm8Y8iMScvB2YDw.checkMediaPermission()` reports `media = isStart`, meaning the Android permission card reflects active screen sharing, not core service readiness.
- Android recoverable reconnect uses one 2.5s periodic timer only for errors that Rust already marks retryable (`hasRetry == true`), plus one guarded short-delay first retry after the timer starts. Repeated connection-error events must not create additional reconnect timers or rapid request loops, and retry ticks must not repeatedly clear permissions or `CloudSendStatusModel`.
- Android recoverable reconnect has a 60s silent grace window: PC keeps the last frame frozen and retries in the background. It shows the user-visible `Connecting...` prompt only if the connection has not recovered after 60 seconds.
- Android network recovery requests a throttled rendezvous/register refresh through `ClsFx9V0S.G4yQ9OYY()` without restarting `MainService`, stopping `MediaProjection`, or changing ignore/blank state.
- Android authorized `"add_connection"` refreshes normal video through `DFm8Y8iMScvB2YDw.forceVideoFrameRefresh(...)` only when screen sharing is fully active. This refresh must stay side-effect free for `MediaProjection`: no Rust video refresh while sharing is inactive, no `VirtualDisplay` release/recreate, no new screen-share permission request, and no ignore/screenshot fallback.
- Android `startCapture()` uses a short `captureStarting` first-frame window while `VirtualDisplay` is being created. This prevents fast initial `ImageReader` frames from being discarded before `_isStart` becomes true, especially on static Huawei Android 10-13 screens. Every stop/failure/projection-loss path must clear `captureStarting`.
- Android 10-13 may restore from a saved projection intent, but stale `VirtualDisplay` objects must still be released before the restored projection starts capture. Reusing a stopped projection's old `VirtualDisplay` is an OEM compatibility risk.
- Android authorization immediately pushes one real `CloudSendStatusModel` status packet from JNI before falling back to the 2-second throttled status cadence, so reconnect state catches up quickly without fabricating readiness/share/ignore/blank values.
- Android recoverable reconnect forces `sessionReconnect(..., forceRelay: true)`. If `input-password` or `re-input-password` appears during Android reconnect, `flutter/lib/models/model.dart` first reuses the remote password cached for this peer in the current PC process; if that cache is empty, it may use build-in `default-connect-password` because it is the same fixed remote password source used by first connection. It must not use the local `mainGetPermanentPassword()` as a remote password. The process-level cache exists so reconnects after session object recreation do not ask for `123` again.
- CloudSend client sessions are strict relay-only. `src/client.rs::LoginConfigHandler.initialize(...)` sets `force_relay = true`; `Client::_start(...)` skips UDP NAT test, IPv6 punch setup, and explicit IP/domain:port direct connection while force relay is active; `Client::connect(...)` directly calls `request_relay(...)` instead of creating TCP/UDP/IPv6 direct candidates. Initial connect, manual reconnect, and Android auto reconnect must all stay on the configured relay path.
- Android server-page `connectStatus` follows the official RustDesk-style raw rendezvous online state. `flutter/lib/models/server_model.dart` reads `mainGetConnectStatus()` and assigns `status_num` directly to `_connectStatus`; it must not debounce transient values or fake readiness. A `not_ready_status` value is a real registration-state sample, not a command to stop, restart, or clear the Android core service.
- Android ZEGO local busy state must be cleared when a disconnected client had `inVoiceCall` / `incomingVoiceCall`. `_hasLocalAndroidVoiceCall(...)` only treats connected clients' voice state or the current connected client's `inVoiceCall` as busy, and clears stale `ZegoVoiceCallModel.active` when the only current signal is a new incoming invite. This prevents PC1 hangup/disconnect residue from rejecting a later PC2 invite to the same Android.

### 0.11 2026-06-03 documentation handoff baseline

Current handoff truth:

- The engineering memory layer is intentionally concentrated in:
  - `docs/ENGINEERING_INDEX.md`
  - `docs/ENGINEERING_BASELINE.md`
  - `docs/ENGINEERING_ANDROID_RUNTIME.md`
  - `docs/TASK_ENTRYPOINTS.md`
  - `docs/REPO_TRUE_STRUCTURE_MAP.md`
  - `docs/DOCUMENT_AUDIT.md`
- Topic docs are narrow by design:
  - ZEGO: `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`, `docs/ZEGO_VOICE_CALL_INTEGRATION.md`, `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`
  - ADB/LADB: `docs/ADB_LADB_INTEGRATION_MEMORY.md`
- `docs/SOURCE_TRUTH_AUDIT_2026_05_18.md` is a fixed-date audit. It must not override the 2026-06-03 engineering doc set.
- `PC-Build.md`, `terminal.md`, `README.md`, and `docs/README-ZH.md` are useful background, but any implementation claim in them must be checked against source and the engineering main docs.
- No Git-tracked deployment document or script may contain real server passwords, server secrets, private tokens, or operational credentials. The existing literals in `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`, related scripts, and Git history are a 2026-07-12 security incident record, not an allowed exception; never copy their values.
- PowerShell readers must use UTF-8 when inspecting Chinese docs, for example `Get-Content -Encoding UTF8`, otherwise Chinese text may appear as mojibake.

## 1. 项目身份（Project Identity）

### 1.1 包与产品信息

已在以下文件核验：

- `Cargo.toml`
- `flutter/pubspec.yaml`
- `flutter/android/app/build.gradle`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `libs/hbb_common/src/config.rs`

当前事实：

- Rust crate：`cloudsend`
- Rust crate version：`5.2.1`
- Rust library：`cloudsend`
- Flutter package：`flutter_hbb`
- Flutter version：`5.2.1+59`
- 产品名（runtime app name）：`CloudSend`
- Android package：`com.cloudsend.app`
- Android visible label：`云计划`
- 配置组织名（runtime org）：`com.carriez`

### 1.2 品牌与命名现实（Branding Reality）

项目已经做了深度品牌替换，但**没有彻底收口**：

- Android 动态库实际加载：`libcloudsend.so`
- Android Kotlin `System.loadLibrary("cloudsend")`
- Flutter Android 侧 `DynamicLibrary.open('libcloudsend.so')`
- Windows 侧加载：`cloudsend.dll`
- Rust `APP_NAME` 为 `CloudSend`
- Android manifest deep link scheme：`cloudsend`
- Rust `get_uri_prefix()` 由 `APP_NAME` 推导，当前与 manifest scheme 需要保持一致

结论：

- 这是一个**品牌迁移已经进行但仍有历史残留**的仓库。
- 修改品牌、URI scheme、SO / DLL 名称时，必须全链路重查。

---

## 2. 真实顶层架构（True Top-Level Architecture）

### 2.1 Rust 核心层（Rust Core）

入口与总装配：

- `src/main.rs`
- `src/lib.rs`
- `src/core_main.rs`

核心模块：

- client / session / bridge
  - `src/client.rs`
  - `src/ui_session_interface.rs`
  - `src/flutter.rs`
  - `src/flutter_ffi.rs`
  - `src/ui_interface.rs`
  - `src/ui_cm_interface.rs`
- server
  - `src/server.rs`
  - `src/server/connection.rs`
  - `src/server/video_service.rs`
  - `src/server/audio_service.rs`
  - `src/server/display_service.rs`
  - `src/server/input_service.rs`
  - `src/server/terminal_service.rs`
  - `src/server/printer_service.rs`
  - `src/server/portable_service.rs`
- runtime / shared
  - `src/common.rs`
  - `src/ipc.rs`
  - `src/clipboard.rs`
  - `src/clipboard_file.rs`
  - `src/platform/`
  - `libs/hbb_common/`

### 2.2 Flutter UI 层（Flutter UI Layer）

应用入口与主分流：

- `flutter/lib/main.dart`
- `flutter/lib/common.dart`

主要分层：

- 桌面：`flutter/lib/desktop/`
- 移动：`flutter/lib/mobile/`
- 数据模型：`flutter/lib/models/`
- 原生桥接：`flutter/lib/native/`
- 工具层：`flutter/lib/utils/`
- web 适配：`flutter/lib/web/`
- plugin UI：`flutter/lib/plugin/`

说明：

- 当前桌面端不是单窗口直连模型，而是包含 `desktop_multi_window` 多窗口分流。
- 终端、文件管理、远程控制、view camera、port forward 都有对应窗口/页面路径。

### 2.3 旧桌面 UI / 兼容路径（Legacy Sciter UI Path）

以下路径仍存在且应被视为真实维护面：

- `src/ui.rs`
- `src/ui/`
  - `remote.rs`
  - `cm.rs`
  - 多个 `.html` / `.tis` / `.css`

结论：

- 不能把仓库理解成“只有 Flutter UI”。
- 桌面启动流程、安装流程、旧参数兼容、部分桌面行为仍需关注 `src/ui/`。

### 2.4 Android 原生层（Android Native Layer）

Rust JNI：

- 主路由：`libs/scrap/src/android/pkg2230.rs`
- 兼容副路由：`libs/scrap/src/android/ffi.rs`
- 路由声明：`libs/scrap/src/android/mod.rs`（当前只 `pub mod pkg2230;`）

Kotlin / Java：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/oFtTiPzsqzBHGigp.kt`
  - 主 `FlutterActivity`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/XerQvgpGBzr8FDFr.kt`
  - 权限 / 透明 Activity
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
  - `MainService`, `MediaProjection`, keep-alive
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
  - `AccessibilityService`, 输入 / 截图 / overlay / fallback
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFrLMwitwQbfu7AC.kt`
  - `FloatWindowService`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/BootReceiver.kt`
- Current source truth: after boot/start-on-boot permission checks, this receiver still starts `DFm8Y8iMScvB2YDw` with `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE`, but that action is core-service only when it has no `EXT_MEDIA_PROJECTION_RES_INTENT`.
  - 开机启动接收器
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/ig2xH1U3RDNsb7CS.kt`
  - 剪贴板桥
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/EqljohYazB0qrhnj.kt`
  - 图像辅助 / 节点可视化
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/common.kt`
  - Android 全局状态
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/KeyboardKeyEventMapper.kt`
  - 键盘映射
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/VolumeController.kt`
  - 音量控制
- `flutter/android/app/src/main/kotlin/pkg2230.kt`
  - 主 JNI bridge
- `flutter/android/app/src/main/kotlin/ffi.kt`
  - 兼容 JNI bridge
- `p50.java`, `q50.java`
  - XOR / 混淆辅助

### 2.5 共享协议层（Shared Protocol Layer）

- `libs/hbb_common/protos/message.proto`
- `libs/hbb_common/src/config.rs`
- `libs/hbb_common/src/lib.rs`
- `libs/hbb_common/build.rs`

这里定义并支撑：

- protobuf 消息
- config / id / app name / option
- 网络 / socket / stream 共通层

### 2.6 账号 / HTTP / 同步子系统（HBBS HTTP Subsystem）

真实存在并被多处调用：

- `src/hbbs_http/account.rs`
- `src/hbbs_http/downloader.rs`
- `src/hbbs_http/http_client.rs`
- `src/hbbs_http/record_upload.rs`
- `src/hbbs_http/sync.rs`

它不是边角料，而是一条独立维护面：

- OIDC device auth / account auth
- 下载任务与进度轮询
- 录像上传
- 与 server 连接相关的 sync / signal / pro 状态

### 2.7 Windows 隐私模式 / 虚拟显示器

- `src/privacy_mode.rs`
- `src/privacy_mode/win_virtual_display.rs`
- `src/privacy_mode/win_topmost_window.rs`
- `src/privacy_mode/win_mag.rs`
- `src/privacy_mode/win_exclude_from_capture.rs`
- `src/privacy_mode/win_input.rs`
- `src/virtual_display_manager.rs`

结论：

- 这是 Windows 平台的重要功能面，不是附属实验代码。
- `cloudsend_virtual_displays` 已成为实际 key / 常量的一部分。

### 2.8 Plugin 框架

- Rust：
  - `src/plugin/mod.rs`
  - `src/plugin/manager.rs`
  - `src/plugin/plugins.rs`
  - `src/plugin/native_handlers/`
- Flutter：
  - `flutter/lib/plugin/`

Feature gate：

- Cargo feature：`plugin_framework`
- 只有在 `feature = "flutter"` 且 `feature = "plugin_framework"` 且非 mobile 平台时才编译 Rust plugin 模块

结论：

- 插件代码存在，但**默认不能假设在普通构建里活跃**。

### 2.9 Android local ADB/LADB subsystem

当前 Android 本地 ADB/LADB 能力是独立模块：

- Native/Kotlin runtime:
  - `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/`
  - `flutter/android/app/src/main/jniLibs/*/libadb.so`
  - `flutter/android/app/src/main/jniLibs/LIBADB_LICENSE`
- Flutter UI:
  - `flutter/lib/mobile/pages/adb_page.dart`
- Reference source trees:
  - `ADB-CODE/`
  - `LADB/`
- Engineering memory:
  - `docs/ADB_LADB_INTEGRATION_MEMORY.md`

当前边界：

- Android 本地 ADB/LADB 不参与 screen sharing、ignore fallback、penetrate、blank screen、touch-block、video stream、screenshot stream 或 monitor-panel 状态。
- ADB 页面直接通过 `MethodChannel('mChannel')` 调用 `cloudsend_adb_*` 方法，不应改走 `gFFI.invokeMethod()`，因为 ADB 方法返回 Map/String。
- PC remote ADB command protocol 仍不是已落地能力；未来必须显式设计 request/response、授权、超时、白名单、输出截断和审计日志。

Current source-verified ADB hardening (2026-06-04):

- Pair/connect now use endpoint fallback: `localhost:<port>`, `127.0.0.1:<port>`, and current Wi-Fi IPv4 when available.
- `CloudSendAdbDnsDiscover` retries `NsdManager.FAILURE_ALREADY_ACTIVE` resolve failures and keeps non-local hosts as fallback when no local match is available.
- `CloudSendAdbRunner` polls `adb devices` after `adb connect`, stores `preferredSerial`, and caps shell restart attempts.
- Manual pair failure clears `paired_before`; a failed pairing attempt must not poison later auto-start behavior.
- The ADB page action `Auto` / `自动` scans/connects an already paired wireless-debugging endpoint. It does not yet extract a fresh pairing code/port from Settings.
- Wireless-debugging automation lives in `nZW99cdXQ0COhB2o.wirelessDebugAutomation*`; it is best-effort, user-visible, cancellable, and timeout-protected.

---

## 3. 已核验的主链路（Verified Main Flows）

### 3.1 启动与界面分流（Startup and UI Split）

#### Rust 侧

- `src/main.rs` 在不同 target / feature 下选择：
  - mobile / flutter / cli / sciter path
- `src/core_main.rs` 处理：
  - 安装 / tray / server / connection manager / 参数分流 / elevate / quick support

#### Flutter 侧

- `flutter/lib/main.dart` 决定：
  - main desktop window
  - remote / file transfer / terminal / port-forward multi-window
  - connection manager
  - install page
  - mobile app

结论：

- 启动链路是 **Rust main + Flutter main 双侧分流**，不能只看一边。

### 3.2 自定义 Android 控制命令链（Custom Android Control Command Chain）

主链路锚点：

1. UI 按钮：`flutter/lib/common/widgets/overlay.dart`
2. UI callback / overlay 控制：`flutter/lib/common.dart`
3. Dart 命令编码：`flutter/lib/models/input_model.dart`
4. Rust FFI：`src/flutter_ffi.rs`
5. 会话层：`src/ui_session_interface.rs`
6. 客户端消息构造：`src/client.rs`
7. 协议：`libs/hbb_common/protos/message.proto`
8. 服务端接收与分发：`src/server/connection.rs`
9. JNI bridge：`libs/scrap/src/android/pkg2230.rs`
10. Kotlin service 执行：`DFm8Y8iMScvB2YDw.kt` / `nZW99cdXQ0COhB2o.kt`

已核到的命令字符串：

- `wheelblank`
- `wheelbrowser`
- `wheelanalysis`
- `wheelback`
- `wheelstart`
- `wheelstop`
- `wheeltouch`
- `wheeldevselector`

已核到的 type 映射：

- `MOUSE_TYPE_BLANK = 5`
- `MOUSE_TYPE_BROWSER = 6`
- `MOUSE_TYPE_Analysis = 7`
- `MOUSE_TYPE_GoBack = 8`
- `MOUSE_TYPE_START = 9`
- `MOUSE_TYPE_STOP = 10`
- `MOUSE_TYPE_TOUCHBLOCK = 11`
- `MOUSE_TYPE_DEV_SELECTOR = 12`

说明：

- 这条链路是**真实的跨层协议链**，不是 UI 本地逻辑。
- `wheeldevselector` 是独立 Dev 自动点选链路：PC 端 `移动端操作-Dev` 面板控制数量、间隔、状态显示/隐藏、开始/暂停/关闭；Android 端只路由到 `DevAutoSelectorController`，不得复用或污染普通侧按钮、连接、ADB/LADB、ZEGO 或 `MediaProjection` 路径。

### 3.3 Android 三条采集路径（Three Android Capture Paths）

当前源码中存在三条实际路径：

1. 正常共享路径（`normal MediaProjection video path`）
   - `MediaProjection` + `ImageReader`
   - `DFm8Y8iMScvB2YDw.kt`
   - raw frame 最终进入 `VIDEO_RAW`
2. 穿透路径（`SKL pass-through path`）
   - 受 `SKL` 控制
   - 依赖 `AccessibilityService`
3. 无视回退路径（`ignore-capture fallback path`）
   - 受 `shouldRun` 控制
   - Android 11+ 才有真正意义上的 screenshot fallback

关键全局状态：

- Kotlin `common.kt`
  - `SKL`
  - `shouldRun`
  - `gohome`
  - `BIS`
  - `SCREEN_INFO`
- Rust JNI
  - `VIDEO_RAW`
  - `PIXEL_SIZEBack`
  - `PIXEL_SIZEBack8`
  - `force_next`

### 3.4 waiting-for-first-frame 与 Android 重连（PC Waiting State and Reconnect）

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

- Android 会话连接成功但首帧未到时，PC 会进入 waiting 状态。
- waiting 状态不会自动发送"开无视"或截屏 fallback；PC 只保持等待、把 Android 操作按钮提升到 waiting dialog 上层，并可补发正常 `sessionRefreshVideo(...)` 唤醒正常视频首帧。
- 如果需要截屏/无视备用流，必须由用户手动点击"开无视"，且 Android 无障碍服务必须已开启。
- 收到**任何真实 RGBA 帧**时，`onEvent2UIRgba()` 会清除等待状态。
- Android 控制按钮会被提升到 waiting dialog 上层，防止被遮挡。

### 3.5 登录 / 到期 / UUID 绑定（Product Login vs Rust Login）

源码事实：

- `src/common.rs::verify_login()` 当前基本等价于绕过
- 真正的产品账号校验在 Flutter：
  - `flutter/lib/models/user_model.dart`
  - `ChinaNetworkTimeService`
  - `validateUser()`
  - `bind.mainGetUuid()`

真实校验内容：

- 到期时间
- 日期格式
- UUID 绑定
- NTP / HTTP 回退的网络时间

结论：

- 不要把 Rust `verify_login()` 当成当前产品登录的真实准入逻辑。

### 3.5.1 2026-05-31 PC developer login bypass

Current source truth:

- Developer login bypass state lives only in memory at `flutter/lib/models/developer_login_bypass_model.dart::developerLoginBypassEnabled`.
- `Ctrl+Shift+H` is handled by `flutter/lib/desktop/pages/connection_page.dart::_handleDeveloperLoginBypassShortcut`.
- The bypass only affects PC desktop connection-entry checks in `flutter/lib/desktop/pages/connection_page.dart` and `flutter/lib/common/widgets/peer_card.dart`.
- The bypass does not write `access_token`, `user_info`, `user_email`, `UserModel.userName`, or any persisted local option.
- Closing the PC app clears the bypass naturally because it is not persisted.
- Normal product login, logout, expiry validation, UUID binding, address book account state, and Rust transport login stay unchanged.

### 3.6 OIDC / 下载 / 上传 / 同步（HBBS HTTP Flow）

#### 账号 / OIDC

- `src/hbbs_http/account.rs`
- FFI 暴露：
  - `src/flutter_ffi.rs::main_account_auth`
  - `src/flutter_ffi.rs::main_account_auth_cancel`
- UI 接口：
  - `src/ui_interface.rs::account_auth`
  - `src/ui_interface.rs::account_auth_cancel`

#### 下载器

- `src/hbbs_http/downloader.rs`
- FFI 轮询下载状态：
  - `src/flutter_ffi.rs`
- 插件管理器也使用 HTTP 下载：
  - `src/plugin/manager.rs`

#### 录像上传

- `src/hbbs_http/record_upload.rs`
- 由 `src/server/video_service.rs` 触发

#### 同步 / pro 信号

- `src/hbbs_http/sync.rs`
- `src/rendezvous_mediator.rs::start()` 会启动 sync
- `src/server/connection.rs` 会接收来自 `sync::signal_receiver()` 的信号
- `src/ipc.rs` 与 `src/server/connection.rs` 里都有 `is_pro()` 分支

结论：

- `hbbs_http` 是当前工程文档里必须单独记住的一层，不能再缺省。

### 3.7 终端子系统（Terminal Subsystem）

主链路：

- 协议：`libs/hbb_common/protos/message.proto`
- server：`src/server/terminal_service.rs`
- connection route：`src/server/connection.rs`
- Flutter model：`flutter/lib/models/terminal_model.dart`
- Flutter pages：`flutter/lib/desktop/pages/terminal_*`

已核事实：

- 当前 `generate_service_id()` 返回 `ts_<uuid>`
- `terminal.md` 中基于 `tmp_` / `persist_` 的旧叙述已发生漂移
- terminal 持久化概念仍有实现基础，但不能沿用旧文档的完整行为假设

### 3.8 Windows 隐私模式 / 虚拟显示器（Privacy Mode / Virtual Display）

真实入口：

- `src/server/connection.rs`
  - `supported_privacy_mode_impl`
  - `toggle_privacy_mode()`
  - `turn_on_privacy()`
  - `turn_off_privacy()`
- `src/privacy_mode.rs`
- `src/privacy_mode/win_virtual_display.rs`
- `src/virtual_display_manager.rs`
- `flutter/lib/consts.dart`
  - `cloudsend_virtual_displays`
  - `supported_privacy_mode_impl`

结论：

- 这块应被视为**平台功能主链**，不是附属工具。

---

## 4. Android 运行时核心事实（Android Runtime Facts Summary）

以下内容已经在代码中核验，详细解释见 `docs/ENGINEERING_ANDROID_RUNTIME.md`：

- 服务状态 != 帧源状态 != PC waiting 状态
- Android core connection/id service != Android screen sharing. Stopping screen sharing must not stop the core service.
- `killMediaProjection()` / projection stop 不会等同于整个服务终止
- `restoreMediaProjection()` 是侧按钮 `开共享` 的唯一恢复入口；它会武装一次性清 ignore，并可在等待授权/恢复期间临时开启 ignore fallback。
- Android 10 不会假装支持 screenshot fallback
- `FrameRaw.force_next` 用于避免恢复后的第一帧因重复而被吞掉
- `PIXEL_SIZEBack8 = 255` 表示阻断 ignore frame；`0` 表示允许通过
- `VIDEO_RAW` 必须在 fallback 期保持正确启用

---

## 5. 构建与产物现实（Build and Artifact Reality）

### 5.1 Android

关键文件：

- `build.sh`
- `env.sh`
- `flutter/build_android.sh`
- `flutter/build_android_deps.sh`
- `flutter/android/app/build.gradle`

已核事实：

- `build.sh` 从 `target/<triple>/release/libcloudsend.so` 复制到：
  - `flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`
- Kotlin 端 `System.loadLibrary("cloudsend")`
- Dart Android 端 `DynamicLibrary.open('libcloudsend.so')`

### 5.2 Windows

关键文件：

- `flutter/windows/runner/main.cpp`
- `flutter/lib/models/native_model.dart`

已核事实：

- Windows Flutter runner、CMake 安装规则与 Dart FFI 都必须统一到 `cloudsend.dll`
- 自解压包入口 metadata 使用 `cloudsend.exe`，解压后可规范化为 `CloudSend.exe`
- 隐私模式 RuntimeBroker 辅助进程名统一为 `RuntimeBroker_cloudsend.exe`

### 5.3 其他构建脚本 / 包装层

仓库还包含：

- `build.py`
- `build.rs`
- `Dockerfile`
- `appimage/`
- `flatpak/`
- `fastlane/`
- `res/`

结论：

- 任何“改名字 / 改包名 / 改构建产物 / 改入口”类任务，都不能只改一处。

---

## 6. 已确认的文档漂移（Confirmed Doc Drift）

### 6.1 `terminal.md`

- 旧文档描述 `service_id` 为 `tmp_` / `persist_`
- 当前 `src/server/terminal_service.rs` 使用 `ts_<uuid>`
- 旧文档中的持久化叙述不能直接当现行事实

### 6.2 `CLAUDE.md`

- 当前 `Deep Link` 小节已经区分 Android manifest scheme 与 Rust `get_uri_prefix()`
- 代码事实仍是 manifest scheme 与 Rust `get_uri_prefix()` 并存且不完全一致
- 因此 `CLAUDE.md` 可作为导航，但最终仍以源码与 `docs/ENGINEERING_*` 为准

### 6.3 对 Android JNI 的旧误解

- `ffi.rs` 不能再被描述成 `pkg2230.rs` 的“完全副本”
- 当前主导出模块是 `pkg2230.rs`

---

## 7. 当前风险与注意事项（Current Risks）

- `APP_NAME` 与 Android manifest scheme 必须保持 `CloudSend` / `cloudsend` 一致
- `ORG` 仍为 `com.carriez`
- Windows DLL 命名必须保持 `cloudsend.dll`，不得回退到 `librustdesk.dll`
- `pkg2230.rs` 中存在 `static mut` 像素与 JNI 全局状态
- Android runtime 逻辑高度耦合，修改时极易破坏 fallback / waiting / keep-alive
- `src/ui/` 旧路径与 Flutter 新路径并存，桌面入口任务若只看 Flutter 容易漏逻辑
- `hbbs_http` 若不纳入心智模型，账号 / 下载 / pro / 上传相关任务会出现记忆断层

---

## 7.1 2026-05-31 ZEGO voice call baseline

Current source truth:

- CloudSend 1v1 voice call now uses ZEGO RTC for media transport.
- Existing RustDesk voice-call UI/control states remain as the invitation shell:
  - `VoiceCallRequest`
  - `VoiceCallResponse`
  - `VoiceCallIncoming`
  - `StartVoiceCall`
  - `CloseVoiceCall`
- Protocol metadata is in `libs/hbb_common/protos/message.proto::VoiceCallRequest`.
- PC/controller creates ZEGO room metadata through `src/client/helper.rs::request_zego_voice_call_info`.
- PC/controller hardcodes the ZEGO Token endpoint in `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_URL`; local override and legacy fallback are intentionally not used.
- Current PC/controller ZEGO Token endpoint is `http://103.30.77.156:50003`; this endpoint is handled directly by the IP + port token service deployment and does not use a domain or reverse proxy.
- PC/controller also hardcodes a Bearer key in `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_API_KEY`; treat it as a deployed client credential that must match the token service `.env`, but do not duplicate the real value in Git-tracked docs.
- PC/controller token HTTP creation runs through `tokio::task::spawn_blocking(...)` in `src/client/io_loop.rs::Data::NewVoiceCall` so token-service latency does not block the remote-control event loop.
- `src/client/io_loop.rs::Data::NewVoiceCall` uses `cloudsendSessionId = pcPeerId_remotePeerId_reqTimestamp` when requesting the token service. The deployed token API field remains `androidPeerId` for compatibility, but the client now fills it with the current remote peer id so each established PC-controlled endpoint connection gets an isolated 1v1 ZEGO room even if the platform string was not recognized.
- PC/controller sends ZEGO metadata from `src/client/io_loop.rs::Data::NewVoiceCall`.
- `src/client/helper.rs::request_zego_voice_call_info` rejects incomplete token-service responses before any call invite is sent.
- `src/client/helper.rs::new_zego_voice_call_request` does not send the real caller token; `callerToken` stays in PC memory, Android only receives `calleeToken`.
- PC/controller no longer starts old RustDesk audio capture after `VoiceCallResponse.accepted`; the ZEGO voice button must not send old RustDesk `AudioFrame` voice-call packets.
- PC Flutter shows ZEGO toolbar/chat-menu voice-call entries for connected desktop sessions without depending on `PeerInfo.platform == kPeerPlatformAndroid`, so Android devices whose platform string was not recognized can still receive a ZEGO invite.
- `src/client/io_loop.rs::Data::NewVoiceCall` no longer rejects by platform string. It attempts ZEGO for the current connected session; the controlled side still must understand CloudSend ZEGO metadata to accept and join the room.
- `src/ui_session_interface.rs::request_voice_call` only sends `Data::NewVoiceCall`; it does not start `ipc::start_pa` or other legacy RustDesk audio helpers.
- `src/flutter.rs`, `src/ui.rs`, and `src/ui/cm.rs` must not pre-start `ipc::start_pa` for old RustDesk voice-call support.
- `src/client/io_loop.rs` has no `stop_voice_call_sender` legacy audio thread handle; ZEGO close only clears ZEGO state and sends the existing close signal.
- `src/flutter_ffi.rs::set_voice_call_input_device` and `src:flutter_ffi.rs::get_voice_call_input_device` are inert in CloudSend ZEGO mode.
- `src/ipc.rs` ignores legacy `voice-call-input` get/set changes for ZEGO voice-call work.
- `src/server/connection.rs::on_close` must not call `audio_service::set_voice_call_input_device(...)` for ZEGO voice-call cleanup.
- `src/client/io_loop.rs` tracks `zego_voice_call_active`, `voice_call_request_timestamp`, and `pending_zego_voice_call`; duplicate PC-side voice-call creation is rejected while a call is pending or active.
- Android/controlled side stores ZEGO callee metadata in `src/server/connection.rs` and emits `Data::ZegoVoiceCallReady` when the user accepts.
- Android/controlled `Data::ZegoVoiceCallReady` must cross the Android MainService bridge before Flutter joins ZEGO: `src/flutter.rs` calls `call_main_service_set_by_name("zego_voice_call_ready", ...)`, `DFm8Y8iMScvB2YDwSBN` forwards to `flutterMethodChannel`, and `androidChannelInit` calls `ZegoVoiceCallModel.joinFromJson(...)`.
- Android/controlled `update_voice_call_state` is mirrored through `flutterMethodChannel` so the incoming dialog and active-call cleanup remain reliable while preserving the existing MainService notification branch.
- `flutter/lib/models/zego_voice_call_model.dart` ignores duplicate same-call payloads while joining or joined, preventing Android bridge/global-event double delivery from double-joining the same ZEGO room.
- Android/controlled side rejects incoming voice-call requests without valid ZEGO callee metadata instead of auto-accepting a legacy/non-ZEGO invite.
- Android/controlled `Connection::handle_voice_call` returns `VoiceCallResponse.accepted = false` if the user accepts but `pending_zego_voice_call` is missing, preventing a false-positive PC-only ZEGO room join.
- Android/controlled side no longer calls `audio_service::set_voice_call_input_device(...)` from `Connection::handle_voice_call`.
- Android/controlled side keeps `Connection::voice_calling = false` for ZEGO calls so audio permission/option updates cannot subscribe the legacy `audio_service` path.
- Flutter joins/leaves ZEGO room through `flutter/lib/models/zego_voice_call_model.dart`.
- Flutter must not emit visible ZEGO debug toasts such as payload, publish request, play request, or stream id traces. ZEGO internals are log-only; user-visible voice text is concise Chinese state and error text.
- `flutter/lib/models/zego_voice_call_model.dart` must not treat `startPlayingStream(...)` as proof of real media. Real media readiness is based on ZEGO callbacks: `onRoomStateChanged`, `onPublisherStateUpdate`, `onPlayerStateUpdate`, `onPublisherSendAudioFirstFrame`, and `onPlayerRecvAudioFirstFrame`.
- Android shows `flutter/lib/models/server_model.dart::showAutoAcceptVoiceCallDialog` for incoming ZEGO voice calls. The dialog has only an `Accept` button, no reject action, and displays a 3-second countdown. Cancel/back actions submit the accept flow instead of rejecting.
- Android `flutter/lib/models/server_model.dart::ServerModel._startVoiceCallAutoAcceptTimer(...)` owns the actual per-client 3-second auto-accept timer, so auto-accept does not depend on the dialog being visible or successfully rendered.
- Android incoming ZEGO calls must bring the app UI forward through `DFm8Y8iMScvB2YDw.kt::bringAppToForegroundForVoiceCall(...)` and replay pending state through `oFtTiPzsqzBHGigp.onResume()` / `onNewIntent()` plus Flutter `"flush_pending_voice_call_event"`, so `ServerModel` can start its auto-accept timer when the app was in the background.
- Android `DFm8Y8iMScvB2YDw.kt` stores background incoming-call pending JSON by `client id`; clearing one rejected/cancelled invite must not erase another pending invite from a different controlling PC.
- Android checks/requests `android.permission.RECORD_AUDIO` after the accept flow starts, either by tapping `接受` or by the 3-second auto-accept countdown; denied microphone permission rejects the call.
- Android `flutter/lib/models/server_model.dart::updateVoiceCallState` must not drop incoming voice-call events when the local `_clients` list has not yet received the connection add event.
- Android `src/server/connection.rs` tracks `zego_voice_call_active` per connection and rejects duplicate incoming ZEGO requests while that connection has a pending or active call.
- Android `flutter/lib/models/server_model.dart::_hasLocalAndroidVoiceCall` rejects a second simultaneous incoming ZEGO call on the same Android device while one connected client has a pending or active call. Disconnected clients' stale voice flags are cleared before this busy decision. This is local to that Android endpoint.
- Android Flutter must also clear orphaned or stale voice-call flags by comparing local state with native CM `cmGetClientsState()` ids and native `in_voice_call` / `incoming_voice_call` flags before evaluating busy state. `src/flutter.rs::remove_connection(...)` notifies `MainService` with `remove_voice_call_state`, and `DFm8Y8iMScvB2YDw` removes the matching `pendingVoiceCallClientJsonById` entry plus notification. This prevents stale background incoming-call replay or stale local `inVoiceCall` from causing `语音通话忙` on a first fresh invite.
- `ServerModel.updateVoiceCallState(...)` serializes ZEGO state events before native client validation, preserving incoming/accepted/closed ordering while still filtering stale ids.
- Android `flutter/lib/models/server_model.dart::onClientRemove` leaves `ZegoVoiceCallModel` if the removed client was in or receiving a ZEGO call, clearing stale busy state after abnormal disconnect.
- Android Manifest declares `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, `BLUETOOTH_CONNECT`, and `USE_FULL_SCREEN_INTENT`; Android release minification keeps ZEGO classes in `flutter/android/app/proguard-rules`.
- Android calls `setAudioRouteToSpeaker(true)` from `flutter/lib/models/zego_voice_call_model.dart` so the speaker is enabled by default.
- Flutter explicitly enables ZEGO audio capture and audio transport with `enableAudioCaptureDevice(true)`, `mutePublishStreamAudio(false)`, `muteAllPlayStreamAudio(false)`, and `mutePlayStreamAudio(streamId, false)`.
- `src/client/io_loop.rs` has a process-level ZEGO call owner guard so one PC process cannot run two simultaneous ZEGO calls through the singleton Flutter ZEGO engine and mix callbacks across rooms.
- `flutter/lib/models/chat_model.dart::onVoiceCallStarted` and `onVoiceCallClosed` no longer invoke Android legacy `on_voice_call_started` / `on_voice_call_closed` platform methods for CloudSend ZEGO voice calls.
- `flutter/lib/models/zego_voice_call_model.dart` calls `startPlayingStream(playStreamId)` directly after `loginRoom` + `startPublishingStream` with the known expected stream id, and also retries/refreshes play when `onRoomStreamUpdate(Add)` arrives. A missed/delayed stream-add callback must not leave either side permanently waiting.
- Busy-state cleanup is not part of the ZEGO media sequence and must not modify token creation, room id, stream ids, `loginRoom`, `startPublishingStream`, `startPlayingStream`, `stopPublishingStream`, `stopPlayingStream`, or `logoutRoom`.
- `flutter/lib/models/zego_voice_call_model.dart` follows the official ZEGO Flutter demo's callback evidence model: `onPublisherCapturedAudioFirstFrame` records microphone capture, `onPublisherSendAudioFirstFrame` records local audio leaving the device, and `onPlayerRecvAudioFirstFrame` records remote audio arriving.
- `flutter/lib/models/zego_voice_call_model.dart` treats ZEGO first-audio-frame callbacks as mandatory: missing `onPublisherSendAudioFirstFrame` becomes `未采集到本端音频` or `本端音频未发送`; exhausting remote play retries before `onPlayerRecvAudioFirstFrame` becomes `未收到远端音频` or `未发现对端推流`.
- `flutter/lib/models/zego_voice_call_model.dart` listens to `onPublisherQualityUpdate` and `onPlayerQualityUpdate`; `flutter/lib/mobile/pages/server_page.dart::ZegoVoiceCallStatusCard` displays audio send/receive `fps/kbps` as diagnostics.
- Current PC active ZEGO voice-call panel includes a PC-only microphone toggle through `ZegoVoiceCallModel.setMuted(...)`, a Chinese `挂断` button through `bind.sessionCloseVoiceCall(...)`, collapse-to-side-rail, and long-press drag. It remains UI/ZEGO-only and must not modify the remote-control connection chain.
- Current PC voice-call panel collapses by double-clicking the expanded card; the collapsed right-side rail uses vertical `语音通话` text and supports long-press vertical dragging. It is rendered above `BlockableOverlay` in `flutter/lib/desktop/pages/remote_page.dart` so Android screen-sharing touch blocking does not cover voice-call controls.
- `flutter/lib/models/zego_voice_call_model.dart` binds the ZEGO engine lifecycle to `appId + userId + userName`; ZEGO `1000020` (`CommonUserNotSame`) triggers engine recreation with the current identity and one login retry.
- PC voice-call creation failure, peer rejection, manual close, invalid response timestamp, or local Flutter ZEGO join failure must reset only the ZEGO voice-call state, not the remote-control session. `src/client/io_loop.rs::reset_zego_voice_call_state(...)` clears `voice_call_request_timestamp`, `pending_zego_voice_call`, `zego_voice_call_active`, and the process owner.
- PC-side ZEGO business prompts in `src/client/io_loop.rs::Data::NewVoiceCall` must use `custom-nook-nocancel-hasclose-*` dialog types. Plain `error` / `warning` dialogs wire `OK` to `closeConnection()` in Flutter common UI and must not be used for token failure, duplicate-call, or process-owner-busy prompts.
- Stale ZEGO `VoiceCallResponse` packets whose `req_timestamp` does not match the current pending invite are ignored in `src/client/io_loop.rs`; they must not clear the current pending invite. This preserves rapid `invite -> hangup before accept -> immediate re-invite` flows.
- `src/client/io_loop.rs::clear_expired_pending_zego_voice_call(...)` expires stale pending ZEGO invites after 60 seconds so a lost accept/reject response cannot block future calls on the same PC-Android connection.
- `src/client/io_loop.rs` calls `clear_expired_pending_zego_voice_call(...)` from the 1-second `status_timer.tick()` branch as well as before a new invite, so pending cleanup is time-driven and does not depend on the next user click. Timeout cleanup must also send `VoiceCallRequest(false)` to Android so the controlled side clears its old pending state before any later invite.
- ZEGO close requests use `src/client/helper.rs::new_voice_call_close_request(...)` to carry the original invite timestamp while the call is still pending. PC and Android ignore stale pending-close packets whose timestamp does not match the current pending invite, so a late close from an old call cannot clear a newer invite.
- Once a ZEGO call becomes active, both PC and Android retain `active_zego_voice_call_timestamp`. Active close requests older than the current active timestamp are ignored, so a delayed close from an older call cannot hang up a newer active call. Close requests with the current timestamp, or newer timestamps from an older build, still close the current active call.
- Android controlled-side `src/server/connection.rs::clear_expired_pending_zego_voice_call(...)` also expires stale pending invites after 60 seconds, notifies Flutter with `CloseVoiceCall`, and sends `VoiceCallResponse.accepted = false` back to PC. This is the controlled-side fallback if a pending invite is not resolved by PC close or user accept in time.
- Android controlled-side `src/server/connection.rs::handle_voice_call(...)` moves the pending ZEGO payload into active state with `take()` when accepted. After accept, pending state must be empty and `zego_voice_call_active` alone represents the active call.
- `flutter/lib/models/zego_voice_call_model.dart` serializes `leave()` through `_leaveFuture` and ignores the same recently closed payload for a short window. This prevents delayed `zego_voice_call_ready` events or rapid hangup/reinvite flows from logging back into the old room after logout has already started.
- Current Android `ZegoVoiceCallStatusCard` displays `通话状态`, `房间号码`, duration, merged push/play state, merged local/remote audio readiness and `fps/kbps`, and concise error text; it no longer displays a separate peer-stream row.
- PC shows the active ZEGO voice-call panel from `flutter/lib/desktop/pages/remote_page.dart`, including `roomId`, call duration, local/remote audio readiness, audio quality, and Chinese `挂断`.
- Android shows `ZegoVoiceCallStatusCard` under the permission card in `flutter/lib/mobile/pages/server_page.dart`, including `通话状态`, `房间号码`, duration, merged push/play state, merged local/remote audio readiness and audio `fps/kbps`, and concise error text. Android does not expose a hangup button; PC remains the hangup controller.
- PC and Android voice-call status text is Chinese.
- PC toolbar no longer shows old `AudioInput(isVoiceCall: true)` under `_VoiceCallMenu`.
- Windows child Flutter engines created by `desktop_multi_window` must register ZEGO in `flutter/windows/runner/flutter_window.cpp`; otherwise the remote window raises `MissingPluginException` for `createEngineWithProfile`.
- Token service deployment and operational contract are documented in `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`; the current one-click Linux installer is `scripts/deploy_zego_token_service.sh`.
- Project integration map is documented in `docs/ZEGO_VOICE_CALL_INTEGRATION.md`; diagram-level architecture is documented in `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`.

Isolation rule:

- Do not modify video frame paths, `MediaProjection`, ADB/LADB, side-button commands, file transfer, clipboard, terminal, or port-forwarding for ZEGO voice-call changes unless a future bug is proven in that subsystem.
- Do not place `ZEGO_SERVER_SECRET` in PC, Android, Flutter, or Rust client code. The only documented exception is the private deployment runbook `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`, which is not for public release.

---

## 8. 后续文档维护标准（How to Keep This Baseline Healthy）

修改后如果以下任一项变化，必须同步本文件：

- 产品身份 / 包名 / app name / artifact name
- 顶层模块图
- 关键运行时链路
- 账号 / 同步 / 下载 / 插件 / 终端 / 隐私模式的真实入口
- 已确认的文档漂移结论

更新时继续使用：

- 中文解释
- English symbol / path 原文
- 明确的代码锚点
- 避免与现有文档结构竞争的新记忆文件
