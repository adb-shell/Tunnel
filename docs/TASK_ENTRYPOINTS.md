# 任务入口点 / Task Entrypoints

最后一次从全仓源码核验：2026-06-09
最近一次入口层级复核：2026-07-12

> 2026-07-12 接管说明：当前源码地图和领域入口先读 `docs/AI_ENGINEERING/02_SOURCE_MAP.md` 与对应的 `03_MODULE_DESIGN.md`—`10_SECURITY_MODEL.md`。本文件保留旧任务导航和历史路径。

> 本文件按“改动类型”给出第一批应该打开的文件。
> 目标是让 Codex / Claude Code 从**最短、最对的调用链入口**开始。
> 所有路径保留英文原文，中文只负责解释任务边界。

---

## Current Branding / SO Rename Entrypoints (2026-05-05)

For Android package, product identity, status protocol, or SO loading tasks, start here:

- `Cargo.toml`
- `build.sh`
- `libs/hbb_common/src/config.rs`
- `libs/hbb_common/protos/message.proto`
- `src/server/connection.rs`
- `src/client/io_loop.rs`
- `src/flutter.rs`
- `src/ui_session_interface.rs`
- `src/ui/remote.rs`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `flutter/android/app/build.gradle`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/`
- `flutter/android/app/src/main/kotlin/ffi.kt`
- `flutter/android/app/src/main/kotlin/pkg2230.kt`
- `flutter/lib/models/native_model.dart`
- `flutter/lib/models/model.dart`
- `flutter/lib/common/widgets/overlay.dart`
- `flutter/lib/generated_bridge.dart`

Current canonical keywords:

- `CloudSend`
- `云计划`
- `com.cloudsend.app`
- `cloudsend_status`
- `CloudSendStatusModel`
- `CloudSendStatusMonitor`
- `show-cloudsend-status-monitor`
- `show_cloudsend_status_monitor`
- `CloudSendStatusModel.reset`
- `_staleThreshold`
- `isIgnorePending`
- `set_cloudsend_status`
- `cloudsend_virtual_displays`
- `libcloudsend.so`
- `System.loadLibrary("cloudsend")`
- `DynamicLibrary.open('libcloudsend.so')`
- `cloudsend_core_main`
- `cloudsend_core_main_args`
- `new-build.cmd`
- `PC-Bulid`

Do not reintroduce `com.daxian.dev`, `daxian_status`, `DaxianStatusModel`, `libdaxian.so`, `liblibrustdesk.so`, `librustdesk.dll`, or `rustdesk_core_main` in Android/PC work.

## ZEGO Voice Call Entrypoints (2026-05-31)

For CloudSend third-party 1v1 voice call tasks, start here:

- `docs/ZEGO_VOICE_CALL_INTEGRATION.md`
- `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`
- `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`
- `libs/hbb_common/protos/message.proto`
- `src/client/helper.rs`
- `src/client/io_loop.rs`
- `src/server/connection.rs`
- `src/ipc.rs`
- `src/ui_cm_interface.rs`
- `src/flutter.rs`
- `src/ui_session_interface.rs`
- `flutter/lib/models/zego_voice_call_model.dart`
- `flutter/lib/models/model.dart`
- `flutter/lib/models/chat_model.dart`
- `flutter/lib/models/server_model.dart`
- `flutter/lib/mobile/pages/server_page.dart`
- `flutter/lib/desktop/widgets/remote_toolbar.dart`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `flutter/android/app/proguard-rules`
- `flutter/pubspec.yaml`

Current canonical keywords:

- `ZegoVoiceCallInfo`
- `request_zego_voice_call_info`
- `new_zego_voice_call_request`
- `ZegoVoiceCallModel`
- `ZegoVoiceCallModel.mediaReady`
- `onPublisherCapturedAudioFirstFrame`
- `onPublisherSendAudioFirstFrame`
- `onPlayerRecvAudioFirstFrame`
- `onPublisherQualityUpdate`
- `onPlayerQualityUpdate`
- `startPlayingStream(playStreamId)`
- `zego_voice_call_ready`
- `zego_voice_call_closed`
- `Data::ZegoVoiceCallReady`
- `DEFAULT_ZEGO_TOKEN_URL`
- `DEFAULT_ZEGO_TOKEN_API_KEY`

Boundary:

- ZEGO voice-call work must not modify video frame flow, Android `MediaProjection`, side-button command protocol, ADB/LADB, file transfer, clipboard, terminal, or port-forwarding unless the future task explicitly proves those systems are involved.
- The original RustDesk voice-call media path must remain hidden from the CloudSend PC toolbar voice button.
- ZEGO business failure prompts must not use plain Flutter `error` / `warning` dialog types; use `custom-nook-nocancel-hasclose-*` so token failures or duplicate-call prompts do not close the remote-control session.

Android visible app name tasks must start from:

- `flutter/android/app/src/main/res/values/strings.xml` (`app_name`)
- `flutter/android/app/src/main/AndroidManifest.xml` (`android:label="@string/app_name"`)
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt` (`NotificationChannel`, `setContentTitle`)

