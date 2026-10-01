# 《CloudSend AI总工程师接管报告》

接管日期：2026-07-12  
源码基线：`HEAD 77062b4`，branch `main`  
接管角色：CloudSend Principal Engineer  
接管方式：源码、文档、Git 历史与公开仓库资产的静态审计

> 本轮没有修改核心业务代码，没有删除文件，没有编译或测试，没有 stage/commit/push/merge/rebase，没有修改版本，没有签名、上传、部署或发布。已创建的内容仅为工程文档、长期 AI 记忆、入口规则和项目专属 Skills。

> 2026-07-12 强化说明：本报告是接管时的历史快照，不再作为当前操作规则。所有新任务从根目录 `PROJECT_START_HERE.md` 进入，并服从 `.codex/AI_RULES.md` 与 `AI_TASK_EXECUTION_PROTOCOL.md`。

## 接管结论

CloudSend 的仓库内端点资产已经完成第一轮工程接管：核心架构、主要运行链、二开能力、历史、文档可信度、技术债、风险、维护规则和正式验证需求均已建立可检索基线。

但“全系统接管”和“发布就绪”尚未成立：

- `hbbs`、`hbbr`、产品 API backend、数据库的 server source，以及 ZEGO token broker 的独立受控 project/dependency lock/生产运维资产不在本仓库；token deployment script 内嵌部分 Go server source，不能等同完整接管。
- Android ADB native binary、Windows driver/helper/DLL 等关键产物缺少 clean-clone provenance。
- 正式 Android/Windows 构建和设备矩阵没有在本轮执行。
- 公开仓库 credential exposure、transport/crypto、update/plugin、Android/Windows 高权限边界存在 release-blocking 风险。

因此当前状态是：

| 维度 | 状态 |
|---|---|
| repository source understanding | 已接管 |
| architecture/document memory | 已建立 |
| static security/release audit | 已完成第一轮 |
| external backend/infrastructure | 资产缺失，未完成 |
| formal build/device verification | 未执行，待正式环境 |
| production release readiness | 不通过 |

## 1. 项目理解

### 1.1 身份与来源

CloudSend 是 RustDesk 的深度二次开发产品，Android 可见名为 `云计划`。当前身份锚点：

- Rust crate/library：`cloudsend`。
- Flutter package：`flutter_hbb`。
- Android applicationId：`com.cloudsend.app`。
- Android deep link：`cloudsend`。
- Android native library：`libcloudsend.so`。
- Windows native library：`cloudsend.dll`。
- 当前源码版本：Rust `5.2.1`，Flutter `5.2.1+59`。
- 根许可证：AGPL-3.0。

当前 Git 并非“运行多年”的完整历史证据：仓库只有 59 个本地 commits、无 tag、无 merge commit，根提交是 2026-04-13 的 `DaXianDesk` 一次性导入。导入前 RustDesk fork point、上游 commit、作者和二开演进无法从本仓库证明。

### 1.2 规模与组成

接管扫描得到：

- 901 个 tracked files，约 14.47 MiB。
- Rust 约 135,562 行 / 258 文件。
- Dart 约 80,376 行 / 119 文件。
- Kotlin 约 8,836 行 / 18 文件。
- Java 3 文件；C/C++ 与 protobuf 仍构成平台/协议支撑。
- Cargo root crate + 8 个 workspace members。
- 39 个 tracked Markdown、9,813 行。

这不是单一 Flutter 应用，而是：Rust controller/server core、Flutter/legacy UI、Android Kotlin/JNI runtime、Windows privacy/virtual-display、外部 account/API/sync、file/terminal/tunnel、ZEGO voice 和本地 ADB 等多条系统并存。

### 1.3 Git 演进阶段

| 时间 | 主要演进 |
|---|---|
| 2026-04-13 | DaXianDesk/RustDesk 代码一次性导入 |
| 04-16—04-22 | Android 黑屏、防触、状态和双链路稳定 |
| 05-05—05-13 | CloudSend 品牌/package/SO/DLL/Windows 构建迁移 |
| 05-20—05-31 | Android 本地 ADB/LADB、mDNS、wireless-debug automation |
| 05-31—06-07 | ZEGO voice media 与 token-service integration |
| 06-09—06-24 | relay-only reconnect、core/share 分离、首帧/授权稳定 |
| 06-25—07-01 | Dev selector、黑屏帧、画质与交互兼容 |
| 06-27—07-10 | 多次 server endpoint migration |

