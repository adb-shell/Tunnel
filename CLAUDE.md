# CLAUDE.md — CloudSend / 云计划 v5.2.1

最后一次与全仓源码对齐：2026-07-12
最近一次文档分层整理：2026-07-12

> 本文件是 **Claude Code** 的项目入口说明。
> 它是补充导航，不是最终真相层。
> 每次会话开始时请优先阅读：
>
> 1. `PROJECT_START_HERE.md`
> 2. `.codex/AI_RULES.md`
> 3. `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`
> 4. 开发任务建立 `TASK_TEMPLATE.md` 并引用 `docs/BASELINE/BASELINE_INDEX.md`
> 5. `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`、`01_ARCHITECTURE.md`、`02_SOURCE_MAP.md` 和对应领域文档
> 6. 长期决定读 `docs/ADR/README.md`；验证读 `TEST_MATRIX.md`
> 7. 开发任务再读 `DEVELOPMENT_WORKFLOW.md`；仓外依赖再读 `EXTERNAL_ASSET_REGISTRY.md`
> 8. `docs/ENGINEERING_*` 与 `docs/TASK_ENTRYPOINTS.md` 只作 2026-07-12 前的旧主套件和历史细节
>
> 本文件中的中文用于解释；**代码路径、类名、函数名、常量、构建产物名一律保留英文原文**，以避免检索歧义。

---

## 1. 项目身份（Project Identity）

- 产品/runtime 名：`CloudSend`
- Android 显示名：`云计划`
- 基础：RustDesk 深度二次开发
- Rust crate：`cloudsend`
- Rust library：`cloudsend`
- Flutter package：`flutter_hbb`
- Android package/applicationId：`com.cloudsend.app`
- Android deep link scheme：`cloudsend`
- Runtime `APP_NAME`：`CloudSend`（`libs/hbb_common/src/config.rs` / `hbb_common::config::APP_NAME`）
- Runtime `ORG`：`com.carriez`（仍有历史来源，非 Android package）
- 当前版本：Rust `5.2.1`，Flutter `5.2.1+59`

---

## 2. 先建立的心智模型（Mental Model First）

这个项目不是单一路径应用，而是多层并存：

1. Rust core：`src/`
2. Shared protocol/config：`libs/hbb_common/`
3. Android JNI / raw frame：`libs/scrap/src/android/`
4. Flutter UI：`flutter/lib/`
5. Android Kotlin runtime：`flutter/android/app/src/main/kotlin/com/cloudsend/app/`
6. Legacy desktop UI：`src/ui/`
7. Account / HTTP / sync / upload：`src/hbbs_http/`
8. Windows privacy mode / virtual display：`src/privacy_mode.rs` + `src/virtual_display_manager.rs`
9. Android local ADB/LADB：`flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/` + `flutter/lib/mobile/pages/adb_page.dart`
10. ZEGO voice call：Flutter RTC side path + Rust control-channel invitation state

不要把项目误读成：

- 只有 Flutter，没有旧 UI
- 只有远控，没有账号/同步链
- Android 只有 Kotlin，没有 Rust JNI 主链
- ADB/LADB 只是外部研究目录；当前 Android 本地 ADB 已有落地模块
- ZEGO 语音走 RustDesk 原 `audio_service`；当前媒体只走 ZEGO SDK
- 品牌/运行名已经迁移到 CloudSend，但上游文档、翻译、兼容判断和第三方依赖中仍会有历史 RustDesk 字样

---

## 3. Android 类名映射（Android Class Map）

- `oFtTiPzsqzBHGigp.kt` = `FlutterActivity`
- `XerQvgpGBzr8FDFr.kt` = 权限 / 透明 Activity
- `DFm8Y8iMScvB2YDw.kt` = `MainService`（`MediaProjection`, keep-alive）
- `nZW99cdXQ0COhB2o.kt` = `AccessibilityService`（输入 / fallback / overlay）
- `DFrLMwitwQbfu7AC.kt` = `FloatWindowService`
- `BootReceiver.kt` = 开机启动
- `ig2xH1U3RDNsb7CS.kt` = 剪贴板桥
- `EqljohYazB0qrhnj.kt` = 图像辅助
- `common.kt` = Android 全局状态
- `KeyboardKeyEventMapper.kt` = 键盘映射
- `VolumeController.kt` = 音量控制
- `adb/CloudSendAdbManager.kt` = Android local ADB/LADB facade
- `adb/CloudSendAdbRunner.kt` = packaged `libadb.so` runner
- `adb/CloudSendAdbDnsDiscover.kt` = wireless debugging mDNS discovery
- `adb/CloudSendAdbState.kt` = ADB state snapshot
- ADB pair/connect currently uses endpoint fallback (`localhost`, `127.0.0.1`, active Wi-Fi IPv4), mDNS retry/fallback, `preferredSerial`, and cancellable wireless-debugging automation. PC remote ADB command protocol is still future work.
- `pkg2230.kt` = 主 JNI bridge
- `ffi.kt` = 兼容 JNI bridge
- `p50.java` + `q50.java` = XOR / 混淆辅助