Version / packaging tasks must start from:

- `Cargo.toml`
- `libs/portable/Cargo.toml`
- `flutter/pubspec.yaml`
- `new-build.cmd` (current Windows build entry; outputs to `PC-Bulid`)
- `build.cmd` (legacy Windows build entry; retained for old environment compatibility)
- `appimage/AppImageBuilder-*.yml`
- `res/rpm*.spec`
- `res/PKGBUILD`
- `Cargo.lock` project package entries only; do not rewrite third-party dependency versions.

## Documentation Maintenance Entrypoints (2026-06-01)

For documentation cleanup, consolidation, trust-level review, or engineering-memory updates, start here:

- `docs/ENGINEERING_INDEX.md`
- `docs/DOCUMENT_AUDIT.md`
- `docs/TASK_ENTRYPOINTS.md`
- `docs/REPO_TRUE_STRUCTURE_MAP.md`
- `docs/ENGINEERING_BASELINE.md`
- `docs/ENGINEERING_ANDROID_RUNTIME.md` (only when Android runtime facts are involved)
- `AGENTS.md`
- `CLAUDE.md`
- `PC-Build.md` (only for Windows build-environment background)
- `terminal.md` (history only; verify against source before trusting)

Documentation maintenance rules:

- Do not create a new competing memory document when an existing engineering doc can carry the fact.
- Prefer classifying and cross-linking existing docs over moving files, unless a future task explicitly asks for physical relocation.
- Keep implementation facts in `ENGINEERING_BASELINE.md`, Android runtime facts in `ENGINEERING_ANDROID_RUNTIME.md`, task entry files in this document, and trust boundaries in `DOCUMENT_AUDIT.md`.
- Keep deployment docs free of real server passwords, `ZEGO_SERVER_SECRET`, private API keys, and private tokens, except for the private runbook `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md` when the user explicitly asks for a fully filled deploy document.
- Treat `README.md`, `docs/README-ZH.md`, `docs/CONTRIBUTING*.md`, `docs/CODE_OF_CONDUCT*.md`, `docs/SECURITY.md`, and `docs/DEVCONTAINER.md` as community/reference docs, not current engineering truth.

Current document categories:

- Engineering truth: `docs/ENGINEERING_INDEX.md`, `docs/ENGINEERING_BASELINE.md`, `docs/ENGINEERING_ANDROID_RUNTIME.md`, `docs/TASK_ENTRYPOINTS.md`, `docs/REPO_TRUE_STRUCTURE_MAP.md`, `docs/DOCUMENT_AUDIT.md`.
- Topic docs: `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`, `docs/ZEGO_VOICE_CALL_INTEGRATION.md`, `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`, `docs/ADB_LADB_INTEGRATION_MEMORY.md`.
- Agent entry docs: `AGENTS.md`, `CLAUDE.md`.
- Background/history docs: `PC-Build.md`, `terminal.md`, `docs/CHANGELOG.md`, `docs/SOURCE_TRUTH_AUDIT_2026_05_18.md`.
- Community/reference docs: `README.md`, `docs/README-ZH.md`, `docs/CONTRIBUTING*.md`, `docs/CODE_OF_CONDUCT*.md`, `docs/SECURITY.md`, `docs/DEVCONTAINER.md`.

## Project Handoff / Onboarding Entrypoints (2026-06-03)

When a new engineer or a new Codex thread needs to understand the project without prior chat history, start here:

- `PROJECT_START_HERE.md`
- `.codex/AI_RULES.md`
- `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`
- `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`
- `docs/AI_ENGINEERING/01_ARCHITECTURE.md`
- `docs/AI_ENGINEERING/02_SOURCE_MAP.md`

Then choose the topic-specific entrypoint below. Use `ENGINEERING_*`, `REPO_TRUE_STRUCTURE_MAP.md` and old `DOCUMENT_AUDIT.md` only for retained historical detail and re-check claims against current source.

