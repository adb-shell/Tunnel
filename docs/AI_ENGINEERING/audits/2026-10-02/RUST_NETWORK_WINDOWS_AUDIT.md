# Rust / Network / Windows 源码接管审计

日期：2026-10-02（Asia/Shanghai）

Task：`T-2026-10-02-001`

Baseline：`CS-BL-2026-10-02-5cee692`；logical change event：`CE-20261002-T001-02`（主 agent 统一登记）

Observed HEAD：`5cee6921ec10971bb4654bc010f9328d7f70d02b`，detached HEAD

证据等级：V0，local source / manifest / protocol / doc cross-check

负责范围：Rust implementation、peer network、Windows runtime；协同 Security / Release / Flutter / Android owners

> 本报告是固定源码快照下的分领域接管记录。`verified` 只表示相应源码路径已查到，不能解释为已编译、已在设备执行或安全问题已复现。当前本地历史只有 1 个可达 commit，旧 `77062b4` 在本 checkout 不可解析；旧历史基线保留为 historical，不从日期猜测实现先后。

## 1. 恢复与授权

已恢复 `PROJECT_START_HERE.md`、Session Start Protocol、AI Rules、Project State、Current Work、Changelog、Decision Log、Task Protocol、Task Template、Baseline，以及 Rust / Network skills。关联 ADR-0003（controller relay）和 ADR-0009（Amyuni）；长期决定未改变。本轮原请求授权源码只读调查、文档 C1 修订和 agent 协作，不授权业务代码 C2 或 build/test/codegen/Git write/driver operation/production 等 C3。

初始 `git status --short --branch` 为 clean detached HEAD。后续其他 agent 的文档修改是并行工作；本 agent 只修改本报告和 `03_MODULE_DESIGN.md`、`05_WINDOWS_PIPELINE.md`、`06_NETWORK_PROTOCOL.md`。共用状态/日志由协调 agent 同步。

旧证据输入包括 `docs/ENGINEERING_INDEX.md`、`docs/ENGINEERING_BASELINE.md`、`terminal.md`；用于定位疑点，不覆盖当前源码。未删除、移动或重写旧报告。

## 2. 覆盖状态与非覆盖

| 面 | 本轮深度 | 状态 / 未证明内容 |
|---|---|---|
| Cargo/workspace/features/cfg/startup | manifest、module gates、native entry、CLI 签名定向核查 | 源码定位完成；各 feature compile 未运行 |
| FRB/protobuf | source→generated references、generator/build 入口 | 未运行 generator；未逐个导出做 ABI 等价证明 |
| Endpoint registry/lifecycle | creation、subscription、login、close、terminal join | 定向源码审计；并发压力与 race 未复现 |
| Relay/rendezvous | controller branch、force relay、endpoint direct/NAT | 源码路径完成；hbbs/hbbr source/config 在仓外 |
| Auth/crypto/permission | password/click/2FA、signed key、counters、input/clipboard/tunnel | 高价值 trust boundary 已查；不是密码学形式证明 |
| Custom Android commands | Dart producer→FRB→message→endpoint→active JNI | Network/Rust 链核查；Kotlin OS 行为由 Android 报告覆盖 |
| File/terminal/tunnel | handler、path/block、PTY registry、listener与cleanup | 已查权限/ownership/限额边界；跨平台 runtime 未验证 |
| Windows capture/input | service→DXGI/GDI/portable→Enigo | 主链和 fallback 已查；GPU/UAC/desktop matrix 未运行 |
| Privacy/Amyuni/helper | owner、start/stop、failure顺序、driver选择 | 仓内契约已查；外部 DLL/driver 行为未接管 |
| Printer | adapter→memory job→controller→OS print、setup | 边界和资源问题定向核查；无打印/安装验证 |
| Linux/macOS/iOS、codec internals、KCP细节、plugin internals | cfg/interface inventory 与交接 | 未做逐路径全面审计；不得从本报告推断完整掌握 |

## 3. Rust 项目与进程地图

