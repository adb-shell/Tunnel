# CloudSend 模块设计 / Module Design

基线：2026-07-12，`HEAD 77062b4`

## 1. 启动与进程模型

`src/main.rs` 按 target/feature 分流；desktop 非 Flutter 走 `core_main()` + Sciter，Flutter 构建主要加载 `cloudsend` library。`src/core_main.rs` 继续处理 install、tray、server、CM、elevation、quick support 和 portable 参数。`flutter/lib/main.dart` 再按 window argument 启动 main/remote/file/terminal/port-forward/install/mobile。

设计含义：一次“启动问题”可能跨 Rust process args、native runner、Flutter engine 和 multi-window。不能只看 `main.dart`。

## 2. Connection 与 Service registry

`Server::new()` 注册 display/audio/clipboard/input 等服务；每个 `Connection` 负责：

1. stream handshake。
2. login request、password/hash、approve/2FA/trusted device。
3. permission 与 service subscription。
4. `Message`/`Misc` 分发。
5. cleanup、connection manager 与平台状态回收。

`src/server/connection.rs` 已接近 5k 行，是远控协议 God object。任何修改必须按消息类别确认 sender、receiver、权限、cleanup 和兼容版本。

## 3. Controller session

`LoginConfigHandler` 保存 ID、conn type、session options、password、codec、relay/direct 信息；`Client` 完成连接和 secure handshake；`client/io_loop.rs` 持有运行时状态机；`FlutterSession` 对 UI 暴露 event/texture。

当前 controller 强制 relay。这个 product decision 不等于 controlled endpoint 删除 direct code。

## 4. 视频模块

```text
display_service/video_service
→ scrap::Capturer
→ platform frame / Android FrameRaw
→ codec negotiation + QoS
→ VideoFrame
→ client decoder
→ Flutter texture/RGBA
```

能力：多 codec、hardware codec feature、display switch、quality/fps、ack、recording、camera、screenshot。

债务：capture/codec/renderer 状态分散；Android raw lifetime 需独立验证；`FrameRaw.force_next`、`VIDEO_RAW`、`PIXEL_SIZE*` 是脆弱全局不变量。

## 5. 输入模块

Controller 由 `input_model.dart`、`flutter_ffi.rs`、`client::send_mouse` 编码；endpoint 在 `Connection::on_message()` 分发到 `input_service`；desktop 通过 enigo/portable service，Android 通过 JNI → Accessibility。

CloudSend 自定义 Android 命令复用 mouse mask/url 通道，包括 blank、browser、analysis、back、share、touch-block、Dev selector。它们是协议命令，不是纯 UI 操作。

安全边界：UI 密码/按钮可见性不是协议授权；endpoint 必须独立检查 session permission。当前部分 Mouse/Touch/Key 和自定义 mask 未形成完整 server-side gate，列为 P0/P1。

## 6. Clipboard 与文件模块

- 文本/多格式 clipboard：`Clipboard`, `MultiClipboards`。
- Windows file clipboard：CLIPRDR + `libs/clipboard`。
- 文件传输：`FileAction`/`FileResponse` + `hbb_common::fs::TransferJob`。

已有 block/digest/zstd、cancel、conflict confirm、`.download` 完成切换和 mtime。受控端文件访问没有独立 session root sandbox，边界依赖远控认证与 file permission。

## 7. Terminal 与 port forward

- Terminal protocol：`OpenTerminal`, `TerminalData`, `ResizeTerminal`, `CloseTerminal`。
- Server：`src/server/terminal_service.rs`。
- Flutter：`terminal_model.dart`, `terminal_*` pages。
- 当前 service ID：`ts_<uuid>`。

`terminal.md` 中 `tmp_`/`persist_` 属历史设计，不能解释当前生命周期。Port forward 由 `src/port_forward.rs` 和 session interface 管理。

## 8. Android runtime 模块

- `MainService`：core/JNI、foreground keep-alive、normal capture、MethodChannel relay。
- `AccessibilityService`：input、overlay、screenshot/ignore、Dev/ADB automation。
- Rust JNI：raw buffers、pixel gates、command dispatch。
- Flutter：permission UI、session waiting、status/reconnect。

这不是单 Kotlin 模块，而是三语言状态机。详见 `04_ANDROID_PIPELINE.md`。

## 9. Android local ADB

```text
adb_page.dart
→ MethodChannel('mChannel')
→ oFtTiPzsqzBHGigp handlers
→ CloudSendAdbManager
→ Runner / DNS discover / Accessibility automation
→ local libadb.so process
```

当前支持 manual pair/connect、endpoint fallback、NSD retry、preferred serial、shell restart cap、best-effort wireless-debug Settings automation。PC remote ADB protocol 未实现。

`libadb.so` 为本地 ignored asset，构建可复现性和来源清单未闭环。

## 10. Windows privacy/virtual display

Privacy abstraction 支持 exclude/topmost、Magnifier、virtual display。当前 virtual display 选择 Amyuni，最多 monitor 数和 plug/unplug 通过 driver/IOCTL 管理。注入路径使用 helper process、remote memory/APC 和 low-level hooks。

它是高权限平台主模块，不是实验代码。任何失败都必须定义恢复显示、恢复输入、清 driver state 的回滚路径。

## 11. Product account/API 模块

两条登录路径：

- Flutter normal login/current user/expiry/UUID：`user_model.dart` 直接访问 `/api/*`。
- Rust OIDC：`hbbs_http/account.rs` device auth/query。

地址簿/设备组由 Flutter models 直接调用 API；sync/heartbeat 在 Rust endpoint；backend/database 均在仓外。

`src/common.rs::verify_login()` 的 unconditional `true` 不等于 endpoint password authentication 被删除，但说明 legacy/custom-client 产品准入不能作为安全边界。

## 12. ZEGO voice 模块

- Rust：创建 metadata、邀请、pending/active timestamp、accept/close/timeout。
- Flutter：engine lifecycle、room login、publish/play、first-audio-frame diagnostics。
- Android：incoming state → foreground/full-screen UI → 3 秒自动 accept → microphone permission → join。

旧 `audio_service` 不应被 ZEGO 重新启用。当前自动接听/无 reject UX、HTTP Token 与客户端 key 是安全/隐私风险，不是普通 UI 细节。

## 13. Config、crypto 与 local persistence

`hbb_common::config` 使用 confy/config files 保存 ID、options、password/trusted devices；`password_security` 以设备 UUID 派生 secretbox key。nonce 固定和 key 可预测性意味着它更接近本机混淆，不应作为抵御本地攻击者的强机密存储。

Flutter API token、cache 和 model state 经 native local options/JSON cache 保存；仓库没有统一 secrets abstraction。

## 14. Plugin、CLI 与 legacy UI

- Plugin：feature-gated，不能默认认定发布包启用。
- CLI：静态接口漂移，待 `cargo check --features cli`。
- Sciter：保留启动/兼容逻辑，但新产品 UI 主路径是 Flutter。
- Web bridge：多处 TODO；不是 desktop/mobile 能力的等价实现。

## 15. 模块维护准则

1. 先标明 active/compat/dormant/external。
2. 为每个状态定义 owner、创建、转移、cleanup。
3. 跨层命令必须画 sender→protocol→receiver→platform 全链。
4. UI 可见性永不替代 endpoint authorization。
5. raw pointer、JNI、WinAPI、driver、injection 需要单独 safety review。
6. 新增 API 必须记录 auth、transport、timeout、retry、idempotency、redaction。
7. 任何恢复路径不得借“重启服务”掩盖状态机错误。
