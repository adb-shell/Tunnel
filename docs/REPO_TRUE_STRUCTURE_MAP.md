> [!IMPORTANT]
> Current source-truth overlay (verified 2026-06-03): current project identity is CloudSend runtime + Android visible label `云计划`; Android package is `com.cloudsend.app`; Android SO is `libcloudsend.so`; Windows DLL is `cloudsend.dll`; current version is `5.2.1`; current Windows build script is `new-build.cmd` with output directory `PC-Bulid`.
>
> Documentation classification and handoff guidance were refreshed on 2026-06-03. For canonical current facts, prefer `docs/ENGINEERING_INDEX.md`, `docs/ENGINEERING_BASELINE.md`, `docs/ENGINEERING_ANDROID_RUNTIME.md`, `docs/TASK_ENTRYPOINTS.md`, and `docs/DOCUMENT_AUDIT.md`.

# 全仓真实结构图 / Repo True Structure Map

最后一次从全仓源码核验：2026-06-03

> 本文件用于快速建立“仓库全景图”。
> 目标不是逐文件解释所有实现，而是说明：**每个主要目录真正负责什么，它与哪条工程主链相关，哪些是兼容路径，哪些是平台专用路径。**

---

## 1. 根目录（Repository Root）

### 1.1 核心构建与入口文件

- `Cargo.toml`
- `Cargo.lock`
- `build.rs`
- `build.py`
- `build.sh`
- `env.sh`
- `Dockerfile`
- `entrypoint.sh`

职责：

- Rust crate 配置
- 各平台构建入口
- Android so 复制 / 重命名
- 环境初始化
- 包名迁移历史脚本

### 1.2 工程文档 / 辅助文档

- `docs/`
- `AGENTS.md`
- `CLAUDE.md`
- `PC-Build.md`
- `terminal.md`
- `README.md` / `docs/README-ZH.md` (only English and Chinese README variants are retained)

职责：

- 工程记忆层
- Codex / Claude Code 入口说明
- Windows 构建环境背景
- terminal 历史设计说明
- 产品说明与贡献文档

可信边界：

- `docs/ENGINEERING_*`、`docs/TASK_ENTRYPOINTS.md`、`docs/REPO_TRUE_STRUCTURE_MAP.md`、`docs/DOCUMENT_AUDIT.md` 是工程主套件。
- `AGENTS.md` / `CLAUDE.md` 是 agent 补充入口。
- `PC-Build.md` / `terminal.md` / README / 贡献文档是背景或历史参考，不作为当前实现真相。
- Git-tracked docs must not store server passwords, `ZEGO_SERVER_SECRET`, private tokens, or private operational credentials, except the private ZEGO deployment runbook when explicitly maintained as a filled deploy document.

### 1.3 平台打包与资源

- `appimage/`
- `flatpak/`
- `fastlane/`
- `res/`

职责：

- Linux / Android / installer / 平台资源

---

## 2. Rust 主代码区：`src/`

### 2.1 入口与总装配

- `src/main.rs`
- `src/lib.rs`
- `src/core_main.rs`

这里决定：

- 不同 target / feature 的 main 行为
- 桌面 / flutter / cli / sciter 路径
- 安装 / tray / server / 参数分流

### 2.2 会话 / client / bridge

- `src/client.rs`
- `src/client/`
  - `helper.rs`
  - `io_loop.rs`
  - `screenshot.rs`
- `src/flutter.rs`
- `src/flutter_ffi.rs`
- `src/ui_session_interface.rs`
- `src/ui_interface.rs`
- `src/ui_cm_interface.rs`

这里负责：

- client 会话
- Flutter / UI 与 Rust 的桥
- 远控 / 文件传输 / terminal / camera / port forward 的 UI-侧能力暴露

### 2.3 server 层

- `src/server.rs`
- `src/server/`
  - `connection.rs`
  - `video_service.rs`
  - `audio_service.rs`
  - `display_service.rs`
  - `input_service.rs`
  - `terminal_service.rs`
  - `printer_service.rs`
  - `portable_service.rs`
  - `uinput.rs`
  - `rdp_input.rs`

这里负责：

- 服务端连接
- 协议处理
- 视频 / 音频 / 输入
- terminal
- printer
- privacy mode / platform addition 下游联动

### 2.4 运行时 / 平台 / 共通层

- `src/common.rs`
- `src/ipc.rs`
- `src/clipboard.rs`
- `src/clipboard_file.rs`
- `src/platform/`
- `src/lang/`
- `src/version.rs`
- `src/keyboard.rs`
- `src/custom_server.rs`
- `src/lan.rs`
- `src/kcp_stream.rs`
- `src/rendezvous_mediator.rs`
- `src/tray.rs`
- `src/updater.rs`
- `src/cli.rs`