| File::symbol / anchor | 事实 / 修改定位 |
|---|---|
| `Cargo.toml::[package]/[lib]` | `cloudsend` 5.2.1，edition 2021，rust-version 1.75；输出 cdylib/staticlib/rlib |
| `Cargo.toml::[workspace]` | 根 crate + scrap、hbb_common、enigo、clipboard、virtual_display、virtual_display/dylib、portable、remote_printer 共 8 members |
| `Cargo.toml::[features]` | default `use_dasp`；Flutter、hardware codec、VRAM、MediaCodec、plugin、unix file clipboard 独立条件 |
| `src/lib.rs` | desktop/mobile/feature 的编译边界；iOS 无 endpoint server；plugin 需 desktop+flutter+plugin_framework |
| `src/main.rs::main` | Sciter、mobile/Flutter、CLI 三路 cfg；mobile/Flutter 的 binary main 不是 Flutter runtime 全部入口 |
| `src/core_main.rs::core_main` | process args、bootstrap、installation、server、tray、CM、portable/elevation；返回 None 可提前终止 UI |
| `src/flutter.rs::{cloudsend_core_main, cloudsend_core_main_args, free_c_args}` | native runner C ABI 与 args ownership；启动最终回到 core_main |
| `src/flutter_ffi.rs::initialize` | Flutter async runner、APP_DIR、custom-client config、platform logging/Android NAT probe |
| `src/flutter.rs::{session_add, session_start_}` | 逻辑 session 创建和异步连接；不能把每个 Flutter window 等同独立 remote peer |
| `src/flutter.rs::sessions::{get_session_by_session_id, insert_session, remove_session_by_session_id}` | session ID registry 与多窗口 session 生命周期 |
| `src/flutter_ffi.rs` → `src/bridge_generated*.rs` / `flutter/lib/generated_bridge*.dart` | FRB source of truth 和生成输出分开；修导出先改源 API，再正式 codegen |
| `libs/hbb_common/build.rs::main` | pure protobuf_codegen 读取 `rendezvous.proto`、`message.proto` 到 OUT_DIR/protos |
| `build.rs::build_windows` | 编译 Windows C++ 边界；不是纯 Rust 构建；toolchain 与外部依赖由 Release 管 |
| `src/cli.rs::Session` | `Interface` 方法、`Data::Login` 参数与 `Client::start()` 返回结构存在漂移；CLI 仅有源码，未证明可编译 |

附加静态注意：`src/main.rs` 的 `feature=cli` 与 `feature=flutter` main 条件可同时成立，因此 feature 不能无差别全开。`src/flutter_ffi.rs::session_send_mouse2` 出现在注释块内，不能把该旧签名单独算作 active compile failure。

## 4. Endpoint 服务与状态所有者

`src/server.rs::new()` 是 free function，返回 `Arc<RwLock<Server>>`，并非 `Server::new()` 方法。启动通过 `start_server()` / `RendezvousMediator::start_all()`；单个 `Connection` 持有 server 的 Weak 引用，避免简单 ownership 环。

| Owner | 创建 / 活动 | 回收 / 注意 |
|---|---|---|
| `Server.services` | `server::new()` 初始 audio/display/clipboard；desktop cursor/position/focus；windows+flutter adapter 成功后 printer | `Server::add_connection()` 按 `noperms` 订阅；`remove_connection()` 逐 service unsubscribe |
| Video services | `Server::try_add_primay_video_service()` / `try_add_primary_camera_service()` 按需添加 | 视频类型/显示器 index 不等于唯一全局采集器 |
| `ServiceTmpl` | `src/server/service.rs::{ServiceInner, ServiceTmpl, GenericService}` 管 subscriber/thread/active | join、停止与 subscriber 变化是新增 service 的必要设计面 |
| `Connection` | `Connection::start()` 初始化 permissions/CM/channels，`on_message()`分发 | `on_close()` 清 session/forward；Drop 释放 modifiers 并 join terminal service |
| Terminal registry | `terminal_service::TERMINAL_SERVICES` 按 service ID 索引 | 跨 connection 存活，受 persistent flag 和 cleanup 控制；不是磁盘持久化 |
| Authorized IDs | `connection::raii::AuthedConnID` | 登录后注册、Drop 清理；printer 与 wake-lock 等消费此 registry |

`src/server.rs::CLIENT_SERVER` 还可服务 controller 本地 audio 等用途，因此看见 Server 实例不代表当前进程正在作为远端受控 endpoint 接受 peer。

## 5. 网络信任边界与实际检查顺序