历史 commit subject 质量不稳定，缺 PR/tag/ADR，无法解释大量“为什么”。新的 `DECISION_LOG.md` 与 `TASK_HISTORY.md` 用于从现在开始补齐决策证据，不会伪造过去的理由。

## 2. 架构总结

### 2.1 总体拓扑

```text
Flutter UI / legacy desktop UI
  <-> FRB + handwritten C FFI
  <-> Rust controller/server core
  <-> protobuf peer protocol
  <-> hbbs rendezvous + hbbr relay (external)
  <-> remote endpoint

Android endpoint
  Rust core <-> pkg2230 JNI <-> MainService / Accessibility / MediaProjection / local ADB

Windows endpoint
  Rust core <-> capture/input <-> privacy helper/injection <-> Amyuni virtual display

Product services
  Flutter/Rust HTTP clients <-> external account/device/sync API
  Rust invitation control <-> Flutter ZEGO SDK <-> external token broker/RTC
```

### 2.2 Rust core

- `src/client.rs` / `src/client/io_loop.rs`：控制端连接、登录、消息循环和服务请求。
- `src/server.rs` / `src/server/connection.rs`：受控端入口、认证、权限、消息分发。
- `src/rendezvous_mediator.rs` / `src/client.rs::{request_relay, create_relay}`：ID registration、rendezvous、relay 和兼容 direct/NAT path。
- `src/server/video_service.rs` + `libs/scrap`：平台采集、编码和视频服务。
- `src/server/input_service.rs` + `libs/enigo`：输入注入。
- `src/client/io_loop.rs` / `src/server/connection.rs` / `libs/hbb_common/src/fs.rs` / `src/server/terminal_service.rs`：文件与 terminal。
- `src/flutter_ffi.rs`：Flutter 主要 Rust API source。
- `libs/hbb_common`：config、protobuf、transport 和 shared utilities。

### 2.3 Flutter

Flutter 不是单一导航树：`main.dart` 按 mobile/desktop/命令行/multi-window 分流。Desktop 可独立或 Tab 化 Remote、File Transfer、Camera、Port Forward、Terminal；当前 mobile home 实际只暴露 Server 与 ADB 页，其他页面源码多为 retained/inactive entry。

状态管理混用 Provider、GetX 和 StatefulWidget。`FFI` 聚合 session 的 image、input、file、terminal、voice、user、server 等 models，便利但 ownership 和 teardown 边界模糊。

三条 native bridge：FRB generated bridge、手写 C FFI、Android MethodChannel。

### 2.4 Android

Android 必须采用四层状态模型：

```text
core service != screen share != frame source != PC first-frame waiting
```

- Core：MainService、JNI context、Rust ID/relay。
- Share：MediaProjection、ImageReader、VirtualDisplay、`captureStarting`。
- Source：normal、SKL Accessibility tree、ignore/one-shot screenshot。
- Waiting：PC `waitForFirstImage`/timer；任意真实 RGBA/Texture 清理。

Android 14+ MediaProjection token one-shot；projection loss 只丢 screen share，不得杀 core。Hidden boot/reconnect/legacy path 不得弹授权，只有明确用户操作可以请求新 token。

当前 active JNI 只有 `pkg2230.rs`；`ffi.rs`/`ffi.kt` 是未导出的 compatibility layer，二者存在生命周期差异。

### 2.5 Windows

- Capture：DXGI/GDI/portable branches 与 `scrap` 平台实现。
- Input：Enigo/SendInput/portable input。
- Privacy：magnifier、topmost/injection、virtual display 等多种实现。
- Virtual display：当前 `IDD_IMPL` 是 Amyuni；RustDesk IDD branch 是 dormant，配置 key 不可混用。
- Remote printer：protocol、service、driver/adapter 和 Flutter UI 的跨层能力。

### 2.6 Network 与 API

CloudSend controller 当前强制 relay、跳过 direct/UDP/IPv6 candidates 并拒绝显式地址；受控端仍保留 direct server/LAN/NAT/PunchHole compatibility code。因此不能写成“全产品已删除 direct”。

产品账号主链在 Flutter，Rust account 主要承担 OIDC。`src/hbbs_http` 是客户端集成，不是 backend。仓库内无业务 DB/schema/migration/backup。

## 3. 当前二开能力

### 已上线或主链可达

