# CloudSend 网络与协议 / Network Protocol

接管基线：2026-07-12  
状态：`verified` + `verification-required`

> 本文描述仓库内的端点协议实现。`hbbs`、`hbbr` 服务端源码不在本仓库，因此服务端部署、数据库和运营策略只能作为外部依赖记录，不能由本文推断。

## 1. 网络角色与边界

CloudSend 同一二进制同时包含两类角色：

- 控制端 client：发起远程桌面、文件、终端、端口转发、语音邀请等会话。
- 受控端 server：注册 ID、接受登录、采集屏幕、注入输入并提供能力服务。

外部基础设施包括：

- `hbbs`：ID 注册、rendezvous、地址协商。
- `hbbr`：relay 转发。
- 产品 HTTP API：账号、设备、地址簿、分组和同步。
- ZEGO Token service 与 ZEGO RTC：语音媒体链。

仓库只保存端点实现、协议定义和外部地址配置，不保存这些服务的完整实现。

## 2. 协议层次

```text
Flutter / legacy UI
  -> Rust session interface
  -> client.rs / server/connection.rs
  -> hbb_common protobuf messages
  -> encrypted peer stream or relay stream
  -> remote endpoint
```

主要锚点：

| 层 | 源码 |
|---|---|
| rendezvous 协议 | `libs/hbb_common/protos/rendezvous.proto` |
| 会话协议 | `libs/hbb_common/protos/message.proto` |
| 控制端连接 | `src/client.rs`, `src/client/io_loop.rs` |
| 受控端入口 | `src/server.rs`, `src/server/connection.rs` |
| rendezvous | `src/rendezvous_mediator.rs` |
| relay | `src/client.rs::{request_relay, create_relay}`, `src/rendezvous_mediator.rs` |
| socket/加密封装 | `libs/hbb_common/src/tcp.rs`, `libs/hbb_common/src/udp.rs` |
| UI bridge | `src/flutter_ffi.rs`, `src/ui_session_interface.rs` |

`message.proto::Message` 是会话消息总 union，包含登录、视频、音频、输入、剪贴板、文件、隧道、终端、权限、语音呼叫和自定义状态等类别。协议新增字段必须保持 protobuf 向后兼容：不得复用已发布 field number，不得只改单端。

## 3. Rendezvous、NAT 与 Relay

### 3.1 控制端当前策略

`src/client.rs::LoginConfigHandler::initialize(...)` 当前把 `force_relay` 固定为 true。其结果是 CloudSend 控制端会话：

- 不启动 UDP punch、IPv6 punch 或直接连接候选。
- 显式 IP/domain:port 的直接连接入口被拒绝。
- Android reconnect 也通过 `sessionReconnect(..., forceRelay: true)` 保持 relay-only。

这是一条产品策略，不等于仓库已经删除所有 direct/NAT 代码。

### 3.2 受控端仍保留的能力

`src/rendezvous_mediator.rs` 仍会：

- 启动 rendezvous mediator 和 direct server。
- 维护 LAN/NAT 相关路径。
- 接收 `PunchHole`、`FetchLocalAddr` 等消息。
- 在 `handle_punch_hole(...)` 中根据 `ph.force_relay`、代理和 WebSocket 状态决定 relay。

因此准确结论是：

> 当前 CloudSend 控制端强制 relay；受控端和兼容协议仍包含 direct/NAT 能力。

未来若要做到全局 relay-only，必须单独审计受控端、LAN discovery、direct server、NAT/STUN 探测和兼容客户端影响，不能只依赖控制端常量。

## 4. 会话建立与登录

典型链路：

```text
controller resolves peer ID through hbbs
  -> obtains relay/rendezvous instructions
  -> connects through hbbr
  -> secure_connection handshake
  -> LoginRequest
  -> controlled endpoint authentication/authorization
  -> PeerInfo + capability negotiation
  -> service messages
```

受控端认证仍由 `src/server/connection.rs` 执行，包含密码、点击确认、2FA、trusted devices 等分支。`src/common.rs::verify_login()` 当前近似直接返回 true，但它属于 legacy/custom UI gate，不能据此推断远程端点认证已关闭。

控制端 Android 自动重连的远端密码来源顺序是：当前进程会话缓存，其次构建内置的默认连接密码；禁止把本机 permanent password 当作远端密码。

## 5. 能力与权限消息

登录完成后，协议承载以下主要服务：

- 视频、光标、显示器和画质控制。
- 鼠标、触摸、键盘和自定义 Android command mask。
- 剪贴板与 file copy/paste。
- 文件传输与目录操作。
- Terminal、TCP tunnel、port forwarding。
- 聊天、消息框、截图、录屏、远程打印。
- privacy mode、block input、摄像头。
- ZEGO voice invitation state；媒体不走该 peer stream。