```text
Flutter / Sciter session
→ LoginConfigHandler::initialize (controller force_relay)
→ Client::{start, _start, connect}
→ external hbbs rendezvous → external hbbr relay
→ Client::secure_connection ↔ server::create_tcp_connection
→ Connection::on_message(LoginRequest/Auth2fa)
→ Connection::send_logon_response (authorized=true)
→ service messages / raw tunnel bytes
```

| 检查点 | 源码锚点 | 当前事实 |
|---|---|---|
| Controller strategy | `src/client.rs::LoginConfigHandler::initialize` | `cloudsend_force_relay=true`；其他 bool 参数不能关闭此产品策略 |
| Direct entry/candidates | `src/client.rs::Client::_start` | force relay 拒绝 IP/domain:port，并跳过 UDP/IPv6 probe/candidate |
| Late connect fallback | `src/client.rs::Client::connect` | force relay 分支优先 request_relay 后 secure_connection；不能把后续 retained direct code算作实际 controller default |
| Relay request | `src/client.rs::Client::{request_relay, create_relay}` | RendezvousMessage 请求、relay UUID/conn type；hbbs/hbbr server实现/部署不在仓内 |
| Endpoint compatibility | `src/rendezvous_mediator.rs::RendezvousMediator::{start_all, handle_punch_hole}` | 启动 direct server、LAN/NAT；根据 ph.force_relay / proxy / websocket决定relay；没有全局移除直连 |
| Endpoint secure setup | `src/server.rs::create_tcp_connection` | 建 ephemeral key；空 PublicKey 分支或错误 message 类型可继续进入 Connection |
| Password | `src/server/connection.rs::Connection::{validate_one_password, validate_password}` | salt/challenge SHA256、temporary/permanent 分支；不能和产品 UI verify_login混淆 |
| Click / trusted device / 2FA | `Connection::{on_message, handle_login_request_without_validation, send_logon_response}` | click审批、trusted-device元数据、2FA门；`authorized=true` 在 send_logon_response中设置 |
| Service option | `Connection::permission` | 根据 access-mode / option 派生能力；后续每种 message 的执行 gate 仍需单独审查 |
| Legacy UI gate | `src/common.rs::verify_login` | unconditional true；不等于 peer password validation被删 |

**重要例外：** `LoginRequest::PortForward` 的 `TcpStream::connect()` 在 username/password/click/2FA前执行，仅受 `enable-tunnel` option gate约束。raw forwarding 后续仍受授权状态控制；认证前网络连接副作用与未认证任意字节隧道是两个不同结论。

### Crypto 差距

- `Client::secure_connection()` 在缺有效 signed ID/key 时发送空消息并返回 Ok；签名 mismatch 分支明确走 non-secure fallback。
- `server::create_tcp_connection()` 对空 asymmetric_value 清 key_confirmed后继续；未知 message type记录错误后继续。
- `libs/hbb_common/src/tcp.rs::Encrypt::{new, enc, dec}` 的同一 key配合两方向均从0开始的 counter，`FramedStream::get_nonce()` 只有序号，无方向分隔；两端首次发送 nonce 序号均为1。
- `libs/hbb_common/src/password_security.rs::symmetric_crypt` 使用 UUID派生 key和固定nonce；这是本地保护实现，不是强安全存储证明。

缺失/错误 key应 fail-closed、方向 key/nonce唯一是**整改目标**，不是当前保证。需 NET-03隔离环境和独立密码学评审；不访问生产或测试现有credential。

## 6. 服务权限与跨层消息地图

| 消息 / 功能 | 当前 receiver控制 | 修改/验证位置 |
|---|---|---|
| Mouse / Pointer / Key | 全局 authorized门；desktop还检查 peer_keyboard_enabled；Android JNI shortcut缺同等gate | `Connection::on_message`、JNI、Kotlin；NET-04 / AND-05 / E2E-04 |
| Clipboard | 单 Clipboard有self.clipboard；desktop MultiClipboards亦有；Android MultiClipboards直接转JNI | `src/clipboard.rs::{handle_msg_clipboard, handle_msg_multi_clipboards}`；NET-04/06 |
| FileAction | file-transfer登录option gate + authorized；printer Send有特殊分支；one-way限制部分操作 | `Connection::on_message`、`TransferJob`、CM FS处理；RST-05 / NET-06 |
| Terminal | 登录有OPTION_ENABLE_TERMINAL和auth；service ID可由peer提交，registry未绑定owner | `Connection::init_terminal_service`、`TerminalServiceProxy`；NET-04/06 |
| Tunnel | enable-tunnel gate；目标connect发生认证前；controller listener全接口 | `port_forward::{listen, connect_and_login, run_forward}`；NET-04/06 |
| Privacy | authorized+connection owner机制；handler未统一以keyboard flag拒绝 | `Connection::{toggle_privacy_mode, turn_on_privacy}`、PrivacyMode；WIN-03/06 |
| ZEGO invitation | VoiceCallRequest/Response、timestamp/busy/close；audio通过Flutter SDK旁路 | `src/client/io_loop.rs::Data::NewVoiceCall`、`Connection::{handle_voice_call, close_voice_call}`；NET-07 |