- CloudSend/云计划品牌、package、SO/DLL、deep link 和构建产物迁移。
- Remote desktop、video/input、clipboard、file transfer、chat、terminal、tunnel/port forward、camera、recording、remote printing 等上游能力保留。
- Controller strict relay-only 与 Android 单 timer reconnect/60 秒静默恢复。
- Android core/share 分离、normal/SKL/ignore 三帧源、waiting normal refresh、真实状态包、side controls、blank/touch block/status overlay。
- Android local ADB/LADB：pair/connect、mDNS、preferred serial、local shell、wireless debugging automation。
- ZEGO voice：peer protocol invitation + Flutter RTC media。
- 产品账号、current user、地址簿、设备分组、heartbeat/config sync、download client。
- Windows privacy mode、Amyuni virtual display、capture/input 与 printer integration。

### Active 但高风险/需重新决策

- Android Dev/contact selector、Accessibility、overlay、ADB high privilege。
- ZEGO auto-accept/microphone consent 和静态 token-service auth。
- Android custom input command 的 server-side permission enforcement。
- Android custom-scheme config import。
- Update/download/execute path。
- Windows injection/privacy/driver/helper loading。

### Dormant、compatibility 或未实现

- `record_upload` 默认 disabled 且无可达 enable path。
- RustDesk IDD virtual display branch 当前不是 active implementation。
- Android `ffi.rs`/`ffi.kt`、legacy `init_service`/`start_capture` 是 compatibility path。
- Plugin source/list 默认状态下可能 dormant，但 archive extraction 风险在启用前必须修复。
- PC remote ADB command protocol 尚不存在。
- Mobile connection/settings 等页面源码保留但不在当前主页入口。
- Terminal persistence 旧设计未实现，当前临时会话使用 `ts_<uuid>`。

## 4. 核心技术路线

### 4.1 保持 RustDesk protocol/core，围绕平台增强

现有产品价值建立在成熟的 cross-platform controller/server、protobuf、video/input/file/terminal 基础上。短期不建议重写 core，而应先明确 upstream baseline、修复本 fork 的安全和生命周期边界、建立回归矩阵。

### 4.2 Controller relay-only，受控端兼容策略待决策

当前路线已选择 controller relay-only。下一步必须由产品/安全决定：继续只约束 CloudSend controller，还是全局关闭 endpoint direct/NAT compatibility。后者是协议/兼容迁移，不是删一个常量。

### 4.3 Android core/share/frame 解耦

继续坚持四层状态、显式 MediaProjection 授权、projection loss 不杀 core、waiting 不切 fallback。任何“修黑屏”方案都不得回退到 hidden permission、自动 ignore 或重绑 VirtualDisplay。

### 4.4 ZEGO 只作为媒体面

Rust peer channel 保留 invitation/control，ZEGO 承担媒体。必须用 authenticated CloudSend session 换取短期 room/user-bound token，并把用户麦克风 consent 作为独立 gate。

### 4.5 Windows 高权限子系统隔离

Privacy、DLL injection、virtual display、driver/installer 需要独立 provenance、签名、失败恢复和 security review，不应与普通 UI/remote control 逻辑共享宽松错误语义。

### 4.6 External backend contract-first

先接管 OpenAPI、backend source、DB/schema/migration、token/ACL、backup/restore 和运维 owner，再扩展账号、设备管理、record upload。客户端 model 不能代替 server contract。

## 5. 风险区域

### Release-blocking P0/P1

1. Public credential incident：ZEGO/token-service 类型的真实 credential 已进入公开仓库与历史。
2. Shared fixed remote password：公开构建内置共享远控密码，setter 实际不更新。
3. Peer transport：signed-key error fail-open 与 secretbox 双向 key/nonce 序列复用。
4. HTTP control/data：login、API、sync、recording、ZEGO token 的 plaintext/auth boundary。
5. Android deep link：可静默改写 rendezvous/API/trust key，URI 还可能携带/记录密码。
6. Update/plugin：update executable 缺签名/hash/host gate；plugin archive 有 zip-slip path。
7. Android native：ImageReader DirectBuffer lifetime、`static mut` data race、shared buffer/service GlobalRef。
8. Android permissions：input/custom command server enforcement、Accessibility/ADB/overlay/microphone/auto-accept。
9. Windows：unverified injected DLL/driver/helper、DLL search order、privacy fail-open、无 Authenticode release stage。
10. CI/supply chain：manual workflow 仍含 stale RustDesk paths、mutable actions、debug/unsigned publish、auto commit/push。

### 外部未知

- hbbs/hbbr production version、config、key、logs。
- backend password storage、token issue/revoke、tenant ACL、database injection/backup。
- record storage/ACL/retention。
- ZEGO broker rate limit、audit 和 production topology。