---

## 4. 关键工程真相（High-Value Facts）

### 4.1 Android JNI 主路由

- 当前生效模块：`libs/scrap/src/android/pkg2230.rs`
- 兼容模块：`libs/scrap/src/android/ffi.rs`
- `libs/scrap/src/android/mod.rs` 当前只导出 `pkg2230`

结论：

- 修改 Android JNI 时，以 `pkg2230.rs` 为主
- `ffi.rs` 不能假设与其完全相同，但改动后应有意识检查

### 4.2 Android 三条采集/显示路径

- 正常共享路径（`normal MediaProjection video path`）
- 穿透路径（`SKL pass-through path`）
- 无视回退路径（`ignore-capture fallback path`）

关键状态：

- Kotlin：`SKL`, `shouldRun`, `gohome`, `BIS`, `SCREEN_INFO`
- Rust JNI：`VIDEO_RAW`, `PIXEL_SIZEBack`, `PIXEL_SIZEBack8`, `force_next`

### 4.3 waiting-for-image 不是等于 Android 服务状态

PC 侧关键状态在：

- `flutter/lib/models/model.dart`
  - `waitForFirstImage`
  - `waitForImageTimer`
  - `showConnectedWaitingForImage()`
  - `onEvent2UIRgba()`

要点：

- 服务活着 != 已经有首帧
- Android core connection/id service != Android screen sharing；`Start service` / `Stop service` 只控制 `MediaProjection` 屏幕共享。
- Android 14+ `MediaProjection` token / `createVirtualDisplay()` is one-shot. Android 15 QPR1+ may stop projection on lock screen. Treat projection stop as screen-share loss only: release projection resources, clear stale saved intent on Android 14+, keep `MainService` / Rust JNI context / relay connection alive.
- Current source truth: `DFm8Y8iMScvB2YDw.createOrSetVirtualDisplay(...)` treats `SecurityException` / virtual-display creation failure as screen-share loss only and must not call `requestMediaProjection()` from that failure path. Only explicit `start_screen_share` / side-button `开共享` may request `MediaProjection`.
- Current source truth: `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE` without `EXT_MEDIA_PROJECTION_RES_INTENT` is core-service only and must not request screen-share authorization. `BootReceiver.kt` still uses this action, but it no longer opens the projection dialog without a real permission result.
- `updateScreenInfo(...)` must not call `stopCapture()` + `startCapture()` while normal screen sharing is active. It may resize/rebind the existing `VirtualDisplay` surface only.
- `MainService.onDestroy()` clears Rust JNI context only on explicit app/service destroy. Non-explicit service destruction keeps JNI context while the app process is alive and requests a guarded `ACT_ENSURE_CORE_SERVICE` restart; network, lock-screen, memory, status, and screen-share changes must not trigger core-service restart.
- PC waiting-for-image 不得自动发送"开无视"或截屏 fallback；允许补发正常 `sessionRefreshVideo(...)` 请求来唤醒已授权的正常屏幕共享首帧，不能自动切无视/截屏。
- 侧按钮 `开共享`/`关共享` 是 Android runtime 的主动操作：`开共享` 可临时无视兜底并在共享恢复后一次性清无视，`关共享` 可在无障碍存在时自动切无视保画面。
- Android 掉线自动重连是 2.5s 单 timer、不可堆叠；启动后允许一次带存活判断的短延迟首试；前 60 秒静默后台重试并保持最后画面，超过 60 秒仍未恢复才显示连接提示。
- Android 授权 `add_connection` 且正常屏幕共享已开启时，会通过 `forceVideoFrameRefresh(...)` 补正常视频首帧，解决重连后静态屏幕卡在 waiting-for-image；不得用 PC 自动切无视/截屏 fallback 或释放重建 `VirtualDisplay` 解决该问题。
- Android 授权成功后必须立即向 PC 推送一次真实 JNI 状态包，之后再回到 2s 节流状态推送；不得伪造就绪、共享、无视或黑屏状态。
- 连接必须是 strict relay-only：PC 侧 `sessionReconnect(..., forceRelay: true)`，Rust `LoginConfigHandler.initialize(...)` 默认 `force_relay = true`；force relay 下不得启动 UDP/IPv6/direct 连接候选，显式 IP/domain:port 直连入口也必须拒绝。
- Android 自动重连期间如果底层出现 `input-password` / `re-input-password`，优先复用本次 PC 进程缓存的远端密码；缓存为空时可读取构建内置 `default-connect-password` 作为与首次连接同源的固定密码。仍然不能用本机 `mainGetPermanentPassword()` 冒充远端密码。
- `src/ui_cm_interface.rs::remove_connection(...)` 不能因为最后一个 PC 连接移除就向 Android 发送 `"stop_capture"`；PC 断开/重连/关闭窗口不等于停止 Android 屏幕共享。
- Android `connectStatus` 必须保持官方 RustDesk 风格的真实 rendezvous 注册状态：`mainGetConnectStatus()` 的 `status_num` 直接写入 `_connectStatus`，不得做 UI 防抖或假就绪；它也不是核心服务存活证明。
- PC ZEGO 语音入口和 `src/client/io_loop.rs::Data::NewVoiceCall` 不再依赖 `PeerInfo.platform == Android`；对当前已连接会话直接尝试 ZEGO 邀请，避免平台字符串未识别的 Android 无法发起通话。
- Android ZEGO 忙状态只以仍被客户端跟踪的 `inVoiceCall` / `incomingVoiceCall` 为准；断开的旧客户端或陈旧 `ZegoVoiceCallModel.active` 不能阻止下一台 PC 发起通话。
- 任何真实 RGBA 帧到达 UI 时都应清理 waiting

