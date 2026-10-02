# T-2026-10-02-001：Tunnel 源码复核与接管文档整理

创建：2026-10-02（Asia/Shanghai）；Task Template：根目录 `TASK_TEMPLATE.md`。

## 0. Metadata / Session Recovery Record

| 字段 | 记录 |
|---|---|
| Status / T-state | complete / T8；repository-side C1 documentation |
| Requester / Product Owner | 当前项目用户 |
| Principal / Skill | Codex primary agent / `tunnel-master` |
| Domain reviewers | Android/Flutter；Rust/Network/Windows；API/Security/Release 三个协作 agent |
| Source | detached HEAD `5cee6921ec10971bb4654bc010f9328d7f70d02b` |
| Initial dirty / conflict | clean；无已有用户改动、无活动任务 |
| Recovered state | `STATE-20260712-005` / `WORK-20260712-005` / `CE-20260712-T004-06` |
| Consistency | drift：旧 state 记录 main/77062b4/dirty；当前 HEAD 不同，旧对象本地不存在 |
| Baseline | 旧 `CS-BL-2026-07-12-77062b4` 保留为历史；本轮建立 `CS-BL-2026-10-02-5cee692` |
| Related ADR / Decision | ADR-0001—0013；D-001—014，沿用已记录决定，不重新批准产品架构 |
| Risk / highest verification | 文档修改 low；被审计的 runtime/security 风险独立记录；本任务要求 V0 |
| Historical authority inherited | none |
| Change events | `CE-20261002-T001-01`：计划/任务登记；`CE-20261002-T001-02`：审计、文档和收尾同步 |

## T0 / T1. Authority、目标与验收

目标：以当前仓内源码为依据，建立可用于后续需求定位的项目地图、功能链、证据清单、文档分层和可恢复记忆；明确尚未掌握的仓外资产及运行验证。

- C0：本地源码、Git 元数据、文档和资产存在性静态核查，当前用户已授权。
- C1：当前用户已授权整理、修正及新建项目文档/地图/记忆，并明确允许多 agent 协作。
- C2：本阶段不修改业务代码、配置、协议、依赖或构建脚本；用户将在后续提出实现需求。
- C3 Git、build/test/analyze/codegen/device/integration、delete/move、version/sign/package、upload/deploy/release、production/credential：均未授权且不执行。
- 非目标：逐行证明所有源码正确、承诺无缺陷、对现有历史做无法复核的归因、证明服务器/设备可用。
- 停止条件：意外并发写入、源码变更、敏感值外泄风险或需产品取舍；可继续不依赖该问题的本地核查。
- unknown owner：外部 backend/DB/hbbs/hbbr/ZEGO、正式构建和原生资产 owner 待项目方提供。

验收条件：

1. 固定当前 baseline，解释旧记忆漂移，保留历史证据。
2. 关键模块及跨层功能均有入口、实现、状态/权限、外部边界和验证案例定位。
3. 对旧文档关键结论回到源码核实，修正已证实错误；未证明部分明确降级为 historical / inferred / external / verification-required。
4. 文档有单一职责及导航，不新增重复规则体系；旧文档不删除。
5. 本轮变更路径/引用/差异/敏感内容完成 V0；Task/Event/State/Current Work 指针同步。

## T2. Impact Map / 工作分配

| Owner | 入口与范围 | 独占文档写范围 | Evidence / cases |
|---|---|---|---|
| Android/Flutter agent | Kotlin/JNI/MethodChannel、frame/waiting/reconnect、ADB/ZEGO、Flutter startup/window/model | `04_ANDROID_PIPELINE.md`、`ANDROID_FLUTTER_AUDIT.md` | 源码 V0；AND/FLT、RST-03/04、NET-07、E2E-01/05 待运行 |
| Rust/Network/Windows agent | crates/FFI、client/proto/endpoint、file/terminal/tunnel、capture/privacy/Amyuni | `03_MODULE_DESIGN.md`、`05_WINDOWS_PIPELINE.md`、`06_NETWORK_PROTOCOL.md`、`RUST_NETWORK_WINDOWS_AUDIT.md` | 源码 V0；RST/NET/WIN、E2E-02/04 待运行 |
| API/Security/Release agent | HTTP/account/OIDC/sync/download、broker、security、build/dependencies/assets/CI | `07_API_SYSTEM.md`—`10_SECURITY_MODEL.md`、`EXTERNAL_ASSET_REGISTRY.md`、`API_SECURITY_RELEASE_AUDIT.md` | 源码 V0；API/安全相关 AND/NET/WIN、E2E-03/05 待运行 |
| Primary agent | 全仓 inventory、history、功能/文档导航、交叉复核、闭环 | 00/01/02、audit index/feature map、baseline、入口、memory/logs | V0 集成复核 |