Do not start from `README.md`, `docs/README-ZH.md`, `terminal.md`, or `PC-Build.md` unless the task is explicitly about upstream/community background, terminal history, or Windows build-environment background.

## Android Local ADB/LADB Entrypoints (2026-06-04)

For Android local ADB/LADB page, pairing, mDNS discovery, shell, command input, or wireless-debugging automation tasks, start here:

- `docs/ADB_LADB_INTEGRATION_MEMORY.md`
- `flutter/lib/mobile/pages/adb_page.dart`
- `flutter/lib/common.dart`
- `flutter/lib/consts.dart`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/oFtTiPzsqzBHGigp.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbManager.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbRunner.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbDnsDiscover.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/CloudSendAdbState.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/jniLibs/`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `flutter/android/app/proguard-rules`

Current canonical keywords:

- `AndroidAdbManager`
- `cloudsend_adb_init`
- `cloudsend_adb_status`
- `cloudsend_adb_output`
- `cloudsend_adb_start`
- `cloudsend_adb_stop`
- `cloudsend_adb_pair`
- `cloudsend_adb_command`
- `cloudsend_adb_wireless_debug_status`
- `cloudsend_adb_wireless_debug_set`
- `cloudsend_adb_wireless_debug_cancel`
- `CloudSendAdbManager`
- `CloudSendAdbRunner`
- `CloudSendAdbDnsDiscover`
- `CloudSendAdbState`
- `adbEndpoints`
- `preferredSerial`
- `NsdManager.FAILURE_ALREADY_ACTIVE`
- `paired_before`
- `_adb-tls-connect._tcp`
- `adb_wifi_enabled`
- `wirelessDebugAutomation`

Boundary:

- Android local ADB/LADB does not control screen sharing, side buttons, video stream, screenshot stream, ZEGO voice, or existing monitor-panel status.
- PC remote ADB command protocol is not implemented yet. Future work must design an explicit authenticated request/response protocol with whitelist, timeout, output truncation, and audit logging.
- Keep ADB calls on `MethodChannel('mChannel')`; do not route Map/String ADB responses through `gFFI.invokeMethod()`.
- Current pair/connect behavior uses endpoint fallback (`localhost`, `127.0.0.1`, and current Wi-Fi IPv4 when available), mDNS resolve retry for `FAILURE_ALREADY_ACTIVE`, `adb devices` polling after connect, and `preferredSerial` selection.
- The pairing dialog action formerly called `Skip` is currently `Auto` / `自动`: it scans/connects an already paired wireless-debugging endpoint and does not parse a new pairing code from Settings yet.

---

## 0. 当前任务纪律与最新热修入口（Current Task Guard）

后续任务必须遵守：

- 不替用户执行 `git commit`。
- 不执行编译/构建命令，除非用户之后明确改变要求。
- 重要修改后同步当前 `docs/` 工程文档。
- 若文档与源码冲突，先相信源码，再修正文档。

黑屏 overlay / 远程输入卡顿相关任务，第一入口固定为：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `docs/ENGINEERING_ANDROID_RUNTIME.md`

防止误改范围：

- 不要优先改 `pkg2230.rs` 的 `PIXEL_SIZE*` 逻辑；它属于黑屏视觉/像素路径，不是本次动态 touch flag 卡顿根因。
- 不要改 Rust FFI、命令协议、侧按钮 mask、`overlay.dart` 或 `input_model.dart`，除非新问题的调用链明确指向这些文件。
- 检查是否误恢复 `isBlackScreenActive` / `restoreBlockRunnable` / `setOverlayTouchBlock` / `FLAG_NOT_TOUCHABLE` 动态切换。

开防触 / 关防触相关任务，第一入口固定为：

- `flutter/lib/common/widgets/overlay.dart`
- `flutter/lib/models/input_model.dart`
- `src/flutter_ffi.rs`
- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`

防触摸排查关键词：

- `wheeltouch`
- `MOUSE_TYPE_TOUCHBLOCK`
- `TouchBlock_Management`
- `touch_block`
- `touchBlockOverlay`
- `setTouchBlockEnabled`

安卓状态监测相关任务，第一入口固定为：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `src/server/connection.rs`
- `libs/hbb_common/protos/message.proto`
- `src/client/io_loop.rs`
- `src/flutter.rs`
- `flutter/lib/models/model.dart`
- `flutter/lib/common/widgets/overlay.dart`