### 4.4 登录逻辑要分两层看

- Rust：`src/common.rs::verify_login()`（当前近似绕过）
- 产品账号：`flutter/lib/models/user_model.dart`
  - `ChinaNetworkTimeService`
  - `validateUser()`
  - `mainGetUuid()`

### 4.5 账号 / 下载 / 上传不是边角料

以下是真实子系统：

- `src/hbbs_http/account.rs`
- `src/hbbs_http/downloader.rs`
- `src/hbbs_http/record_upload.rs`
- `src/hbbs_http/sync.rs`

### 4.6 Windows 平台还包含独立维护面

- `src/privacy_mode.rs`
- `src/privacy_mode/win_virtual_display.rs`
- `src/virtual_display_manager.rs`

---

## 5. 命令协议速查（Android Command Quick Map）

Flutter / Rust 命令字符串：

- `wheelblank`
- `wheelbrowser`
- `wheelanalysis`
- `wheelback`
- `wheelstart`
- `wheelstop`

Rust 映射：

- `MOUSE_TYPE_BLANK = 5`
- `MOUSE_TYPE_BROWSER = 6`
- `MOUSE_TYPE_Analysis = 7`
- `MOUSE_TYPE_GoBack = 8`
- `MOUSE_TYPE_START = 9`
- `MOUSE_TYPE_STOP = 10`

主链路：

`overlay.dart`  
→ `input_model.dart`  
→ `src/flutter_ffi.rs`  
→ `src/ui_session_interface.rs`  
→ `src/client.rs`  
→ `message.proto`  
→ `src/server/connection.rs`  
→ `pkg2230.rs`  
→ Kotlin services

---

## 6. 构建命令（Build Commands）

> 以下命令只记录正式环境入口，不构成 AI 执行授权。默认禁止自动 build/test/analyze/codegen；需要验证时先遵守 `.codex/AI_RULES.md`，并按 `docs/AI_ENGINEERING/09_DEBUG_SYSTEM.md` 输出《编译验证需求》。构建事实以 `docs/AI_ENGINEERING/08_BUILD_SYSTEM.md` 为准。

### Android（Linux 构建机）

```bash
./build.sh 1
./build.sh 2
```

### Windows / PC（Windows Server 构建机）

```bat
new-build.cmd
```

说明：

- `new-build.cmd` 适配 `PC-Build.md` 中的 `C:\DevEnv` + `C:\DevTool` 环境。
- 生成的自解压产物输出到：`PC-Bulid\<源码目录名>.exe`。
- 旧 `build.cmd` 仍保留，但不是当前推荐的新环境入口。

### Desktop / Flutter（通用）

```bash
python3 build.py --flutter --release
cd flutter && flutter pub get
cd flutter && flutter build apk --release
```

### Rust

```bash
cargo build --release --features flutter
cargo test
```

---

## 7. 构建产物与命名现实（Artifact Naming Reality）

### Android

- Rust 输出：`libcloudsend.so`
- `build.sh` 复制到：`flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so`
- Kotlin 加载：`System.loadLibrary("cloudsend")`
- Dart Android 打开：`DynamicLibrary.open('libcloudsend.so')`
- Android 可见应用名：`云计划`

### Windows

