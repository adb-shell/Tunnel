# Tunnel 项目总览 / Project Overview

> 2026-10-03 当前增量（T-2026-10-03-001 / V0）：新增受控远程 ADB 源码链；本地同意、单 owner、typed 操作与 H264 投屏已接入。P0—P6运行验收仍未完成。 [实现、构建与验收](../plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。以下2026-10-02及更早的阶段描述以本增量和当前源码为准。

最近源码复核：2026-10-02
源码基线：`HEAD 5cee692` / `CS-BL-2026-10-02-5cee692`
接管角色：Tunnel Principal Engineer

> 本文是新 AI 工程体系的入口。结论分为源码已证实（`verified`）、静态推断（`inferred`）、仓外依赖（`external`）和待正式环境验证（`verification-required`）。源码永远高于本文。

## 1. 项目身份

Tunnel 是 RustDesk 的深度二次开发产品，Android 可见名称为 `隧道`。当前身份锚点：

| 项目 | 当前值 | 源码锚点 |
|---|---|---|
| Rust package/library | `tunnel` / `tunnel` | `Cargo.toml` |
| Rust version | `5.2.1` | `Cargo.toml` |
| Flutter package/version | `flutter_hbb` / `5.2.1+59` | `flutter/pubspec.yaml` |
| Runtime app name | `Tunnel` | `libs/hbb_common/src/config.rs::APP_NAME` |
| Android package | `com.tunnel.app` | `flutter/android/app/build.gradle` |
| Android label | `隧道` | `flutter/android/app/src/main/res/values/strings.xml` |
| Android scheme | `tunnel` | `flutter/android/app/src/main/AndroidManifest.xml` |
| Android native library | `libtunnel.so` | `build.sh`, `pkg2230.kt`, `native_model.dart` |
| Windows native library | `tunnel.dll` | `flutter/windows/CMakeLists.txt`, `native_model.dart` |

当前产品组织标识为 `ORG = com.tunnel`。RustDesk 类型名、第三方仓库 URL、驱动名和兼容字段中的上游锚点仍被保留，不做无差别替换。2026-10-02 品牌迁移和独立部署边界见 `docs/plans/TUNNEL_IDENTITY_MIGRATION_TASK.md`；旧文档中的项目专有名称已统一，历史原文以对应 Git 对象为准。

## 2. 来源与可追溯性

- 当前本地 Git 非 shallow，仅 1 个可达提交、无 tag；HEAD/root 为 `5cee6921ec10971bb4654bc010f9328d7f70d02b`（2026-10-01），无 parent。初始 detached HEAD、clean。
- 旧文档的 59 commits、`96a9c7c` 导入与 `77062b4` 基线是 7 月历史记录；旧对象在当前本地仓不可解析，不能重放或据此证明新旧源码一致。
- 代码布局、协议、依赖和 README 支持 RustDesk 派生关系；精确 upstream commit、补丁序列和原始作者历史仍待补材料。
- 本轮未联网核验远端 public/private、PR、Issue、Release 或分支保护；旧远端观察保持 `historical`。
- 在没有上游基线 commit 前，只能称为“RustDesk 深度分支”，不能声称与某个官方版本存在可重放的 fork ancestry。

## 3. 资产规模

静态清点结果：

- HEAD Git tree 跟踪文件：975；blob 总计 15,605,939 bytes。
- tracked 代码/头文件/协议合计 235,596 文本行（含 generated/vendor/空行）：Rust 135,562，Dart 80,377，Kotlin 8,836，Java 34，C/C++/headers 9,591，protobuf 1,196。
- Rust workspace：根 crate + 8 个成员 crate。
- HEAD 跟踪 Markdown：104 个；逐份用途与处理见 [本轮文档登记](audits/2026-10-02/DOCUMENT_REGISTER.md)。
- 高复杂度文件：`generated_bridge.dart` 13,740 行、`src/server/connection.rs` 4,835 行、`flutter/lib/common.dart` 4,243 行、`src/client.rs` 4,058 行、`flutter/lib/models/model.dart` 3,976 行。

旧接管记录列出的 ignored 资产，在当前工作区的存在性如下：

| 本地目录 | 规模 | 状态与风险 |
|---|---:|---|
| `ADB-CODE/` | 当前不存在 | 7 月记为 local-only；来源/revision/内容需资产 owner 补充 |
| `LADB/` | 当前不存在 | 旧参考源码及许可结论是历史材料，不能视为当前已验收资产 |
| `flutter/android/app/src/main/jniLibs/` | 当前不存在 | `libadb.so` 所需 ABI、provenance、license、获取方式未闭环 |

因此，干净 clone 不能独立重建当前 Android ADB 资产。必须建立受控的 binary provenance、hash、license 和获取流程。

## 4. 产品能力全景

### 4.1 当前活跃能力（`active`）

- 控制端与受控端远程会话、密码/点击确认/2FA/trusted-device 认证。
- 视频采集、编码、传输、解码、纹理/RGBA 显示、多显示器和画质/QoS。
- 键鼠/触控输入、剪贴板、文件剪贴板、文件传输、端口转发、截图、录制、终端。
- Flutter 桌面多窗口、移动端 UI、旧 Sciter UI 兼容面。
- Android `MainService`、`MediaProjection`、Accessibility 输入、normal/SKL/ignore 三帧源、黑屏、防触、Dev 自动点选、状态监测和自动重连。
- Android 本地 ADB/LADB、mDNS 发现与无线调试辅助。
- Windows DXGI/GDI capture、privacy mode、Amyuni virtual display、输入注入与 portable service。
- Flutter 产品账号、地址簿、设备组、Rust OIDC client、sync/heartbeat/downloader。
- ZEGO 1v1 语音媒体；Rust 仅承载邀请/状态控制。

### 4.2 休眠、兼容或构建条件能力

- `src/hbbs_http/record_upload.rs`：实现存在，但 `ENABLE` 没有启用路径，当前为休眠能力。
- `plugin_framework`：仅特定 desktop Flutter feature 组合编译。
- RustDesk IDD：代码存在，但当前 `virtual_display_manager::IDD_IMPL` 选择 Amyuni。
- `cli` feature：接口签名已静态漂移，需正式环境确认是否可编译。
- `src/ui/` Sciter：遗留兼容面，不应当作新功能主入口。
- `libs/scrap/src/android/ffi.rs`：未由 `android/mod.rs` 导出，是兼容参考而非运行主路由。

### 4.3 仓库外能力（`external`）

本仓库不包含：

- `hbbs` rendezvous server、`hbbr` relay server。
- 产品 API server、用户/设备/Token 数据库、schema、migration、backup/restore。
- ZEGO Token 服务端源码的独立受控仓库与生产密钥管理系统。
- 生产监控、发布平台、证书/密钥托管和灾备配置。

`src/server.rs` 是终端设备上的受控端服务，不是 `hbbs`。

所有仓外服务、database、driver、binary、signing 和 release infrastructure 的接管状态统一见根目录 `EXTERNAL_ASSET_REGISTRY.md`。

## 5. 核心技术路线

1. Rust 负责网络会话、协议、媒体/输入/文件服务、平台能力与 FFI。
2. Flutter 负责主产品 UI、session/model 状态和多平台窗口。
3. Android 由 Flutter MethodChannel + Rust FFI/JNI + Kotlin service 三层共同组成；任何运行时改动都可能跨层。
4. Windows 继续使用 RustDesk 平台底座，并叠加 Amyuni driver、隐私窗体和注入辅助进程。
5. Tunnel 控制端会话固定请求 relay；受控端仍保留上游 direct/punch/LAN/NAT 代码，不能称为全局移除直连。
6. ZEGO 是独立 RTC 媒体旁路；旧 RustDesk `audio_service` 不承担当前语音媒体。
7. 产品账号 API 与远控会话认证是两套边界，不能混为一个“登录”。

## 6. 2026-04—07 二开时间线（historical）

下表保留旧文档的演进记录；当前本地 Git 无对应历史对象，本轮未独立证明其日期、归因或完整性。

| 阶段 | 旧文档记载的演进 |
|---|---|
| 2026-04-13 | DaXianDesk/RustDesk 快照导入 |
| 04-14—04-22 | 工程文档初建；Android 黑屏、独立防触、状态监测、双通道与无障碍守卫 |
| 05-05—05-18 | Tunnel 品牌/package/SO/DLL/构建迁移；文档清理和源码审计 |
| 05-20—05-31 | Android 本地 ADB/LADB、mDNS、无线调试自动化 |
| 05-31—06-07 | ZEGO 替换语音媒体；Token 服务和开发者登录旁路 |
| 06-09—06-24 | relay 重连、core service/share 解耦、Android 首帧与投屏授权稳定性 |
| 06-25—07-01 | Dev 自动点选、黑屏帧恢复/亮度/提示、Actions 改为仅手动 |
| 06-27—07-10 | 多次基础设施 endpoint 迁移；Token 部署脚本进入仓库 |

## 7. 当前最高风险

1. 当前 tracked 源码/部署资料存在 credential 类型字面量；有效性、生产用途、远端公开性和历史传播本轮未核验；不复制其值。
2. API、sync、ZEGO Token 默认使用明文 HTTP。
3. transport secure handshake 存在 fail-open 降级；secretbox nonce/key 使用需要独立密码学复核。
4. Android raw frame 存在 direct `ByteBuffer` 与 `Image.close()` 生命周期疑点；JNI 还有跨线程 `static mut` data-race 风险。
5. Android 已授权连接的输入/自定义命令缺少完整服务端 permission gate；Dev UI 密码不是协议鉴权。
6. Windows privacy/driver/injection 是高权限面，且部分 WinAPI 初始化和快速显示器切换有 UB/崩溃风险。
7. ADB binary、参考源码和部分构建依赖是本地/外部资产，干净 clone 不可复现。
8. CI均含manual dispatch，部分另有workflow_call，无push/PR自动gate；Flutter/Android自动化测试薄，文档与代码漂移缺少持续证据。

详见 `10_SECURITY_MODEL.md`、`11_ROADMAP.md` 和 `DOCUMENT_AUDIT_REPORT.md`。

## 8. 新工程知识读取顺序

1. 从根目录 `PROJECT_START_HERE.md` 进入。
2. 读取 `.codex/AI_RULES.md` 与 `AI_TASK_EXECUTION_PROTOCOL.md`，确定权限和任务状态。
3. 开发任务实例化根目录 `TASK_TEMPLATE.md`，固定 `docs/BASELINE/` 的 Baseline ID。
4. 回到当前任务的源码、manifest、protocol、build script 和 diff。
5. 读取本文件、`01_ARCHITECTURE.md`、`02_SOURCE_MAP.md` 和对应专题文档 `03`—`10`。
6. 长期决定读取 `docs/ADR/`；验证从根目录 `TEST_MATRIX.md` 选择 cases。
7. 开发任务读取根目录 `DEVELOPMENT_WORKFLOW.md`；仓外依赖读取 `EXTERNAL_ASSET_REGISTRY.md`。
8. `11_ROADMAP.md` 仅用于 proposed 规划，不作为已实现事实。
9. 原 `docs/ENGINEERING_*` 只作详细历史基线和 no-regression 参考。
10. `.codex/` 只作规则、摘要、决策和任务索引，不高于源码或当前工程文档。

## 9. 接管限制

本轮没有编译、测试、发布、删除、版本修改、Git 写操作或上传，也未联网访问服务。结论来自当前本地源码、文件和 Git 元数据；不是逐行安全证明、设备验证或全系统接管。需要动态证据的事项统一列入《编译验证需求》。

本轮结果与待接管边界见 [2026-10-02 接管索引](audits/2026-10-02/README.md)；按产品功能定位后续修改见 [功能与对接地图](12_FEATURE_MAP.md)。