资产分类沿用 active / compatibility / dormant / generated / tracked / ignored-local-only / external / missing；物理存在性单独复核，不从 ignore pattern 推定资产存在。

协议号、持久化 key、生成桥、跨版本客户端、OS/ABI/window/session 和服务端契约是后续修改的兼容面；本轮无行为变更。

## T3 / T4. Design / Confirmation Record

- 推荐：保留已有 canonical 00—11 和治理文件；源码复核后做定点更正；新增本轮 dated audit、功能入口地图和 source baseline。
- 不采用：全量重写文档（丢失历史、重复规则）、删除旧文档（未经授权）、运行构建来代替源码核验（当前未授权/无正式环境证据）。
- 顺序：恢复记忆 → baseline/inventory → 并行源码核验 → 主 agent 交叉核验 → 文档定点校正 → V0 → memory transaction → 关闭。
- ADR assessment：N/A — no decision delta；采用既有 ADR-0011/0012 的文档层级和协议，不新造 D-ID。
- Confirmation：项目用户于本次请求明确要求文档整理/修订和多 agent；C1 原请求已足够，无需再次确认。
- Reconfirmation trigger：扩大到代码/规则改造、删除/迁移、Git、构建、外部系统或生产动作。
- Rollback：文档新增/patch 可由后续获准 patch 撤销；不改 runtime/config/data，无 migration 或不可逆操作；旧 snapshots 不覆写。

## T5. Change Record

计划与实际文件见本轮 audit index 及 `CE-20261002-T001-02`。所有改动为手写文档；generated source 不修改。初始 clean，不存在要覆盖的用户修改；agents 仅写分配的文档，primary 独占共用日志。

## T6. Verification Plan

| Level | 计划 | 状态 |
|---|---|---|
| V0 | tracked inventory、源码锚点、文档 link/path、diff/空白/敏感值、事件指针 | PASS于本轮文档验收范围；不代表runtime安全 |
| V1—V5 | 复用 `TEST_MATRIX.md` cases，不在本任务执行 | NOT_RUN；本任务不以运行通过为验收条件 |

正式构建与设备/服务验证请求在本轮 audit index 中登记；不把安全目标 oracle 当成当前实现已满足的事实。

## Security / Build / Release Review

- Source→sink、认证/权限/consent、crypto/HTTP、JNI lifetime、ADB/driver、下载/plugins、native assets/CI 分别由领域审计记录。
- 不写敏感值、真实 endpoint、个人信息；不验证凭据，不联网检查远端公开性。
- Canonical Android/Windows build 仅作源码入口定位；V2—V4、SBOM、hash/signature、安装回滚、external owner 仍需正式证据。
- G2/G3 未通过；本任务不改变 release readiness。

## T7 / T8. 同步与关闭

已同步canonical docs、Baseline、External Registry、Project/Architecture Memory、Task History、AI Changelog、Project State、Change Events；Current Work在上述同步后最后关闭。Decision Log / ADR：reviewed-no-change / N/A — no decision delta。规则/Skill本体未改变；TEST_MATRIX仅补当前baseline/NOT_RUN边界。

验证实绩：原HEAD 975文件/104Markdown清点；三个领域审计+第二轮交叉复核；9个HEAD blob hashes独立重算一致；新增/修改文档local link/fence/path检查、diff whitespace和敏感内容模式检查。引用检查中缺失的jniLibs、version.rs、macOS generated header与未建立test目录均在文档中明确为missing/generated/absent，不伪造文件使检查变绿。初轮空白和几处符号/状态语义问题已修正，最终scope与memory指针复核通过。

交付：8份新文档、既有领域/入口/历史补注与记忆记录；所有变更都是Markdown。工作区由初始clean变为本轮docs-only dirty，未stage/commit，HEAD不变。没有业务、依赖、版本、运行或外部状态变化；旧文件均保留。

掌握边界：关键链repository-side定位完成，未声明逐行审计或外部infra/所有OS/设备可用。完整覆盖及材料清单见README；V1—V5 NOT_RUN、release BLOCKED。

下一步：用户提出具体功能修改后，由12_FEATURE_MAP定位入口/对接/测试；C2实现范围和C3正式验证按该需求处理，本轮不自动进入业务修改。