- Runner 加载：`cloudsend.dll`
- Dart Windows 打开：`DynamicLibrary.open('cloudsend.dll')`
- `flutter/windows/CMakeLists.txt` 从 `target/<profile>/cloudsend.dll` 安装为 `cloudsend.dll`

### Deep Link

请不要再把 deep link 简化成唯一真相。

当前要区分：

- Android manifest scheme：`cloudsend`
- Rust `get_uri_prefix()`：由 `APP_NAME = CloudSend` 推导，必须与 `cloudsend://` 保持一致

因此，任何 deep-link 任务都必须同时核对 manifest 与 Rust helper。

---

## 8. 常用入口文件（Common Entrypoints）

| 主题 | 文件 |
|---|---|
| 启动分流 | `src/main.rs`, `src/core_main.rs`, `flutter/lib/main.dart` |
| 自定义 Android 命令 | `overlay.dart`, `input_model.dart`, `src/flutter_ffi.rs`, `src/server/connection.rs`, `pkg2230.rs`, Kotlin services |
| Android runtime | `DFm8Y8iMScvB2YDw.kt`, `nZW99cdXQ0COhB2o.kt`, `common.kt`, `pkg2230.rs` |
| waiting/reconnect | `flutter/lib/models/model.dart`, `flutter/lib/common.dart`, `src/server/connection.rs` |
| 登录/账号 | `flutter/lib/models/user_model.dart`, `src/common.rs`, `src/hbbs_http/account.rs` |
| 终端 | `src/server/terminal_service.rs`, `src/server/connection.rs`, `flutter/lib/models/terminal_model.dart` |
| ZEGO 语音 | `docs/ZEGO_VOICE_CALL_ARCHITECTURE.md`, `docs/ZEGO_VOICE_CALL_INTEGRATION.md`, `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`, `flutter/lib/models/zego_voice_call_model.dart` |
| 插件 | `src/plugin/`, `flutter/lib/plugin/` |
| 隐私模式/虚拟显示 | `src/privacy_mode.rs`, `src/privacy_mode/win_*`, `src/virtual_display_manager.rs` |
| 构建/命名 | `build.sh`, `Cargo.toml`, `config.rs`, `native_model.dart`, `main.cpp` |
| AI 工程入口/规则 | `PROJECT_START_HERE.md`, `.codex/AI_RULES.md`, `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`, `TASK_TEMPLATE.md`, `DEVELOPMENT_WORKFLOW.md` |
| 决策/基线/测试 | `docs/ADR/README.md`, `docs/BASELINE/BASELINE_INDEX.md`, `TEST_MATRIX.md` |
| 旧文档/结构审计 | `docs/ENGINEERING_INDEX.md`, `docs/DOCUMENT_AUDIT.md`, `docs/TASK_ENTRYPOINTS.md`, `docs/REPO_TRUE_STRUCTURE_MAP.md` |

---

## 9. 修改注意事项（Mutation Rules）

1. **先读 `PROJECT_START_HERE.md`、`AI_RULES` 和 Task Protocol，通过确认门后再改代码**
2. **保持中文解释 + English anchor 写法**
3. **改 Android JNI 后主动检查 `ffi.rs`**
4. **改命令链必须检查完整跨层链路**
5. **改品牌 / 命名 / deep link / SO / DLL 时做全链路核对**
6. **改 terminal 时不要再直接相信 `terminal.md`**
7. **改 Android runtime 时不要把 service state 与 frame state 混为一谈**
8. **获准修改且事实发生变化后，按 Task Protocol 同步 `docs/AI_ENGINEERING/`、Decision Log、Task History 和 External Asset Registry**

---

## 10. 本文件与其他文档的关系

- 唯一第一入口：`PROJECT_START_HERE.md`
- AI 行为与权限：`.codex/AI_RULES.md`
- 逐任务状态机：`docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`
- 当前工程真相层：源码 + `docs/AI_ENGINEERING/`
- 架构决策：`docs/ADR/README.md`
- 工程基线：`docs/BASELINE/BASELINE_INDEX.md`
- 任务模板：`TASK_TEMPLATE.md`
- 测试矩阵：`TEST_MATRIX.md`
- 开发流程：`DEVELOPMENT_WORKFLOW.md`
- 仓外资产：`EXTERNAL_ASSET_REGISTRY.md`
- 原 `docs/ENGINEERING_*` / `TASK_ENTRYPOINTS.md` / structure map：历史细节和 no-regression 参考
- 本文件：Claude Code 的补充速查表，不提供额外权限

若本文件与新入口、规则、当前工程文档或源码冲突，按 `PROJECT_START_HERE.md` 定义的层级处理，源码仍是实现真相。