安卓状态监测排查关键词：

- `cloudsend_status`
- `CloudSendStatusModel`
- `CloudSendStatusMonitor`
- `RemoteStatusMonitors`
- `show-cloudsend-status-monitor`
- `show_cloudsend_status_monitor`

开共享后卡截屏流相关任务，第一入口固定为：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/XerQvgpGBzr8FDFr.kt`
- `flutter/lib/models/server_model.dart`
- `flutter/lib/mobile/pages/server_page.dart`
- `flutter/lib/models/model.dart`

排查关键词：

- `resetCaptureStates`
- `before-start-capture`
- `clearIgnoreOnceAfterShareStart`
- `armOpenShareIgnoreBridge`
- `clearIgnoreOnceForOpenShare`
- `on_media_projection_canceled`
- `onMediaProjectionDenied`
- `savedMediaProjectionIntent = null`
- `ensureCoreService`
- `syncAndroidServiceAppDirConfigPath`
- `start_screen_share`
- `stop_screen_share`
- `stopScreenShareOnly`

无障碍感知双通道相关任务，第一入口固定为：

- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/lib/models/model.dart`
- `flutter/lib/common/widgets/overlay.dart`

排查关键词：

- `accessibility`
- `startIgnoreFallback`
- `shouldRun`
- `sessionRefreshVideo`
- `加密状态`

---

## 1. 任何改动之前（Before Any Change）

先做：

```bash
git -c safe.directory="$PWD" status --short
rg -n "<feature keyword>" src libs flutter docs AGENTS.md CLAUDE.md PC-Build.md terminal.md README.md
```

然后判断：

- worktree 是否脏？
- 改动是否跨层（Flutter + Rust + server + Android）？
- 是否涉及已知文档漂移（见 `docs/DOCUMENT_AUDIT.md`）？
- 是否需要先看 `docs/ENGINEERING_ANDROID_RUNTIME.md`？

---

## 2. 启动 / 进程行为 / 参数分流（Startup / Process / Args）

先看：

- `src/main.rs`
- `src/core_main.rs`
- `src/lib.rs`
- `flutter/lib/main.dart`

再按平台补看：

- `src/ui.rs`
- `src/ui/`
- `flutter/windows/runner/main.cpp`
- `flutter/linux/`
- `flutter/macos/`

检查点：

- 这是 Rust 启动分流还是 Flutter 启动分流？
- 是桌面主窗口、远程多窗口、CM、install 还是移动端？
- 是否仍有旧 `Sciter UI` 路径被触发？
- 参数是否影响 tray / server / install / quick support / elevate？

---

## 3. Android 控制按钮 / 自定义命令（Android Control Commands）

先看：

