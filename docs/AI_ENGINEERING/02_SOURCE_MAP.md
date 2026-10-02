# Tunnel 源码地图 / Source Map

> 2026-10-03 当前增量（T-2026-10-03-001 / V0）：新增 `android-adb/`、`adb/mirror/TunnelAdbSession.kt` / `TunnelAdbRuntime.kt`、`AccessibilityLifecycle.kt`、`src/server/android_control.rs`、`libs/scrap/src/android/encoded.rs`、Flutter `android_mode_model.dart` / `android_adb_menu.dart` / `adb_remote_consent_card.dart`。完整路径见指南第2节；原P0地图是历史阶段。 [实现、构建与验收](../plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。以下2026-10-02及更早的阶段描述以本增量和当前源码为准。

最近关键路径复核：2026-10-02，`HEAD 5cee692` / V0。按功能和接口定位见 [12_FEATURE_MAP.md](12_FEATURE_MAP.md)，覆盖边界见 [本轮审计](audits/2026-10-02/README.md)。

## 1. 状态标签

- `PRIMARY`：当前运行主路径。
- `COMPAT`：兼容/遗留路径，仍需回归关注。
- `DORMANT`：实现存在但默认不可达或 feature-gated。
- `GENERATED`：由工具生成，不应手工维护。
- `LOCAL-ONLY`：存在于当前机器但不受 Git 管理。
- `MISSING`：当前工作区没有所需文件；不能从 ignore 规则推断为本机存在。
- `EXTERNAL`：本仓只有 client/contract，没有实现。

## 2. 根目录

| 路径 | 状态 | 作用 |
|---|---|---|
| `Cargo.toml`, `Cargo.lock` | PRIMARY | root crate、workspace、feature、依赖锁定 |
| `build.rs` | PRIMARY | 生成 `src/version.rs`、native link/build |
| `build.sh`, `env.sh` | PRIMARY/Android | Linux Android 环境与构建编排 |
| `new-build.cmd` | PRIMARY/Windows | 当前 `C:\DevEnv` + `C:\DevTool` PC 构建 |
| `build.cmd` | COMPAT | 旧Windows环境入口 |
| `build.py` | PRIMARY Windows sub-orchestration / conditional other platforms | `new-build.cmd`会调用；其他平台分支保留，部分命名漂移待验证 |
| `.github/workflows/` | COMPAT | 上游多平台 Actions；manual dispatch，部分还有 workflow_call；无 push/PR 自动 gate |
| `res/`, `appimage/`, `flatpak/`, `fastlane/` | PLATFORM | installer、driver、icons、store metadata |
| `.info` | PRIMARY build config | `new-build.cmd` 读取的配置输入；具体值不进入接管文档 |

`src/version.rs` 不是跟踪源码，由 `hbb_common::gen_version()` 在 build script 阶段生成。

## 3. Rust workspace

| Crate | 关键目录 | 职责 |
|---|---|---|
| `tunnel` | `src/` | controller、endpoint、FFI、平台与产品集成 |
| `hbb_common` | `libs/hbb_common/` | protobuf、config、crypto、socket、fs |
| `scrap` | `libs/scrap/` | capture、codec、Android raw/JNI |
| `enigo` | `libs/enigo/` | desktop input injection |
| `clipboard` | `libs/clipboard/` | clipboard/CLIPRDR/file clipboard |
| `virtual_display` | `libs/virtual_display/` | RustDesk IDD wrapper |
| `dylib_virtual_display` | `libs/virtual_display/dylib/` | virtual display DLL ABI |
| `tunnel-portable-packer` | `libs/portable/` | Windows self-extracting package |
| `remote_printer` | `libs/remote_printer/` | Windows printer/driver integration |

`workspace.exclude` 指向的 `vdi/host`、`examples/custom_plugin` 当前不存在，是 manifest 历史残留。

## 4. `src/` 主地图

### 启动与装配

- `src/main.rs`：target/feature main 分流。
- `src/lib.rs`：模块 feature/cfg 总装配。
- `src/core_main.rs`：desktop args、install、tray、server、CM、elevation。
- `src/service.rs`, `src/tray.rs`, `src/cli.rs`：平台/CLI 入口。

### Controller

- `src/client.rs`：连接、login config、媒体线程、输入编码、Interface/Data。
- `src/client/io_loop.rs`：session 消息循环、解码/文件/ZEGO/terminal 事件。
- `src/client/helper.rs`：协议构造、ZEGO Token client。
- `src/client/file_trait.rs`, `screenshot.rs`：文件与截图辅助。

### Controlled endpoint

- `src/rendezvous_mediator.rs`：ID/PK 注册、relay/direct/LAN/NAT、sync 启动。
- `src/server.rs`：service registry 与连接接入。
- `src/server/connection.rs`：认证和协议总分发。
- `video_service.rs`, `display_service.rs`, `video_qos.rs`：capture/encode/display/QoS。
- `input_service.rs`, `rdp_input.rs`, `uinput.rs`：输入执行。
- `clipboard_service.rs`, `audio_service.rs`：数据服务。
- `terminal_service.rs`, `printer_service.rs`, `portable_service.rs`：平台服务。

### Bridge/UI

- `src/flutter.rs`：session registry、Rust→Dart event、texture/RGBA。
- `src/flutter_ffi.rs`：手写 `main_*`/`session_*`/`cm_*` 门面。
- `src/bridge_generated*.rs`：GENERATED，FRB 1.80.1。
- `src/ui_session_interface.rs`, `ui_interface.rs`, `ui_cm_interface.rs`：UI 领域接口。
- `src/ui.rs`, `src/ui/`：COMPAT Sciter UI。

### Product integration

- `src/hbbs_http/account.rs`：OIDC。
- `downloader.rs`：download/progress/cancel。
- `sync.rs`：heartbeat/config/disconnect。
- `record_upload.rs`：DORMANT record upload。
- `http_client.rs`：reqwest client。

### Platform

- `src/platform/`：Windows/Linux/macOS。
- `src/privacy_mode.rs`, `src/privacy_mode/win_*`：Windows privacy。
- `src/virtual_display_manager.rs`：Amyuni/RustDesk IDD abstraction。
- `src/port_forward.rs`, `clipboard*.rs`, `ipc.rs`, `common.rs`：横切能力。
- `src/plugin/`：DORMANT/feature-gated plugin framework。

## 5. Flutter 地图

| 路径 | 作用 |
|---|---|
| `flutter/lib/main.dart` | desktop/mobile、多窗口类型启动 |
| `flutter/lib/common.dart` | 全局服务、dialog/overlay/channel、共享状态 |
| `flutter/lib/consts.dart` | protocol/platform/UI constants |
| `flutter/lib/models/` | session、server、input、file、terminal、user、AB、group、ZEGO |
| `flutter/lib/desktop/pages/` | connection、remote、file、terminal、settings |
| `flutter/lib/desktop/widgets/` | toolbar、tab、status、controls |
| `flutter/lib/mobile/pages/` | server、connection、remote、file、ADB |
| `flutter/lib/common/widgets/` | dialog、overlay、input、peer cards |
| `flutter/lib/native/`, `utils/` | platform/FFI utilities |
| `flutter/lib/web/` | web bridge；多处 TODO/DORMANT |
| `flutter/lib/plugin/` | plugin UI |
| `flutter/lib/generated_bridge*.dart` | GENERATED，需与 `flutter_ffi.rs` 同步 |

状态管理不是单一框架：Provider、GetX Rx、全局 singleton 和 engine-local state 并存。

## 6. Android 地图

### Kotlin/Java

除特别说明外，下表业务类位于 `flutter/android/app/src/main/kotlin/com/tunnel/app/`；`pkg2230.kt` 和 `ffi.kt` 实际位于 `flutter/android/app/src/main/kotlin/` 根目录。

| 文件 | 角色 |
|---|---|
| `oFtTiPzsqzBHGigp.kt` | `FlutterActivity`、MethodChannel、deep link、ADB handler |
| `XerQvgpGBzr8FDFr.kt` | 透明权限 Activity、fresh `MediaProjection` intent |
| `DFm8Y8iMScvB2YDw.kt` | `MainService`、core keep-alive、normal capture、ZEGO bridge |
| `nZW99cdXQ0COhB2o.kt` | Accessibility、input、screenshot、overlay、Dev/ADB automation |
| `DFrLMwitwQbfu7AC.kt` | floating keep-alive service |
| `BootReceiver.kt` | boot core-service path |
| `DevAutoSelectorController.kt` | 微信 Dev 自动点选 |
| `common.kt` | `SKL`、`shouldRun`、`BIS`、screen globals |
| `KeyboardKeyEventMapper.kt`, `VolumeController.kt` | Android input helpers |
| `ig2xH1U3RDNsb7CS.kt` | clipboard bridge |
| `EqljohYazB0qrhnj.kt` | image/node helper |
| `pkg2230.kt` | PRIMARY JNI declarations |
| `ffi.kt` | COMPAT JNI declarations |
| `p50.java`, `q50.java` | XOR/obfuscation helper |

### ADB module

- `adb/TunnelAdbManager.kt`：facade/state。
- `TunnelAdbRunner.kt`：`libadb.so` process、pair/connect/shell。
- `TunnelAdbDnsDiscover.kt`：NSD discovery/fallback。
- `TunnelAdbState.kt`：snapshot。
- `flutter/lib/mobile/pages/adb_page.dart`：UI。
- `jniLibs/*/libadb.so`：当前 MISSING；旧接管曾记为 LOCAL-ONLY，当前整个 `jniLibs/` 目录不存在。provenance 与获取方式见 External Asset Registry。

### Rust JNI

- `libs/scrap/src/android/mod.rs` 只导出 `pkg2230`。
- `pkg2230.rs` 是 PRIMARY；`ffi.rs` 未编译且不是精确镜像。
- core JNI 另位于 `src/flutter_ffi.rs::server_side`，包括 `xt4P9mWE`、`G4yQ9OYY`、`qR9Ofa6G` 的 exports；不能把所有 active JNI 都局限在 scrap 模块。
- 两文件不是精确镜像；显式 JNI context destroy 等逻辑有差异，不能依据旧 diff 行数判断是否同步。具体 active exports 与差异见本轮 Android/Flutter 审计。

## 7. Windows 地图

- capture：`libs/scrap/src/dxgi/`，包括 `gdi.rs` 和 `mag.rs`。
- platform：`src/platform/windows.rs`, `windows.cc`。
- input：`src/server/input_service.rs`, `libs/enigo/src/win/`。
- privacy：`src/privacy_mode.rs`, `src/privacy_mode/win_*`。
- virtual display：`src/virtual_display_manager.rs`；当前 Amyuni。
- driver/package：`res/vcpkg/`, `res/msi/`, external `usbmmidd_v2`, printer driver。
- runner/library：`flutter/windows/runner/`, `tunnel.dll`。

## 8. Protocol 与 API 地图

- `libs/hbb_common/protos/rendezvous.proto`：registration/punch/relay/NAT。
- `message.proto`：session/video/input/file/terminal/ZEGO/permission。
- `libs/hbb_common/build.rs`：protobuf generation。
- Flutter API：`user_model.dart`, `ab_model.dart`, `group_model.dart`, `common/hbbs/`。
- Rust API：`src/hbbs_http/`。
- backend/db：EXTERNAL，不在仓库。

## 9. 风险热点优先阅读

| 风险 | 第一批文件 |
|---|---|
| 网络/crypto | `src/client.rs`, `rendezvous_mediator.rs`, `hbb_common/src/tcp.rs`, `password_security.rs` |
| Android frame | `DFm8Y8iMScvB2YDw.kt`, `pkg2230.rs`, `video_service.rs` |
| Android input/auth | `connection.rs`, `input_service.rs`, `nZW99cdXQ0COhB2o.kt` |
| waiting/reconnect | `model.dart`, `server_model.dart`, `flutter.rs` |
| API/token | `user_model.dart`, `hbbs_http/`, `client/helper.rs` |
| Windows privacy | `privacy_mode.rs`, `win_*`, `virtual_display_manager.rs` |
| build/release | `new-build.cmd`, `build.sh`, `build.py`, workflows |

## 10. 生成与可复现性规则

- 不手改 generated bridge；改 `flutter_ffi.rs` 后在正式环境重新生成并检查 diff。
- `src/version.rs` 必须由 build script 生成。
- MISSING/LOCAL-ONLY/EXTERNAL ADB、driver、assets 必须先进入受控 artifact manifest，不能依赖个人机器或旧 hash 记录。
- Git dependency 应同时记录 manifest source 与 lock revision。
- clean clone 复现是 release gate；当前尚未满足。

## 11. ADB P0 原型新增地图（2026-10-02）

| 路径 | 状态 | 作用 |
|---|---|---|
| `android-helper/protocol/` | DORMANT / source only | APK/helper共享认证framing、JUnit源码 |
| `android-helper/server/` | DORMANT / source only | 固定来源shell H264原型、NOTICE/provenance |
| `android-helper/build_helper.py` | NOT_RUN | 显式Java/D8构建、hash manifest和首次stage |
| Android `adb/probe/`、`adb/mirror/` | DORMANT / default off | 本机UID探测、artifact校验、有限时supervisor |
| `flutter/lib/mobile/widgets/adb_mirror_probe_card.dart` | DORMANT / default off | 本机诊断计数、生命周期取消 |

本地MethodChannel位于FlutterActivity，不新增远程协议或JNI路径。源码变化来自T003；[验证交接](../plans/ADB_P0_VALIDATION_RUNBOOK.md)记录真实完成范围及未验证项。
