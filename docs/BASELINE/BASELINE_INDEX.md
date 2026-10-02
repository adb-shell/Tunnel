# Tunnel Engineering Baseline Index

2026-10-03 Windows构建修复：[TUN-BL-2026-10-03-WINDOWS-BUILD](2026-10-03_WINDOWS_BUILD_BASELINE.md)，Source 3c22a25 + T003工作树；入口、资产与验证见 [Windows指南](../plans/WINDOWS_BUILD_GUIDE.md)。

当前ADB源码增量基线：[TUN-BL-2026-10-03-ADB](2026-10-03_ADB_IMPLEMENTATION_BASELINE.md)，HEAD bc50fe5 + T001未提交补丁；仅V0，正式构建与设备验收NOT_RUN。旧基线保留为历史快照。

## 当前与历史 Baseline

| Baseline ID | 记录 | 使用边界 |
|---|---|---|
| `TUN-BL-2026-10-02-IDENTITY` | [2026-10-02_TUNNEL_IDENTITY_BASELINE.md](2026-10-02_TUNNEL_IDENTITY_BASELINE.md) | 当前身份 + 未提交P0源码；V0，尚未构建/运行 |
| `CS-BL-2026-10-02-5cee692` | [2026-10-02_SOURCE_BASELINE.md](2026-10-02_SOURCE_BASELINE.md) | 迁移前源码、库存、hash/EOL 与历史缺口；V0，正式运行未验证 |
| `CS-BL-2026-07-12-77062b4` | 以下四份旧快照 | 历史 baseline；当前本地 Git 无旧对象，不可宣称仍是当前 HEAD |

以下正文保留 2026-07-12 的记录含义；不将其当作 2026-10-02 的工作树状态。T004 按用户要求统一了历史文档中的品牌字样和路径，未改历史 ID、日期或 hash；本文及四份旧快照不再是逐字原稿，名称规范化边界见当前身份基线。旧 ORG 记录属于历史值，不代表当前 `com.tunnel`。

基线日期：2026-07-12  
Baseline ID：`CS-BL-2026-07-12-77062b4`  
Source HEAD：`77062b4d8b63eae9a31afe288e3ac00a4f89e009`  
状态：repository-side baseline accepted；formal build/runtime baseline `verification-required`

> 本目录冻结可追溯的来源、产品版本、关键依赖和正式构建环境。它不保存 credential、生产地址或本机私有配置，也不证明未执行的 build/test 已通过。

## 1. 文件职责

| 文件 | 记录内容 |
|---|---|
| `01_UPSTREAM_BASELINE.md` | RustDesk/DaXianDesk 来源证据、历史缺口和接管要求 |
| `02_VERSION_MATRIX.md` | Tunnel 产品、平台、协议/bridge 与 artifact 版本矩阵 |
| `03_DEPENDENCY_BASELINE.md` | Cargo、Flutter、Gradle、protobuf、vcpkg 等锁定状态 |
| `04_BUILD_ENVIRONMENT.md` | Android/Windows 正式构建环境、入口、外部资产和验证门 |

完整架构仍以当前源码和 `docs/AI_ENGINEERING/` 为准；外部资产状态仍以 `EXTERNAL_ASSET_REGISTRY.md` 为准。

## 2. 证据标签

| 标签 | 含义 |
|---|---|
| `SOURCE-PINNED` | 当前 tracked manifest/script/lockfile 直接固定 |
| `LOCK-PINNED` | lockfile 固定，但 manifest 可能允许更宽范围 |
| `SCRIPT-PINNED` | canonical script 固定；正式机器尚未验证 |
| `HISTORICAL` | 旧文档/workflow 的参考值，不自动成为 canonical |
| `EXTERNAL` | 值或资产由仓库外 owner 管理 |
| `DRIFT` | 两个来源不一致或声明与 lock 不一致 |
| `UNKNOWN` | 当前仓库无法证明 |
| `VERIFICATION-REQUIRED` | 需要正式环境证据 |

## 3. 基线变更控制

- 每个开发任务在 `TASK_TEMPLATE.md` 中引用 Baseline ID；不得只写“latest”。
- 产品版本、协议、身份、MSRV、Flutter/NDK/Gradle/vcpkg、driver/binary 或正式环境变化必须先做影响分析。
- 长期架构或工具链路线变化创建/更新 `docs/ADR/`；普通 lock refresh 仍需 task、review、SBOM/license 和 rollback 记录。
- manifest、lockfile、script 与正式环境证据不一致时标 `DRIFT`，不得选择一个方便的值冒充已统一。
- 基线更新只能追加新 Baseline ID；旧 baseline 不删除、不静默重写。
- accepted baseline 不授权 build、Git、version、sign、package、upload 或 release。

## 4. 当前封版边界

- 当前 source HEAD 固定，但工作树包含未提交的 AI 工程文档/Skill 资产；本目录没有把它们冒充 commit 内容。
- 本次只做 V0 静态核验，没有执行项目编译、测试、codegen、设备、服务或发布验证。
- `CS-BL-2026-07-12-77062b4` 可用于源码/文档任务；正式 release baseline 必须补齐 G2 证据、artifact hashes、signatures、SBOM 和 external asset owners。