Android自定义命令链：

```text
flutter/lib/common/widgets/overlay.dart
→ flutter/lib/models/input_model.dart::InputModel.sendMouse
→ src/flutter_ffi.rs::session_send_mouse
→ src/ui_session_interface.rs::Session.send_mouse
→ src/client.rs::send_mouse
→ message.proto::MouseEvent(mask, url=5)
→ src/server/connection.rs::Connection::on_message
→ libs/scrap/src/android/pkg2230.rs::call_main_service_pointer_input
→ call_main_service_set_by_name / Kotlin Service+Accessibility
```

`src/common.rs::input` 的自定义type 5—12包括原六类与touch-block/Dev selector；不能把mask理解成独立新增protobuf枚举。未知老端点对mouse低位的解释需单独兼容测试。active JNI来自 `libs/scrap/src/android/mod.rs`；`ffi.rs`保留但未导出。

type10/`wheelstop`只确认保留mapping，未证实当前有效Dart sender和专用JNI sink；完整active core JNI还包括`src/flutter_ffi.rs::server_side`，不只scrap模块。

关键wire字段：`Message.mouse_event=10`、`key_event=15`、`clipboard=16`、`file_action=17`、`misc=19`、`voice_call_request/response=23/24`、`pointer_device_event=26`、`multi_clipboards=28`、`terminal_action/response=31/32`；`Misc.cloudsend_status=39`。这些号码不得复用；可被protobuf解析不代表有完整语义兼容。

## 7. File / Terminal / Tunnel 具体维护点

### File

`libs/hbb_common/src/fs.rs::TransferJob::{new_write, new_read, write, read, modify_time, remove_download_file, join}` 管job、block压缩、`.download`、rename、mtime和cancel资源。`write()`校验job ID和file index，`join()`直接PathBuf::join；未发现独立session root containment。路径、symlink、absolute/parent name、压缩膨胀与中断收尾必须作为独立负向矩阵，不能以“远控已登录”替代路径审查。

### Terminal

真实状态链：LoginRequest.Terminal.service_id → permission/auth → init_terminal_service → get_or_create_service → TerminalServiceProxy::handle_action → platform PTY/shell。`TerminalOpened.service_id`仅在persistent时回传；`src/client/io_loop.rs`将成功返回ID记入peer option，`LoginConfigHandler::create_login_msg`后续带回。

- ID统一 `ts_<uuid>`；`is_persistent` 是独立bool。
- 同一endpoint进程全局HashMap registry，无peer-owner字段/匹配；已授权peer持有有效其他service ID时的隔离为条件性风险，未动态复现。
- 非persistent服务的GenericService退出时删除并stop child；persistent保留进程。cleanup约每5分钟检查，非persistent闲置1小时、persistent且空sessions闲置2小时才清理。
- 已有1 MiB output buffer / 10,000 lines、100 services和100-message channels；这些不等于每service terminal数量、单次输入/解压或全局memory全部有上限。
- `handle_open()`复用既有terminal会读近期buffer但源码TODO未实际发送该buffer；断连存活不等于完整历史output replay。
- `terminal.md` 的前缀设计是historical drift；当前persistence功能真实存在。跨进程重启恢复并不存在于该registry实现。

### Tunnel

`port_forward::listen` 使用全接口 `0.0.0.0`；每个accept创建remote登录。`run_rdp()`调用cmdkey/mstsc且当前打印含credential参数的args；值未读取/复制。后续应明确监听范围、目标ACL、认证前副作用、timeout/cancel和日志策略，C2前不改产品行为。

## 8. Windows 主链与资源地图