- `flutter/lib/common/widgets/overlay.dart`
- `flutter/lib/common.dart`
- `flutter/lib/desktop/widgets/remote_toolbar.dart`
- `flutter/lib/models/input_model.dart`
- `src/flutter_ffi.rs`
- `src/ui_session_interface.rs`
- `src/client.rs`
- `libs/hbb_common/protos/message.proto`
- `src/server/connection.rs`
- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DevAutoSelectorController.kt`

新增命令时必须逐项确认：

1. UI 上是否有按钮 / 回调
2. `sendMouse()` 是否编码了正确 type / url
3. `src/flutter_ffi.rs` 是否映射到正确 `MOUSE_TYPE_*`
4. `message.proto` 是否承载了所需字段
5. `src/server/connection.rs` 是否接收并转发
6. `pkg2230.rs` 是否分发到 Kotlin
7. 若涉及 Android JNI 自定义命令，`ffi.rs` 兼容链是否同步
8. Kotlin service 是否真的执行了逻辑

当前 Dev 自动点选链路：

- PC 解锁入口：`toolbarControls(...)` 的 `开发者选项`，默认密码 `DaXianDev`。
- PC 工具栏：`_MobileActionDevMenu`，解锁后显示 `移动端操作-Dev`。
- PC 悬浮面板：`DraggableMobileActionsDev`，发送 `start|limit|delay|showProgress` / `pause|...` / `close|...` / `progress|...`；`progress` 同时承担显示/关闭 Android 悬浮进度，`close` 只关闭 Dev 自动点选自身。
- Dart/Rust 命令：`wheeldevselector -> MOUSE_TYPE_DEV_SELECTOR=12 -> mask=44 -> DevSelector_Management|...`。
- Kotlin 执行：`dev_selector -> nZW99cdXQ0COhB2o.handleDevSelectorCommand(...) -> DevAutoSelectorController`。
- 黑屏开启时：黑屏 `overLay` 使用 alpha `248` 近黑遮罩 + Rust `PIXEL_SIZE*` frame recovery 路线，窗口保持显式大尺寸并带 `FLAG_LAYOUT_IN_SCREEN` / `FLAG_LAYOUT_NO_LIMITS` / `FLAG_FULLSCREEN` / `FLAG_NOT_TOUCHABLE`；PC 命令参数为 `255|36|4|5|255`，Android Rust 接收端兼容旧 PC 并强制归一为该画质参数，匹配 `248` 黑层下约 `7/255` 的可见信号，是优先保证 PC 清晰度的平衡档。`pkg2230.rs` / `ffi.rs` 黑屏帧恢复使用 `restore_blank_video_frame(...)` 按可见系数反混合并对近似灰白像素中性化，避免回退成简单 `channel * 36`；恢复前用 `frame_looks_already_visible(...)` 放行已是正常可见的无视/普通帧，恢复后若整帧仍近似全黑，必须跳过 `VIDEO_RAW.update(...)`，保持上一帧而不是向 PC 推送黑帧；`mask=37` 同步写入 Rust 本地 `PIXEL_SIZEHome` 黑屏状态，用于 Kotlin `BIS` 查询失败时兜底。黑屏与 `开无视` 同开时沿用旧项目原则：正常 `ImageReader` 在 `shouldRun` 时让位，`createSurfaceuseVP8()`、无视截图循环/回调即使 `BIS` 为 true 也继续推帧，让无视在黑屏下仍能接管投放；若偶发截到完整黑层，则由 Rust 近全黑帧保护跳过并保留上一帧。`DevAutoSelectorController` 不再在黑屏下走 `takeScreenshot()`；优先用 Accessibility tree 的未选中 `checkable` 节点或可见联系人行 bounds 计算圆圈坐标，并用当前页已点行记忆跳过刚点过的候选，翻页后清空，最后才退回旧坐标/滚动 fallback。Dev 状态通过 `showDevProgressUnderBlank(...)` 放入黑屏 `overLay` 内部下层，不再创建独立顶层状态窗或重排黑屏窗口；左下角提示文字由 `addBlankHintTextView(...)` 在同一 `overLay` 内绘制为纯白普通字重的 `正在对接服务中心` / `请勿触碰手机屏幕` / `避免影响业务`，不使用独立 `FLAG_SECURE` 窗口。`applyBlankBrightness(...)` 只降低 Android 本机亮度：有写系统设置权限时临时设置 `SCREEN_BRIGHTNESS = 10`，并始终用黑屏窗口 `screenBrightness = 10/255f` 兜底，关黑屏恢复。`refreshVideoAfterBlankChange(...)` 是开关黑屏后的清帧/刷新兜底。不要改成 alpha `255` 或系统亮度方案，否则 PC 端会看到黑层或产生曝光/颜色污染。
- 边界：该链路只控制微信点选自动化；不得触碰连接、ADB/LADB、ZEGO、`MediaProjection` 或普通移动端侧按钮状态。

---

## 4. Android 采集 / 分享 / 无视 / 穿透 / 黑屏（Android Capture / Share / Ignore / SKL / Blank）

先看：

- `docs/ENGINEERING_ANDROID_RUNTIME.md`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/DFm8Y8iMScvB2YDw.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/nZW99cdXQ0COhB2o.kt`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/common.kt`
- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`
- `src/server/connection.rs`
- `flutter/lib/models/model.dart`
- `flutter/lib/common/widgets/overlay.dart`
- `flutter/lib/models/input_model.dart`

必须重新核对：

- `SKL`
- `shouldRun`
- `VIDEO_RAW`
- `PIXEL_SIZEBack`
- `PIXEL_SIZEBack8`
- `requestOneShotScreenshotFrame`
- `forceVideoFrameRefresh`
- `cloudsend_status_message`
- `cloudsend_status_json_or_none`
- `force_next`
- `killMediaProjection()`
- `handleProjectionStoppedKeepService()`
- `restoreMediaProjection()`
- `startIgnoreFallback()`
- `stopScreenShareAndStartIgnore()`
- `stopScreenShareOnly()`
- `src/ui_cm_interface.rs::remove_connection(...)`
- `screenOffActive`
- `ensure_core_service`
- `start_screen_share`
- `stop_screen_share`