完整 risk register 与人工处置顺序见 `10_SECURITY_MODEL.md`。

## 6. 技术债

### 架构与生命周期

- Flutter `FFI` 聚合过宽；global/session/window ownership 不清。
- `ServerModel` 500ms timer 无引用/取消；StatelessWidget controller dispose 无效。
- Android MainService、AccessibilityService、`pkg2230.rs` 文件过大且状态全局化。
- Android scaled/physical screen metrics 混用，存在 scale=0 和反复 rebind path。
- ADB 100ms 双 polling、async-dispose、process output/deadlock 和 old-SDK boundary。
- Windows virtual display count process-local，rapid plug/unplug 有 crash warning。

### Build 与 reproducibility

- ignored `libadb.so` 和 external driver/helper assets 缺 provenance。
- FRB generated files 与后续 FFI 修改存在 drift 风险。
- `workspace.exclude` 指向不存在目录，`src/version.rs` 被旧文档误当 tracked source。
- `cli` feature 接口可能漂移，需正式编译确认。
- 32 处左右 Cargo Git dependency 未在 manifest 固定 `rev`。
- GitHub workflows 无自动 PR quality gate，且遗留 release workflow 不可信。

### Test 与 observability

- Rust test 数量有限，Flutter test 极少，Android tests 缺失。
- 无可证明 required CI checks。
- session correlation、permission denial、first-frame、relay/auth error taxonomy 不统一。
- 历史 Android keepalive 实验缺原始日志，难以复现。

### 文档与治理

- 39 份 Markdown 中 A=5、B=18、C=12、D=4。
- README、Security、Contribution 等仍主要指向 upstream RustDesk。
- `AGENTS.md`/`CLAUDE.md` 高度重复且曾不同步。
- CHANGELOG 截止 2026-06-13，混入会话元数据且漏后续变更。
- 无 upstream baseline/tag、PR、formal ADR、release/source provenance。
- third-party NOTICE/SBOM/security contact 不完整。

## 7. 未来开发路线

### Phase 0：先止损

- 人工轮换/撤销公开 credential，停用客户端共享 token-service secret。
- 制定每设备独立远控凭据迁移。
- 暂停 update/plugin/legacy release workflow，直至签名/hash/provenance 完成。
- HTTPS/authenticated sync、peer fail-closed handshake、directional key/nonce redesign。
- 修复 Android owned frame buffer、concurrency、input permission、deep-link、voice consent。
- 建立 Windows DLL/driver signature/provenance 和 Authenticode release gate。

### Phase 1：可重复工程基线

- 固定 Rust/Flutter/NDK/JDK/vcpkg/FRB 和所有 external binaries。
- 建立 clean-clone Android/Windows build manifest、SBOM、third-party notices。
- 恢复最小 hardened CI：format/lint/unit/bridge drift/secret/dependency checks。
- 建立 Android、protocol、Flutter lifecycle、Windows privacy/display 和 API contract tests。
- 全链 structured logging/correlation/redaction。

### Phase 2：结构性重构

- 收敛 Flutter model ownership 与 timers/subscriptions teardown。
- 把 Android global/JNI state 收敛为受测 state objects，拆分超大 service。
- 明确 endpoint direct/NAT 兼容策略。
- 接管 backend/OpenAPI/DB/backup/token/ACL。
- 隔离 Windows privacy/injection/driver subsystem。

### Phase 3：治理后再扩展功能

- 建立 upstream baseline、CVE/diff process、CloudSend security contact、license/source offer。
- 通过 decision/task history 记录所有重大变更、验证和 rollback。
- 之后再评估 PC remote ADB、record upload、terminal persistence、新 capture/display backend。

详细 gate 和 90 天建议见 `11_ROADMAP.md`。

## 8. 后续 AI 协作规则

### 权限

未经用户明确确认，AI 不得：

- stage/commit/push/merge/rebase/branch/PR。
- 删除源码、文档、历史资产。
- build/test/sign/package/version bump。
- upload/deploy/release/publish。
- secret validation/rotation/revoke、production scan、history rewrite。

### 工作顺序

1. 先读根目录 `PROJECT_START_HERE.md`，再读 `.codex/AI_RULES.md` 与 `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`。
2. 记录 source baseline 和 dirty state。
3. 以源码为真相，文档结论标 evidence level。
4. 选择最窄的 CloudSend domain Skill；跨域由 `cloudsend-master` 协调。
5. 追完整跨层链，不做局部猜测。
6. 保护用户修改、secret、PII、compatibility 和 rollback。
7. 同步 domain doc、decision log、task history。
8. 无正式环境时输出《编译验证需求》，绝不把静态审查说成已通过。