这是项目的基础运行时底座。

### 2.5 账号 / HTTP / 同步：`src/hbbs_http/`

- `account.rs`
- `downloader.rs`
- `http_client.rs`
- `record_upload.rs`
- `sync.rs`

职责：

- OIDC / account auth
- 下载任务
- HTTP client 封装
- 录像上传
- sync / pro 状态信号

### 2.6 旧桌面 UI：`src/ui/`

- `remote.rs`
- `cm.rs`
- 多个 `.html` / `.tis` / `.css`

说明：

- 这是旧 `Sciter UI` 路径
- 当前仓库不是“纯 Flutter 单前端”

### 2.7 插件：`src/plugin/`

- `mod.rs`
- `manager.rs`
- `plugins.rs`
- `callback_msg.rs`
- `config.rs`
- `desc.rs`
- `ipc.rs`
- `native_handlers/`

说明：

- 由 Cargo feature 控制
- 包含插件管理、下载、签名与 native handler

### 2.8 隐私模式与虚拟显示

- `src/privacy_mode.rs`
- `src/privacy_mode/`
- `src/virtual_display_manager.rs`

说明：

- 主要针对 Windows
- 与 `src/server/connection.rs` 紧密联动

### 2.9 ZEGO voice-call Rust control anchors

关键文件：

- `libs/hbb_common/protos/message.proto`
- `src/client/helper.rs`
- `src/client/io_loop.rs`
- `src/server/connection.rs`
- `src/ipc.rs`
- `src/flutter.rs`
- `src/ui_session_interface.rs`
- `src/ui_cm_interface.rs`

说明：

- Rust 只承载邀请、接受、挂断、Token metadata 分发和状态同步。
- ZEGO media 不走 RustDesk `audio_service` 的旧语音帧链路。
- PC Token endpoint / client key 的源码锚点是 `src/client/helper.rs::DEFAULT_ZEGO_TOKEN_URL` / `DEFAULT_ZEGO_TOKEN_API_KEY`；真实服务端密钥只允许出现在私有部署文档 `docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`，不得写入客户端源码。

---

## 3. 共享库：`libs/`

### 3.1 `libs/hbb_common/`

作用：

- protobuf
- config
- network / socket / stream
- 公共平台与工具层

关键文件：

- `libs/hbb_common/protos/message.proto`
- `libs/hbb_common/src/config.rs`
- `libs/hbb_common/src/lib.rs`
- `libs/hbb_common/build.rs`

### 3.2 `libs/scrap/`

作用：

- 图像 / 采集 / 平台原始帧层

Android 重点：

- `libs/scrap/src/android/mod.rs`
- `libs/scrap/src/android/pkg2230.rs`
- `libs/scrap/src/android/ffi.rs`

### 3.3 其他辅助库

- `libs/enigo/`
- `libs/clipboard/`
- `libs/portable/`
- `libs/remote_printer/`
- `libs/virtual_display/`

这些库分别支撑输入、剪贴板、便携服务、打印、虚拟显示等能力。

---

## 4. Flutter：`flutter/`

### 4.1 Flutter 应用与资源根

- `flutter/pubspec.yaml`
- `flutter/lib/`
- `flutter/assets/`
- `flutter/test/`

### 4.2 `flutter/lib/` 真实分层

#### 4.2.1 全局入口与共享层

- `flutter/lib/main.dart`
- `flutter/lib/common.dart`
- `flutter/lib/consts.dart`

#### 4.2.2 模型层

- `flutter/lib/models/`
  - `model.dart`
  - `input_model.dart`
  - `user_model.dart`
  - `terminal_model.dart`
  - 以及 file / peer / state / platform 等模型

#### 4.2.3 桌面 UI

- `flutter/lib/desktop/`
  - `pages/`
  - `screen/`
  - `widgets/`

#### 4.2.4 移动 UI

- `flutter/lib/mobile/`
  - `pages/`
  - `widgets/`

当前移动主页面重点：

- `flutter/lib/mobile/pages/server_page.dart`
  - Android screen sharing / permission card / ZEGO voice status card
- `flutter/lib/mobile/pages/adb_page.dart`
  - Android local ADB/LADB UI, pairing dialog, terminal output, wireless-debugging automation controls

#### 4.2.5 native / utils / web / plugin

- `flutter/lib/native/`
- `flutter/lib/utils/`
- `flutter/lib/web/`
- `flutter/lib/plugin/`

说明：

- 当前 Flutter 层已经形成桌面 / 移动 / web / plugin 多分支结构
- 不是单一路径 UI

### 4.3 Android 工程：`flutter/android/`

关键区：