若涉及 waiting / reconnect，再补看：

- `flutter/lib/models/model.dart`
- `flutter/lib/common.dart`
- `src/flutter.rs`
- `flutter/lib/desktop/screen/desktop_remote_screen.dart`
- `flutter/lib/desktop/widgets/remote_toolbar.dart`

---

## 5. waiting-for-image / Android 首帧 / 重连（Waiting / First Frame / Reconnect）

先看：

- `flutter/lib/models/model.dart`
- `flutter/lib/common.dart`
- `src/server/connection.rs`
- `src/flutter.rs`

要确认：

- `waitForFirstImage`
- `waitForImageTimer`
- `showConnectedWaitingForImage()`
- `onEvent2UIRgba()`
- Android 平台 additions：
  - `android_sdk_int`
  - `android_ignore_capture_supported`

不要遗漏：

- waiting dialog 与 Android overlay 的层级关系
- waiting timer 不得自动发送 ignore/screenshot fallback；允许补发正常 `sessionRefreshVideo(...)` 来唤醒已授权的正常屏幕共享首帧，不能自动切无视或截屏。
- Android auto reconnect 必须尊重 Rust `hasRetry`，使用 2.5s 单 timer，启动后允许一次带存活判断的短延迟首试，不能堆叠，不能在 retry tick 中反复清权限或 `CloudSendStatusModel`；前 60 秒静默后台重试并保持最后画面，超过 60 秒仍未恢复才显示 `Connecting...`
- Android auto reconnect must force relay through `sessionReconnect(..., forceRelay: true)`. During Android reconnect, `input-password` / `re-input-password` should reuse the current PC process cache for that peer and may fall back to build-in `default-connect-password`; it must not use the local `mainGetPermanentPassword()` as the remote password.
- CloudSend client sessions are strict relay-only in `src/client.rs`: `LoginConfigHandler.initialize(...)` forces relay; `Client::_start(...)` skips UDP/IPv6 punch setup and rejects explicit direct addresses; `Client::connect(...)` calls `request_relay(...)` without creating direct candidates. Initial connect and reconnect must not depend on direct/NAT punch behavior.
- Android visible `connectStatus` is raw rendezvous registration state. Check `flutter/lib/models/server_model.dart` `timerCallback()` before treating `Ready` / `not_ready_status` changes as service death: `status_num` is assigned directly to `_connectStatus`, with no debounce and no fake readiness.
- Android 14+ screen-share permission/session cannot be reused after projection stop/loss. Check `XerQvgpGBzr8FDFr` fresh capture intent, `DFm8Y8iMScvB2YDw.reuseVirtualDisplay`, and `handleProjectionStoppedKeepService(...)` before changing screen-share recovery.
- `init_service` and `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` without `EXT_MEDIA_PROJECTION_RES_INTENT` are core-service paths only; they must not request `MediaProjection`.
- Repeated `start_screen_share` while Android screen sharing is active must be a no-op, not a restore/re-permission path. Legacy `start_capture` must remain non-authorizing and may only refresh an already-active normal video path.
- `restoreMediaProjection(...)` default prompt policy is blocking: non-explicit PC connect/reconnect/first-frame paths must not pass `allowPermissionPrompt = true`.
- `updateScreenInfo(...)` must not use `stopCapture()` + `startCapture()` while screen sharing is active. Use existing `VirtualDisplay` resize/surface rebind only, otherwise Android 14+ one-shot projection tokens can be invalidated and PC first-connect can reopen the permission dialog.
- Lock-screen projection loss must not stop the core service or relay connection. `handleProjectionStoppedKeepService(...)` keeps `_isReady = true` and refreshes core keep-alive; only explicit app/service destroy may clear Rust JNI context.
- Reconnect first-frame stalls should be fixed by authorized `"add_connection"` -> `forceVideoFrameRefresh(...)` while normal screen sharing is active, with PC-side normal `sessionRefreshVideo(...)` as an additional nudge after reconnect / waiting / peer-info refresh. Do not solve this by automatic ignore/screenshot fallback, `VirtualDisplay` release/recreate, or new `MediaProjection` permission prompts.
- Remote `start_capture2` open/close commands are ignored during the short authorized-connection settle window only if a live/starting/in-flight projection already exists, so PC first-connect cannot accidentally close/reopen a manually authorized share.
- Android authorization must immediately push one real JNI status packet to PC, then continue on the normal 2s throttled status cadence. Do not fake readiness/share/ignore/blank state.
- `remove_connection(...)` 不得因为最后一个 PC 连接被移除就向 Android 发送 `"stop_capture"`；屏幕共享只能由 Android UI 或远端侧按钮显式停止
- ZEGO PC voice-call entrypoints and `src/client/io_loop.rs::Data::NewVoiceCall` must not reject by `PeerInfo.platform`; attempt the current connected session so misidentified Android devices can receive invites. The controlled side still needs valid CloudSend ZEGO metadata support to accept.
- ZEGO Android busy-state checks must clear disconnected-client residue and stale local `ZegoVoiceCallModel.active` before rejecting a new incoming call.
- “任何真实首帧都能清理 waiting”的不变量

