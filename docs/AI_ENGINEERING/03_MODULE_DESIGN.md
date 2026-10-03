# Tunnel 模块设计 / Module Design

2026-10-03 T008：配对弹窗首次配对/已配对连接分离，手动连接端口折叠；InputModel本地dialog引用计数释放并阻止远控键盘抓取，关闭后由画布焦点恢复。按钮与mode/pairing可用性同源且显示原因，12秒无手机ACK与90秒执行超时分别报告并等待取消确认。状态面板仅ADB/投屏模式，错误放tooltip。见[修复记录](../plans/ADB_RELIABILITY_REPAIR_TASK.md)，构建/交互待验。

2026-10-03 T006：common.dart::DialogBuilder要求返回CustomAlertDialog；配对窗使用私有_AndroidAdbPairingOverlay子类override build承载原StatefulWidget，保留OverlayDialogManager生命周期和全窗口拖动约束。不能将含LayoutBuilder的完整窗口直接嵌入AlertDialog intrinsic content。修复用户Flutter3.24.5返回类型编译错误；源码/V0，重编待验。[任务](../plans/ADB_PAIRING_DIALOG_BUILD_FIX_TASK.md)。

2026-10-03 T004当前ADB：RemoteAdbPairing单后台worker与独立pairing状态；PC主动请求、端fresh probe后当前conn会话scopes（T005/ADR-0017无固定期限；helper protocol3 duration=0；断连/撤销/退出ADB清理）；旧手机页移除。本机consent旧设计由[ADR-0016](../ADR/0016-pc-owned-adb-pairing-and-session-consent.md)限定替代；视频epoch/帧事务保留。

> 2026-10-03 当前增量（T-2026-10-03-001 / V0）：本地ADB Runner改为有限子进程与共享transport lease；Runtime持本机consent与唯一input/source所有权；Server endpoint再次验证加密、授权、键盘权限、订阅及owner；Dart状态按FFI窗口隔离。 [实现、构建与验收](../plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。以下2026-10-02及更早的阶段描述以本增量和当前源码为准。

原始基线：2026-07-12，`HEAD 77062b4`（historical）

Rust / Network / Windows 复核：2026-10-02，`HEAD 5cee692`，V0 静态源码审计

本轮证据：[RUST_NETWORK_WINDOWS_AUDIT.md](audits/2026-10-02/RUST_NETWORK_WINDOWS_AUDIT.md)。未执行 build/test 或运行验证；未复核段落需结合对应领域报告使用。

## 1. 启动与进程模型

`src/main.rs` 按 target/feature 分流；desktop 非 Flutter 走 `core_main()` + Sciter，Flutter 构建主要加载 `tunnel` library。`src/core_main.rs` 继续处理 install、tray、server、CM、elevation、quick support 和 portable 参数。`flutter/lib/main.dart` 再按 window argument 启动 main/remote/file/terminal/port-forward/install/mobile。

设计含义：一次“启动问题”可能跨 Rust process args、native runner、Flutter engine 和 multi-window。不能只看 `main.dart`。

## 2. Connection 与 Service registry

`src/server.rs::new()` 是构造 `ServerPtr` 的 free function，注册 audio/display/clipboard 与按平台启用的 cursor/position/focus 服务；`windows + flutter` 且 adapter 初始化成功时才注册 printer。视频服务由 `Server::try_add_primay_video_service()` / `try_add_primary_camera_service()` 按需增加；terminal 的 `GenericService` 由 connection 独立创建。每个 `Connection` 负责：

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

Tunnel 自定义 Android 命令复用 mouse mask/url 通道，包括 blank、browser、analysis、back、share、touch-block、Dev selector。它们是协议命令，不是纯 UI 操作。

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

当前确实有 persistent terminal：`LoginRequest.Terminal.service_id` 与 `OptionMessage.terminal_persistent` 进入 `Connection::init_terminal_service()`，使用进程内 `TERMINAL_SERVICES` registry；空 ID 才生成 `ts_<uuid>`。`TerminalServiceProxy::handle_open()` 可复用已有 terminal，controller 将返回的 service ID 存入 peer option `terminal-service-id`。这是同一 endpoint 进程内的断连保留，未实现跨进程重启恢复。

`terminal_service::run()` 退出会删除非 persistent service；cleanup 每约 5 分钟检查，非 persistent 空闲阈值为 1 小时，persistent 且无 terminal 的阈值为 2 小时。已有 output buffer / channel 上限不等于所有服务、terminal 数或输入都有完整限额。`terminal.md` 中 `tmp_` / `persist_` 的 ID 与按前缀判断属于历史漂移；不能因此否认现有 persistence 功能。

Port forward 由 `src/port_forward.rs::{listen, connect_and_login, run_forward}` 管理。当前 controller listener 绑定 `0.0.0.0`；endpoint 在 `LoginRequest::PortForward` 中先做 tunnel option gate，再连接目标，后续才验证 username/password/click/2FA。认证前 outbound connect、persistent service ID 缺 peer-owner 绑定、RDP credential 参数日志是待整改的静态路径，见 Network 文档。

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
→ TunnelAdbManager
→ Runner / DNS discover / Accessibility automation
→ local libadb.so process
```

当前支持 manual pair/connect、endpoint fallback、NSD retry、preferred serial、shell restart cap、best-effort wireless-debug Settings automation。PC remote ADB protocol 未实现。

`libadb.so` 为 packaging 所需的 ignored/external asset；是否在当前 checkout 存在必须逐次清点，不能继承旧机器的“本地存在”结论。构建可复现性和来源清单未闭环。

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

## 16. Rust crate / feature / generation 定位

| 入口 | 当前实现与后续修改位置 |
|---|---|
| `Cargo.toml` | 根 `tunnel` + 8 个 workspace members；edition 2021 / rust-version 1.75；`cdylib`、`staticlib`、`rlib`；default feature 是 `use_dasp` |
| `src/lib.rs` | `flutter` / mobile 编译 FRB module；`plugin_framework + flutter + desktop` 才导出 plugin；iOS 不导出 endpoint server；port forward 和 PTY 限 desktop |
| `src/main.rs` | desktop Sciter、mobile/Flutter 与 CLI 三组 `cfg`；CLI 不是当前已验证的替代产品入口 |
| `src/flutter.rs::{tunnel_core_main, tunnel_core_main_args}` | native runner 启动桥，继续调用 `core_main()`；raw C args 与释放函数属于 ABI/ownership 合约 |
| `src/flutter_ffi.rs` | FRB source of truth；`src/bridge_generated*.rs` / `flutter/lib/generated_bridge*.dart` 是生成输出，不能手工修补作为最终方案 |
| `libs/hbb_common/build.rs::main` | 从两份 `.proto` 生成到 `OUT_DIR/protos`，protobuf 改动必须检查 producer/consumer 和生成边界 |
| `src/server/service.rs::ServiceTmpl` | subscriber、thread、active state、join/cleanup；修改长驻 service 时先确认这些所有权 |
| `src/cli.rs::Session` | 与当前 `Interface`、`Data::Login`、`Client::start()` 存在静态签名/返回形状漂移；`cli + flutter` 的 `main()` cfg 也可能重叠。只有正式 RST-01 结果才能证明编译状态 |

硬件 codec、`vram`、`mediacodec`、`unix-file-copy-paste`、`screencapturekit` 都是独立 feature/platform 面。模块被跟踪、默认注册或有 UI 入口，均不证明当前发布包启用了它。