| File::symbol | 已确认实现 | 生命周期/外部边界 |
|---|---|---|
| `src/server/video_service.rs::{new, create_capturer, run}` | monitor/camera service，codec/refresh/topology切换触发SWITCH | 不同状态变化可重建capturer；需保留fallback可观测性 |
| `libs/scrap/src/dxgi/mod.rs::Capturer` | Desktop Duplication、AcquireNextFrame/ReleaseFrame；pixel/texture | GPU/device loss与ReleaseFrame配对需要runtime验证 |
| `libs/scrap/src/dxgi/gdi.rs::CapturerGDI` | GDI layered-window采集fallback | DXGI初始化/帧失败、关闭DirectX等路径可回退 |
| `src/server/portable_service.rs::client::{create_capturer, handle_mouse, handle_pointer, handle_key}` | primary display+portable running用shared memory；input通过IPC，否则本进程 | capture/input必须一起回归UAC/secure desktop |
| `portable_service.rs::CapturerPortable::frame` | 对width/height检查后按共享FrameInfo.length构造slice | 未见同处length≤mapping剩余区间的检查；返回slice与共享内存owner/shutdown并发需ownership证明 |
| `src/server/input_service.rs` → `libs/enigo/src/win/win_impl.rs::{mouse_event, keybd_event}` | Win32 SendInput，固定ENIGO_INPUT_EXTRA_VALUE | input hook marker可被本机程序伪造，不是身份认证 |
| `src/privacy_mode.rs::PrivacyMode::{check_on_conn_id, check_off_conn_id}` | connection owner互斥；system invalid ID可恢复 | 与UI view-only不是同一个边界 |
| `src/privacy_mode.rs::{DEFAULT_PRIVACY_MODE_IMPL, get_supported_privacy_mode_impl}` | 19041+ exclude，否则mag/installed virtual；virtual列表需service running | 配置requested impl可fallback，实际impl应真实反馈 |
| `win_topmost_window.rs::PrivacyModeImpl::{start, stop, turn_on_privacy, turn_off_privacy}` | suspended user-token helper + APC LoadLibraryW，窗口show/hide + input hooks | 外部WindowInjection.dll行为/签名无法由Rust侧证明 |
| `src/platform/windows.rs::check_update_broker_process` | 从目标OS RuntimeBroker.exe复制命名为RuntimeBroker_cloudsend.exe | OS-DERIVED，不是丢失独立helper工程；当前以modified time判断更新，未见此处signature验证 |
| `win_virtual_display.rs::PrivacyModeImpl::{ensure_virtual_display, set_primary_display, disable_physical_displays, restore}` | virtual primary、physical disable、registry recovery | Win24H2/third-party VD和快速拔插限制见源码注释；不是本轮真机结果 |
| `win_virtual_display.rs::restore_reg_connectivity` | 读取reg_recovery恢复registry connectivity | kill/crash/driver失败的恢复必须真机证明 |
| `virtual_display_manager.rs::IDD_IMPL` | active Amyuni；RustDesk IDD retained dormant | index/modes语义不能混用 |
| `virtual_display_manager.rs::amyuni_idd::{plug_in_monitor, plug_out_monitor, reset_all}` | driver IOCTL、最多4 monitors、内部count为best-effort | force_all/force_one可能影响其他process显示器，源码明确count不精确 |
| `win_input.rs::{hook, unhook}` | 独立Win32 message-loop线程、low-level hooks、Ctrl+P emergency exit | hook和window/topology是不同资源，失败必须逐项清理 |
| `src/server/printer_service.rs::{init, get_prn_data, run}` | runtime adapter DLL获取XPS bytes，300ms poll，转owned Vec再free | 外部ABI、size、free函数可用性和signature待验证 |
| `src/server/connection.rs::on_printer_data` | AUTHED_CONNS中选择第一个printer-capable connection | 不保证多controller的用户期望路由 |
| `src/platform/windows.rs::send_raw_data_to_printer` / `windows.cc::PrintXPSRawData` | controller本地printer校验→OS XpsPrint.dll→job/stream | 函数实现已tracked；OS API/driver互操作未验证 |
| `libs/remote_printer/src/setup/printer.rs::add_printer` | AddPrinterW成功路径未见ClosePrinter | 与delete_printer已Close不同；静态资源风险 |