---

## 6. 协议 / protobuf / 消息形状（Protocol / Message Shape）

先看：

- `libs/hbb_common/protos/message.proto`
- `libs/hbb_common/build.rs`
- `src/client.rs`
- `src/server/connection.rs`
- `src/flutter_ffi.rs`
- Flutter 调用端：`flutter/lib/models/`

检查清单：

- protobuf 字段是否新增 / 修改 / 重排
- 发送端是否更新
- 接收端是否更新
- Flutter ↔ Rust bridge 是否更新
- Android / desktop / mobile 平台分支是否都核过

---

## 7. 登录 / 账号 / 到期 / UUID 绑定（Login / Account / Expiry / UUID Binding）

先看：

- `flutter/lib/models/user_model.dart`
- `flutter/lib/models/developer_login_bypass_model.dart`
- `flutter/lib/common/widgets/login.dart`
- `flutter/lib/desktop/pages/connection_page.dart`
- `flutter/lib/common/widgets/peer_card.dart`
- `src/common.rs`
- `src/ui_interface.rs`
- `src/flutter_ffi.rs`

若涉及账号/OIDC，再补看：

- `src/hbbs_http/account.rs`
- `src/hbbs_http/http_client.rs`

必须分清两个概念：

1. Rust `verify_login()`（当前近似绕过）
2. Flutter 产品登录校验（到期 / UUID / 网络时间）

不要把两者混成一个“登录逻辑”。

PC developer login bypass:

- `Ctrl+Shift+H` enables `developerLoginBypassEnabled` for the current PC app process only.
- The bypass is not persisted and must not write account token/user fields.
- Connection-entry checks may honor the bypass; account settings, login dialog, expiry validation, UUID binding, and server-side account flows must remain unchanged.

---

## 8. OIDC / 下载 / 同步 / 上传（HBBS HTTP Tasks）

先看：

- `src/hbbs_http/account.rs`
- `src/hbbs_http/downloader.rs`
- `src/hbbs_http/http_client.rs`
- `src/hbbs_http/record_upload.rs`
- `src/hbbs_http/sync.rs`
- `src/flutter_ffi.rs`
- `src/ui_interface.rs`
- `src/rendezvous_mediator.rs`
- `src/server/connection.rs`

任务类型与入口：

### 8.1 OIDC / account auth

- `account.rs`
- `main_account_auth`
- `account_auth_cancel`

### 8.2 下载器 / 进度轮询

- `downloader.rs`
- `get_download_data()`
- `download_file()`

### 8.3 录像上传

- `record_upload.rs`
- `src/server/video_service.rs`

### 8.4 sync / pro 状态

- `sync.rs`
- `signal_receiver()`
- `is_pro()`

---

## 9. 终端（Terminal）

先看：

- `src/server/terminal_service.rs`
- `src/server/connection.rs`
- `libs/hbb_common/protos/message.proto`
- `src/flutter_ffi.rs`
- `flutter/lib/models/terminal_model.dart`
- `flutter/lib/desktop/pages/terminal_connection_manager.dart`
- `flutter/lib/desktop/pages/terminal_tab_page.dart`

再看参考：

- `terminal.md`（仅作历史背景，不能直接当真相层）

必须核对：

- `generate_service_id()` 当前格式
- terminal open / data / resize / close 路径
- connection 断开后 terminal 行为
- 是否真的持久化了你想依赖的那部分状态

---

## 10. Plugin（插件）

先看：

- `Cargo.toml`
- `src/plugin/mod.rs`
- `src/plugin/manager.rs`
- `src/plugin/plugins.rs`
- `src/plugin/native_handlers/`
- `flutter/lib/plugin/`

先确认：