### 项目 Skills

已创建并完成结构验证：

- `cloudsend-master`
- `cloudsend-rust-engineer`
- `cloudsend-android-engineer`
- `cloudsend-flutter-engineer`
- `cloudsend-network-engineer`
- `cloudsend-api-engineer`
- `cloudsend-security-engineer`
- `cloudsend-release-engineer`

每个 Skill 都包含使用场景、工作流、禁止事项、检查清单和验证方式。External Skills 当前只评估、不批量安装；CloudSend `AI_RULES` 永远优先。

## 9. 文档与知识资产交付

### AI Engineering 真相层

- `00_PROJECT_OVERVIEW.md`
- `01_ARCHITECTURE.md`
- `02_SOURCE_MAP.md`
- `03_MODULE_DESIGN.md`
- `04_ANDROID_PIPELINE.md`
- `05_WINDOWS_PIPELINE.md`
- `06_NETWORK_PROTOCOL.md`
- `07_API_SYSTEM.md`
- `08_BUILD_SYSTEM.md`
- `09_DEBUG_SYSTEM.md`
- `10_SECURITY_MODEL.md`
- `11_ROADMAP.md`

### 审计与迁移

- `DOCUMENT_AUDIT_REPORT.md`
- `LEGACY_DOCUMENT_MIGRATION_REPORT.md`
- `EXTERNAL_SKILLS_ASSESSMENT.md`
- 本接管报告

### 长期 AI 记忆

- `.codex/PROJECT_MEMORY.md`
- `.codex/ARCHITECTURE_MEMORY.md`
- `.codex/DECISION_LOG.md`
- `.codex/TASK_HISTORY.md`
- `.codex/AI_RULES.md`

旧文档没有删除或整份恢复。知识已按 migration report 提取和映射；旧 `ENGINEERING_*` 保留为历史细节，新入口已经在 `AGENTS.md`、`CLAUDE.md`、README 和旧索引顶部标明。

## 10. External Skills 决定

当前不批量安装任何候选：

- `superpowers`：只借鉴 debugging/planning/verification/review；完整 TDD/worktree/commit flow 与当前权限冲突。
- Rust skills：只选 unsafe/FFI/async/error/observability 的版本无关规则；必须锁定 Rust 1.75/edition 2021。
- Trail of Bits：未来可小范围引入 audit-context、insecure-defaults、differential-review、Rust/supply-chain/static analysis，需固定 revision、license 和工具权限。
- Flutter 官方 Skills：只作为 Dart/UI/测试参考，由项目 Skill 覆盖 legacy GetX、FRB、MethodChannel、多窗口和 Android 高权限不变量。
- Flutter artifact security skill：等正式 APK/AAB 存在后再作为 release gate。
- GitHub engineering skills：只考虑 Actions hardening/efficiency、secret scanning、dependency/code scanning；避开自动 commit/branch/deploy/release。

完整评估见 `EXTERNAL_SKILLS_ASSESSMENT.md`。

## 11. 《编译验证需求》

本轮没有执行项目构建或测试。正式验证清单已集中到 `09_DEBUG_SYSTEM.md`，包括：

- Rust workspace/feature/CLI/FFI。
- Flutter analyzer/test/lifecycle/multi-window。
- Android signed aarch64/universal builds 与 Android 10/13/14/15 真机。
- Android DirectBuffer/data-race/MediaProjection/input/ADB/voice 专项。
- Windows `new-build.cmd`、capture/input/Amyuni/privacy/printer/signing。
- isolated protocol/crypto/API/deep-link/update/plugin/security tests。

正式环境结果必须回填 commit、dirty state、toolchain、commands、exit codes、artifact hashes、matrix、sanitized logs、reviewer 和 date。在结果回填前，所有运行结论保持 `verification-required`。

## 12. Principal Engineer 接管承诺

从本基线起，CloudSend 的维护应遵循三条底线：

1. 不用“能连接/能出画面”替代安全、权限和生命周期正确性。
2. 不用文档、UI 或历史习惯替代当前源码与正式验证。
3. 不在没有 owner、rollback、provenance 和 evidence 的情况下发布高权限远控软件。

仓库知识已经从“散落在源码、旧文档和个人记忆”转化为可持续维护体系；下一阶段应由项目 owner 先决定 P0 security incident response 和正式环境验证窗口，再进入任何业务开发。