新增恢复差距：virtual `turn_on_privacy()`先 `guard.succeeded=true`，后 `allow_err!(hook())`，最终Ok(true)；关闭virtual/topmost时先 `unhook()?`，错误可阻断后续topology restore/window hide。已有TurnOnGuard不能证明这两类失败都已恢复。

其他定向复核：`STARTUPINFOW.cb=0`仍存在；SetupDiGetClassDevsW后的本函数未见SetupDiDestroyDeviceInfoList；APC注入remote buffer未见VirtualFreeEx。以上是静态资源/初始化风险，不能声称全部已发生泄漏或crash。

## 9. 文档差异与后续任务定位

| 本地发现ID | 当前事实 / 差距 | 本轮文档动作 | 后续验证 |
|---|---|---|---|
| RNW-01 | `Server::new`锚点不存在，实际server::new free function；video按需，terminal按connection | 03/05纠正注册描述 | RST-01/06 |
| RNW-02 | 旧06把terminal称临时且否认persistence，当前确有is_persistent/reconnect | 03/06纠正ID/保留/cleanup边界 | RST-05、NET-06 |
| RNW-03 | PortForward目标connect早于remote身份认证 | 03/06新增顺序和条件性风险 | NET-04/06 |
| RNW-04 | Android MultiClipboards缺同类clipboard gate | 06明确与单Clipboard不同 | NET-04、E2E-04 |
| RNW-05 | Persistent terminal registry仅按ID，无peer owner检查 | 03/06补边界，未称未登录shell bypass | NET-04/06 |
| RNW-06 | RDP args日志可含credential；tunnel listener全接口 | 03/06说明当前行为，不复制值 | RST-05、NET-06 |
| RNW-07 | virtual hook错误仍success；unhook错误提前阻断restore/hide | 05补真实错误顺序 | WIN-03/04/06 |
| RNW-08 | crypto fail-open和同key方向nonce序列重合仍存在 | 06补endpoint和counter证据，区分目标/现状 | NET-03 |
| RNW-09 | CLI feature drift、生成物/源边界、optional cfg | 03增crate/feature map | RST-01/03 |
| RNW-10 | 旧机器ADB“本地存在”不能继承到当前worktree | 03改为按次inventory，交Release/Android汇总 | Release asset intake |

本报告ID只作本次定位，不替代全仓 `SEC-*` 风险编号或ADR。Security report整合RNW-03/04/05/06/07；本轮不修改业务逻辑、wire field、版本或外部资产。

## 10. 验证记录与下一步

实际执行：只读 `rg` / Get-Content、Git status/HEAD、文档diff和精确源码anchor核对；文档用apply_patch修改。达到V0。没有运行Cargo、Flutter、Gradle、codegen、单元测试、UI/device、driver/helper、network integration或生产请求；V1—V5均NOT_RUN。

已有TEST_MATRIX适用：RST-01/03/04/05/06/07、NET-01—08、WIN-01—08、E2E-01/02/04/05。加入测试ID只表示验证需求，不是已通过。需要追加的场景由协调者归入现有case细则：认证前tunnel connect、跨peer已知terminal ID、Android MultiClipboards关闭权限、privacy hook/unhook错误、shared memory伪造length。

《编译验证需求》（待单独授权和正式环境，未执行）：

- 命令：仓库根 `cargo check --locked --features flutter`；CLI专项 `cargo check --locked --features cli`，分开feature组合；Windows正式产品入口 `new-build.cmd`；Android正式机器使用项目约定的 `./build.sh 1` / `./build.sh 2`。
- 环境：项目规定Rust/Flutter/native toolchain与依赖cache；Windows遵循PC-Build.md的正式机器；Android为正式Linux构建机。不得在当前接管工作树自动解析/安装依赖。
- 目标：分别验证活跃产品/条件feature的编译与FRB/protobuf边界；编译通过仍不能替代NET/WIN/AND设备与隔离integration结果。
- 集成要求：隔离hbbs/hbbr、disposable credentials、两类不同peer身份、恶意字段/permission toggle/failure injection；正式Windows test device覆盖capture/input/privacy/driver/print恢复。禁止以生产credential或driver安装做临时探测。

接管结论：本报告范围内已建立从入口到高权限sink和cleanup的可检索源码地图，并修正有直接证据的文档漂移。外部服务、DLL/driver provenance、全feature编译和运行安全性仍未闭环，不宣称整个系统已动态验证或可发布。