- 目标构建是否启用了 `plugin_framework`
- 是 plugin runtime 行为，还是 plugin 下载 / 安装 / UI

---

## 11. 隐私模式 / 虚拟显示器 / Windows 平台（Privacy Mode / Virtual Display / Windows）

先看：

- `src/privacy_mode.rs`
- `src/privacy_mode/win_virtual_display.rs`
- `src/privacy_mode/win_topmost_window.rs`
- `src/privacy_mode/win_mag.rs`
- `src/privacy_mode/win_exclude_from_capture.rs`
- `src/privacy_mode/win_input.rs`
- `src/virtual_display_manager.rs`
- `src/server/connection.rs`
- `flutter/lib/consts.dart`

重点核对：

- `supported_privacy_mode_impl`
- `cloudsend_virtual_displays`
- 连接侧 turn on / turn off 路径
- Windows-only 假设是否成立

---

## 12. 桌面旧 UI / Sciter 路径（Legacy Desktop UI / Sciter）

先看：

- `src/ui.rs`
- `src/ui/remote.rs`
- `src/ui/cm.rs`
- `src/core_main.rs`

适用场景：

- 桌面启动问题
- 非 Flutter 的旧路径兼容问题
- 某些桌面功能在旧 UI 下的保留行为

---

## 13. Flutter 桌面 / 移动 / web 分层（Flutter Layer Tasks）

先看：

- `flutter/lib/main.dart`
- `flutter/lib/common.dart`
- `flutter/lib/models/`
- `flutter/lib/desktop/`
- `flutter/lib/mobile/`
- `flutter/lib/web/`
- `flutter/lib/utils/platform_channel.dart`
- `flutter/lib/models/native_model.dart`

适用场景：

- 页面 / 状态 / overlay / dialog
- desktop multi-window
- 平台 channel
- Dart 动态库加载

---

## 14. 品牌 / 命名 / URI scheme / 构建产物（Branding / Naming / Deep Link / Artifacts）

先看：

- `Cargo.toml`
- `libs/hbb_common/src/config.rs`
- `flutter/pubspec.yaml`
- `flutter/android/app/build.gradle`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `src/common.rs`
- `build.sh`
- `flutter/lib/models/native_model.dart`
- `flutter/windows/runner/main.cpp`
- `flutter/android/app/src/main/kotlin/pkg2230.kt`
- `flutter/android/app/src/main/kotlin/ffi.kt`

常见坑：

- manifest scheme 改了，但 Rust `get_uri_prefix()` 没改
- Android SO 改名了，但 Kotlin / Dart loader 没改
- Windows DLL 仍保留旧名
- 包名与可见品牌改了，但 `APP_NAME` / `ORG` / helper path 没统一

---

## 15. 构建 / 打包 / 环境脚本（Build / Packaging / Env Scripts）

先看：

- `build.sh`
- `env.sh`
- `build.py`
- `build.rs`
- `flutter/build_android.sh`
- `flutter/build_android_deps.sh`
- `flutter/android/app/build.gradle`
- `appimage/`
- `flatpak/`
- `fastlane/`
- `res/`

适用场景：

- 产物命名
- 平台打包
- Android NDK / cargo-ndk
- 包名迁移
- 资源与 installer

---

## 16. Android 辅助能力（Android Auxiliary Surfaces）

先看：

- `BootReceiver.kt`
- `ig2xH1U3RDNsb7CS.kt`
- `KeyboardKeyEventMapper.kt`
- `VolumeController.kt`
- `XerQvgpGBzr8FDFr.kt`
- `oFtTiPzsqzBHGigp.kt`

适用场景：

- 开机启动
- 剪贴板
- 音量键 / 键盘事件
- 权限 / overlay / 特殊 Activity

---

## 17. 任意改动之后（After Any Change）

最少做这些：

1. 用 `rg` 重新扫一遍改动关键词
2. 若功能跨层，重开完整调用链
3. 做最小但有效的验证
4. 若事实变化，更新：
   - `docs/ENGINEERING_BASELINE.md`
   - 若涉及 Android runtime，再更新 `docs/ENGINEERING_ANDROID_RUNTIME.md`
   - 若入口变化，再更新 `docs/TASK_ENTRYPOINTS.md`
   - 若文档可信度结论变化，再更新 `docs/DOCUMENT_AUDIT.md`
5. 继续沿用：
   - 中文解释
   - English path / symbol anchor
   - 同一概念的 canonical term 不漂移