受控端必须在服务入口处执行权限检查，UI 是否显示按钮不能作为授权边界。已确认的审计缺口：Android Mouse/Touch/Key 和部分自定义 command 在授权连接后未与 desktop 分支一样统一检查 `peer_keyboard_enabled()`。该行为需要产品安全决策和正式环境验证。

## 6. 视频与输入在协议中的位置

### 视频

```text
platform capturer
  -> video_service encoder
  -> VideoFrame/codec messages
  -> relay stream
  -> controller decoder
  -> Flutter texture or RGBA
```

首帧等待属于控制端 UI 状态，不属于 rendezvous 在线状态。刷新请求只能唤醒已有正常视频链，不得隐式改变 Android frame source 或请求新的 MediaProjection token。

### 输入

```text
Flutter InputModel
  -> session_send_mouse/key
  -> MouseEvent/KeyEvent/TouchEvent
  -> server/connection.rs
  -> platform input service or Android JNI
```

CloudSend 在 `MouseEvent.url` 上复用了若干 Android command 字符串。修改这些命令必须同时检查 Dart、Rust FFI、client、protobuf usage、server、JNI 与 Kotlin，且需要验证旧端点收到未知命令时的行为。

## 7. 文件、终端与隧道

- 文件传输：`src/client/io_loop.rs`、`src/server/connection.rs`、`libs/hbb_common/src/fs.rs` 与 Flutter `FileModel` 协作。
- Terminal：`src/server/terminal_service.rs` 创建 `ts_<uuid>` 临时会话；旧 `terminal.md` 的持久化描述不是当前实现。
- Tunnel/port forwarding：使用会话协议协商后转发字节流。
- Remote printer：Windows 专属能力，需显式 option 和平台依赖。

文件路径、符号链接、覆盖、权限和取消语义属于跨平台安全边界；未来改动必须包含恶意 peer 输入测试设计。

## 8. ZEGO 语音控制面

RustDesk peer protocol 只传：

- `VoiceCallRequest`
- `VoiceCallResponse`
- 取消、忙状态和邀请生命周期

Token 由外部 HTTP service 获取，音频经 ZEGO SDK 传输。原 RustDesk `audio_service` 不承载当前 ZEGO 媒体。控制通道授权与 ZEGO room/token 授权是两个独立边界，两者都必须成立。

当前明文 token transport、客户端内置 credential 和 Android 自动接受语义均已列入 `10_SECURITY_MODEL.md`，不能把“连接已授权”等价为“用户已同意打开麦克风”。

## 9. 加密与完整性风险

已确认需要专项审计的实现：

1. `src/client.rs::secure_connection(...)` 对缺失、无效或不匹配 signed key 的部分路径可能回退到非 secure 连接，呈 fail-open 风险。
2. `libs/hbb_common/src/tcp.rs` 的 secretbox 双向使用相同 key，方向计数器/nonce 初值相同，可能形成跨方向 nonce reuse。
3. 本地 password protection 使用可读取设备 UUID 派生材料，并在多项数据上使用固定 nonce，安全性更接近混淆而非强 at-rest protection。
4. 部分产品 API、sync 和 ZEGO token 地址使用明文 HTTP。

这些结论来自静态源码审计，尚未通过抓包、互操作或攻击复现。修复前必须设计协议版本、兼容窗口和降级拒绝策略。

## 10. 兼容性原则

协议改动必须满足：

- protobuf field number 只增不改，不复用。
- 新旧 controller/endpoint 至少定义一个明确兼容矩阵。
- 未知 option/command 必须安全忽略或明确拒绝。
- 鉴权和加密失败默认 fail-closed；若保留兼容降级，必须由显式配置开启并告警。
- relay-only 作为产品决策写入测试，不依赖 UI 参数。
- 平台权限在受控端强制，不能只在控制端隐藏入口。
- 日志不得记录密码、access token、ZEGO credential、完整剪贴板或文件内容。

## 11. 可观测性

最小诊断字段应包含：

- session correlation ID，不使用 credential 作关联键。
- controller/endpoint version 与 platform。
- rendezvous、relay、secure handshake、login、capability 各阶段结果。
- relay server 选择与重连次数；地址需脱敏。
- 视频首帧时间、frame source、codec、丢帧/解码错误。
- 权限拒绝的服务类型。

日志等级必须区分正常 relay-only、暂时断线、认证失败、协议不兼容和服务权限拒绝，避免把所有失败归类为“连接失败”。

## 12. 待正式验证

- CloudSend controller 与当前生产 hbbs/hbbr 的完整 handshake/relay 兼容矩阵。
- 受控端是否仍可被非 CloudSend 兼容客户端请求 direct/punch hole。
- signed key 异常时的真实 wire behavior。
- secretbox 双向 nonce 是否会在同一 key 下重复。
- Android input permission 关闭后的 server enforcement。
- 文件、terminal、tunnel 在恶意路径和中断条件下的边界。

所需命令、环境和验收目标统一记录在 `09_DEBUG_SYSTEM.md` 的《编译验证需求》中。
