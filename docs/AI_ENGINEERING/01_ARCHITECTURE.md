# Tunnel 完整架构 / Architecture

> 2026-10-03 当前增量（T-2026-10-03-001 / V0）：新增 helper→APK TunnelAdbRuntime→owned JNI encoded→relay→候选 decoder/barrier→Flutter呈现ACK。ADB与MediaProjection保留独立生命周期，只有COMMITTED开放输入。 [实现、构建与验收](../plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。以下2026-10-02及更早的阶段描述以本增量和当前源码为准。

最近关键链路复核：2026-10-02，`HEAD 5cee692` / V0。详细覆盖与未验证边界见 [本轮审计](audits/2026-10-02/README.md)。

## 1. 运行体与信任边界

```mermaid
flowchart LR
    PCUI["PC Flutter / Sciter UI"] --> PCBridge["Rust FFI + Client Session"]
    PCBridge --> HBBS["External hbbs / ID rendezvous"]
    PCBridge --> HBBR["External hbbr / relay"]
    HBBR --> Endpoint["Controlled Rust Connection"]
    HBBS --> Endpoint
    Endpoint --> Services["Video / Input / Clipboard / File / Terminal"]
    Services -->|"Android capture/input/clipboard subset"| AndroidJNI["Android Rust JNI"]
    AndroidJNI --> AndroidKotlin["MainService + AccessibilityService"]
    PCUI --> ProductAPI["External Product API"]
    Endpoint --> ProductAPI
    PCBridge --> ZegoToken["External ZEGO Token service"]
    PCUI --> ZegoRTC["ZEGO SDK media"]
    AndroidKotlin --> AndroidFlutter["Android Flutter ZegoVoiceCallModel"]
    AndroidFlutter --> ZegoRTC
```

必须分开的信任域：

- 控制端（`controller`）：发起连接、解码画面、产生输入和文件操作。
- 受控端（`controlled endpoint`）：认证会话并执行高权限本地操作。
- rendezvous/relay：仓外基础设施；只在协议客户端中有定义。
- 产品 API：账号、地址簿、设备组、策略和 sync；后端不在仓库。
- ZEGO：Token service 与 ZEGO RTC 是两个外部域。
- Android OS：`MediaProjection`、Accessibility、overlay、ADB、前台服务分别由系统授权。
- Windows OS：driver、service、injection、privacy window 和 input hook 是本机高权限边界。

## 2. 分层架构

| 层 | 主要目录 | 职责 |
|---|---|---|
| Product UI | `flutter/lib/`, `src/ui/` | 桌面/移动/web UI、多窗口、遗留 Sciter |
| State/Bridge | `flutter/lib/models/`, `src/flutter*.rs`, `src/ui_*_interface.rs` | session/model、事件流、FFI、texture/RGBA |
| Controller | `src/client.rs`, `src/client/` | rendezvous、relay、登录、解码、文件/终端/输入发送 |
| Controlled endpoint | `src/server.rs`, `src/server/` | 认证、服务订阅、输入/文件/终端/视频执行 |
| Shared protocol | `libs/hbb_common/` | protobuf、config、socket/stream、crypto、fs |
| Capture/Input | `libs/scrap/`, `libs/enigo/`, `libs/clipboard/` | 采集、编码辅助、输入、剪贴板 |
| Native platform | `src/platform/`, `src/privacy_mode*`, Android Kotlin | OS service、driver、JNI、MediaProjection、Accessibility |
| Product integration | `src/hbbs_http/`, Flutter API models | OIDC、login、address book、group、sync、download |
| Packaging | `build*`, `env.sh`, `flutter/*`, `res/`, `.github/workflows/` | 生成、编译、签名、portable、平台发布 |

## 3. 控制面与数据面

### 3.1 控制面

- rendezvous registration、ID/PK、relay request：`rendezvous.proto`。
- session login、permission、display switch、privacy、terminal、ZEGO invitation：`message.proto`。
- Flutter/Android 状态同步：Rust event stream、MethodChannel、`tunnel_status`。
- 产品策略：`hbbs_http::sync` heartbeat/config/disconnect。

### 3.2 数据面

- 视频：capture → codec → `VideoFrame` → relay stream → decoder → texture/RGBA。
- 音频：普通远控音频仍走 RustDesk `AudioFrame`；当前 1v1 语音走 ZEGO SDK。
- 输入：Flutter → Rust message → endpoint `Connection`；desktop 经 `input_service` 到 OS，Android 分支直接经 JNI → MainService → Accessibility，不能用 desktop gate 推断 Android 已检查相同权限。
- 文件：`FileAction`/`FileResponse` + `TransferJob` block/digest/compression。
- 终端：`TerminalAction`/`TerminalResponse` + PTY service。
- Android raw frame：Kotlin direct buffer → JNI `FrameRaw` → Rust video service。

## 4. 会话架构

### 4.1 Controller

`LoginConfigHandler` 持有 peer、connection type、password/options、codec 和 relay 状态；`Client` 完成 rendezvous 与 stream 建立；`client/io_loop.rs` 是消息主循环；`FlutterSession` 将事件送到 Dart。

Tunnel controller 固定 `force_relay = true`，拒绝显式 direct address，并跳过 direct candidate。这个决定只证明 Tunnel controller 的默认路径；`rendezvous_mediator.rs::handle_punch_hole()` 在 controlled endpoint 仍可响应未设置 `force_relay` 的兼容请求。

### 4.2 Controlled endpoint

`RendezvousMediator::start_all()` 建立本机 server、注册 ID/PK、启动 sync，并保留 direct/LAN/NAT 继承逻辑。`Server` 注册服务；每个 `Connection` 完成登录、权限和消息分发。

远控会话认证与 `src/common.rs::verify_login()` 不同。后者当前直接返回 `true`，主要属于 legacy/custom-client UI 校验；受控端实际认证仍在 `src/server/connection.rs` 与 `hbb_common::password_security`。

## 5. UI 与状态管理

Flutter 使用混合状态方案：

- `provider` 承担主 session/model 注入。
- GetX `.obs`/`Rx*` 用于地址簿、设备组和局部响应式状态。
- 大量全局 singleton/registry 位于 `common.dart`、`model.dart`、`server_model.dart`。
- desktop 使用 `desktop_multi_window`，remote/file/terminal/port-forward 等窗口拥有独立 Flutter engine；plugin 注册必须在每个 runner/engine 完整执行。

源码采用此混合模型，运行表现待验证；状态所有权分散，存在 timer、stale client、dialog overlay 和跨 engine 生命周期风险。

## 6. Android 四层状态架构

Android 不能用一个“服务开关”描述：

1. core service/JNI：`_isReady`、`MAIN_SERVICE_CTX`。
2. screen share：`_isStart`、`mediaProjection`、`captureStarting`。
3. frame source：normal `ImageReader`、`SKL`、`shouldRun` ignore、one-shot。
4. PC display：`waitForFirstImage`、`waitForImageTimer`、last frame/reconnect。

core service 在线不等于投屏；projection 丢失不等于 relay 断开；只有真实 RGBA/texture frame 到 UI 才能清 waiting。详见 `04_ANDROID_PIPELINE.md`。

## 7. Windows 架构

- capture：DXGI 优先、GDI fallback，privacy 可切 Magnifier。
- input：`src/server/input_service.rs` → `libs/enigo/` / `SendInput`，portable service 可代理高权限操作。
- privacy：exclude/topmost window、Magnifier、virtual display 三类实现。
- virtual display：当前活跃选择为 Amyuni `usbmmidd_v2`，实际 platform addition 是 `amyuni_virtual_displays`；`tunnel_virtual_displays` 只属于未选用 RustDesk IDD 分支。
- helper/injection：`RuntimeBroker_tunnel.exe`、`WindowInjection.dll`、low-level hooks。

详见 `05_WINDOWS_PIPELINE.md`。

## 8. 仓外 API 架构

Flutter 产品登录和资源 API 经 `utils/http_service.dart` wrapper，可走 Dart HTTP 或 Rust proxy；Rust `account.rs` 提供独立 OIDC device auth；`sync.rs` 提供 endpoint heartbeat/strategy；`downloader.rs` 提供下载；`record_upload` 休眠。仓库没有产品 backend/数据库实现；ZEGO deployment script 内嵌部分 Go broker source，不等于独立受控后端或已部署版本。

详见 `07_API_SYSTEM.md`。

## 9. 架构不变量

以下包含维护要求，不代表现有代码全部满足。例如 endpoint permission、Windows privacy失败恢复、credential与日志边界存在已记录缺口；当前实现与目标差异见 `10_SECURITY_MODEL.md`。waiting不得自动切ignore的规则只针对PC waiting/reconnect，不否认Android锁屏等平台事件的有条件fallback。

- 源码与运行时状态必须分层，不用 UI 文案代替真实状态。
- Android hidden recovery 不得弹 `MediaProjection` 授权。
- Android disconnect 不得自动停止 screen share。
- waiting 不得自动切 ignore/screenshot fallback。
- ZEGO 不得进入旧 `audio_service` 媒体链。
- ADB/LADB 不得污染 screen share/video/side-button 状态。
- controller relay-only 与 endpoint direct surface 必须明确区分。
- 任何 protobuf 改动必须同步 sender、receiver、FFI/UI 和兼容策略。
- Windows privacy/virtual-display 修改必须带 driver/OS/permission 恢复方案。
- secret 不得写入客户端、跟踪文档或脚本默认值。

## 10. 架构债务

- 当前本地仅单root快照，旧历史无法重放，缺少可验证upstream strategy；不推断是谁或如何改变历史。
- 大型 God files 与 global/static state 形成高耦合。
- Android raw buffer 和 `static mut` 横跨语言/线程安全边界。
- transport crypto、HTTP 与 fail-open 行为缺少正式 threat model。
- active/dormant/legacy feature 没有自动可达性检查。
- 构建和二进制资产依赖本机隐式状态。
- 手动-only Actions、少量测试和弱 commit 语义使回归证据不足。