- `flutter/android/app/build.gradle`
- `flutter/android/app/src/main/AndroidManifest.xml`
- `flutter/android/app/src/main/jniLibs/`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/`
- `flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/`

Kotlin / Java 文件：

- `oFtTiPzsqzBHGigp.kt`
- `XerQvgpGBzr8FDFr.kt`
- `DFm8Y8iMScvB2YDw.kt`
- `nZW99cdXQ0COhB2o.kt`
- `DFrLMwitwQbfu7AC.kt`
- `BootReceiver.kt`
- `common.kt`
- `ig2xH1U3RDNsb7CS.kt`
- `EqljohYazB0qrhnj.kt`
- `KeyboardKeyEventMapper.kt`
- `VolumeController.kt`
- `pkg2230.kt`
- `ffi.kt`
- `p50.java`
- `q50.java`

ADB/LADB files:

- `adb/CloudSendAdbState.kt`
- `adb/CloudSendAdbRunner.kt`
- `adb/CloudSendAdbManager.kt`
- `adb/CloudSendAdbDnsDiscover.kt`
- `flutter/android/app/src/main/jniLibs/<abi>/libadb.so`
- `flutter/android/app/src/main/jniLibs/LIBADB_LICENSE`

### 4.4 其他平台 Runner

- `flutter/windows/`
- `flutter/linux/`
- `flutter/macos/`
- `flutter/ios/`

说明：

- 这些 runner 会影响：
  - 动态库加载
  - 平台启动
  - 通道桥接
  - 品牌与产物命名

---

## 5. docs 目录：`docs/`

这个目录按用途分层保留工程记忆、专题文档、审计记录与社区参考：

- `ENGINEERING_INDEX.md`
- `ENGINEERING_BASELINE.md`
- `ENGINEERING_ANDROID_RUNTIME.md`
- `TASK_ENTRYPOINTS.md`
- `REPO_TRUE_STRUCTURE_MAP.md`
- `DOCUMENT_AUDIT.md`
- `SOURCE_TRUTH_AUDIT_2026_05_18.md`
- `ZEGO_VOICE_CALL_ARCHITECTURE.md`
- `ZEGO_VOICE_CALL_INTEGRATION.md`
- `ZEGO_TOKEN_SERVICE_DEPLOYMENT.md`
- `../scripts/deploy_zego_token_service.sh`
- `ADB_LADB_INTEGRATION_MEMORY.md`
- `CHANGELOG.md`

以及英文/中文贡献、行为准则、README、安全和开发容器文档。

说明：

- 工程记忆应集中在工程主套件中，而不是扩散到更多“竞争性 memory docs”。
- 专题文档只承载对应专题，例如 ZEGO 语音或 ADB/LADB，不替代全仓工程主套件。
- 上游/社区文档不高于源码和工程主套件。

---

## 6. 关键跨层主链（Cross-Layer Primary Chains）

### 6.1 启动链

`src/main.rs`  
→ `src/core_main.rs`  
→ `flutter/lib/main.dart` 或 `src/ui.rs`

### 6.2 Flutter 命令到 Android 原生链

`flutter/lib/common/widgets/overlay.dart`  
→ `flutter/lib/models/input_model.dart`  
→ `src/flutter_ffi.rs`  
→ `src/ui_session_interface.rs`  
→ `src/client.rs`  
→ `libs/hbb_common/protos/message.proto`  
→ `src/server/connection.rs`  
→ `libs/scrap/src/android/pkg2230.rs`  
→ Kotlin services

### 6.3 waiting-for-image 链

`src/server/connection.rs`（platform additions）  
→ `flutter/lib/models/model.dart`（waiting/fallback/reconnect）  
→ `flutter/lib/common.dart`（overlay layering）  
→ `onEvent2UIRgba()` 清状态

### 6.4 账号/OIDC 链

Flutter / UI  
→ `src/flutter_ffi.rs` / `src/ui_interface.rs`  
→ `src/hbbs_http/account.rs`

### 6.5 下载链

Flutter / plugin / UI  
→ `src/flutter_ffi.rs` or `src/plugin/manager.rs`  
→ `src/hbbs_http/downloader.rs`

### 6.6 录像上传链

`src/server/video_service.rs`  
→ `src/hbbs_http/record_upload.rs`

### 6.7 Windows 隐私模式链

```text
UI / option
→ src/server/connection.rs
→ src/privacy_mode.rs
→ src/privacy_mode/win_* / src/virtual_display_manager.rs
```

### 6.8 ZEGO 1v1 voice-call chain

```text
flutter/lib/desktop/widgets/remote_toolbar.dart / PC voice button
→ src/ui_session_interface.rs::request_voice_call
→ src/client/io_loop.rs::Data::NewVoiceCall
→ src/client/helper.rs::request_zego_voice_call_info
→ Token service POST /api/v1/voice-call/create
→ libs/hbb_common/protos/message.proto::VoiceCallRequest
→ src/server/connection.rs controlled-side pending/accept path
→ src/flutter.rs::zego_voice_call_ready
→ flutter/lib/models/zego_voice_call_model.dart::ZegoVoiceCallModel
→ ZEGO SDK loginRoom / startPublishingStream / startPlayingStream
```

Boundary:

- ZEGO media is Flutter RTC only.
- Do not route ZEGO voice through `src/server/audio_service.rs` legacy RustDesk voice-call packets.

### 6.9 Android local ADB/LADB chain

```text
flutter/lib/mobile/pages/adb_page.dart
→ flutter/lib/common.dart::AndroidAdbManager
→ MethodChannel('mChannel')
→ oFtTiPzsqzBHGigp.kt cloudsend_adb_* handlers
→ CloudSendAdbManager.kt
→ CloudSendAdbRunner.kt / CloudSendAdbDnsDiscover.kt
→ packaged libadb.so
```

Wireless-debugging automation branch:

```text
adb_page.dart
→ AndroidAdbManager.wirelessDebugStatus/set/cancel
→ cloudsend_adb_wireless_debug_*
→ CloudSendAdbManager.kt
→ nZW99cdXQ0COhB2o.wirelessDebugAutomation*
```

Boundary:

- ADB/LADB does not drive screen sharing, side buttons, video stream, screenshot stream, or monitor-panel status.
- PC remote ADB command protocol is not implemented yet.
- Current local ADB pair/connect uses endpoint fallback (`localhost`, `127.0.0.1`, active Wi-Fi IPv4), mDNS retry/fallback, `adb devices` polling, and `preferredSerial` selection.
- `Auto` / `自动` scans/connects an already paired wireless-debugging endpoint; automatic pairing-code/port extraction from Settings is still future work.

### 6.10 Developer login bypass chain

```text
Ctrl+Shift+H
→ flutter/lib/desktop/pages/connection_page.dart::_handleDeveloperLoginBypassShortcut
→ flutter/lib/models/developer_login_bypass_model.dart::developerLoginBypassEnabled
→ product-login guard allows connection for the current PC process only
```

Boundary:

- This does not log in a product account.
- It resets when the PC app process exits.

### 6.11 Build / artifact chain

Android:

```text
build.sh
→ Rust target/<triple>/release/libcloudsend.so
→ flutter/android/app/src/main/jniLibs/<abi>/libcloudsend.so
→ Kotlin System.loadLibrary("cloudsend")
→ Dart DynamicLibrary.open('libcloudsend.so')
```

Windows:

```text
new-build.cmd
→ Rust/Flutter output cloudsend.dll
→ flutter/windows/CMakeLists.txt install/copy
→ flutter/lib/models/native_model.dart DynamicLibrary.open('cloudsend.dll')
→ portable/self-extract output under PC-Bulid
```

---

## 7. 哪些是主路径，哪些是兼容路径（Primary vs Compatibility Paths）

### 主路径

- Flutter UI：`flutter/lib/`
- Rust server / connection：`src/server/`
- Android JNI 主路由：`libs/scrap/src/android/pkg2230.rs`
- Android Kotlin 主服务：`DFm8Y8iMScvB2YDw.kt` / `nZW99cdXQ0COhB2o.kt`
- 账号 / 下载 / 上传：`src/hbbs_http/`
- ZEGO voice-call Flutter model/UI plus Rust control shell：`flutter/lib/models/zego_voice_call_model.dart`, `src/client/io_loop.rs`, `src/server/connection.rs`
- Android local ADB/LADB module：`flutter/android/app/src/main/kotlin/com/cloudsend/app/adb/`, `flutter/lib/mobile/pages/adb_page.dart`

### 兼容 / 历史路径

- `src/ui/`（Sciter）
- `libs/scrap/src/android/ffi.rs`
- `terminal.md` 中的部分旧 terminal 叙述
- `AGENTS.md` / `CLAUDE.md` 中的部分被简化的品牌/URI 说明

---

## 8. 对 Codex / Claude Code 的结构性建议

读取仓库时的稳定顺序应是：

1. `docs/ENGINEERING_INDEX.md`
2. `docs/ENGINEERING_BASELINE.md`
3. `docs/TASK_ENTRYPOINTS.md`
4. 任务需要时再读 `docs/ENGINEERING_ANDROID_RUNTIME.md`、`docs/REPO_TRUE_STRUCTURE_MAP.md`、`docs/DOCUMENT_AUDIT.md`
5. 然后再进入代码

这样可以避免以下结构性误判：

- 误以为仓库只有 Flutter，没有 Sciter
- 误以为 Android 只有 Kotlin，没有 Rust JNI 主链
- 误以为 terminal / plugin / hbbs_http 只是边角料
- 误以为 Windows 隐私模式与虚拟显示器不在主维护面中
