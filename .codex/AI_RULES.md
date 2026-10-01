# CloudSend AI Rules

最后更新：2026-07-12  
适用范围：所有 AI、agent、skill 和自动化在 CloudSend 仓库中的工作。

## 0. Mandatory Entry and Precedence

每个新会话先执行 `.codex/SESSION_START_PROTOCOL.md` 的全局记忆恢复：

1. `PROJECT_START_HERE.md`；读到启动顺序后打开 Session Start Protocol 作为执行细则。
2. `.codex/AI_RULES.md`（本文件）。
3. `.codex/PROJECT_STATE.md`。
4. `.codex/CURRENT_WORK.md`。
5. `.codex/CHANGELOG_AI.md`。
6. `.codex/DECISION_LOG.md`。
7. 相关 `docs/ADR/`。
8. 相关 `.agents/skills/<skill>/SKILL.md`。

完成恢复后、开始任何任务动作前，再读 `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`、`TASK_TEMPLATE.md` / 当前 Baseline、对应 Architecture 和验证/外部资产文档。记忆恢复与 Task Protocol 都不可跳过；前者不替代后者。

行为权限与工程事实是两套层级：

- 行为权限：用户当前明确指令 → 本文件 → Task Protocol → Development Workflow / Skills。下游规则只能收窄，不能扩大。
- 工程事实：当前源码/manifest/protocol/build script → `docs/AI_ENGINEERING/` → 旧 `docs/ENGINEERING_*` → historical/proposed 文档。

源码中存在某个命令或功能不代表 AI 获得执行权限。

## 1. Permission Boundary

| 权限类 | 允许的典型动作 | 默认规则 |
|---|---|---|
| `OBSERVE` | 本地读取、`rg`、`git status/diff/log/show`、静态检查 | 在任务范围内允许，不改变项目或外部状态 |
| `EDIT` | 创建/修改文档、Skill、代码或配置 | 只有用户明确要求创建/修改时允许；review/diagnose/report 不授权编辑 |
| `EXECUTE` | build/test/analyze/codegen、依赖安装/更新/解析、package/sign | 默认禁止；需明确动作、环境和停止边界 |
| `GIT-WRITE` | fetch/pull/add/stage/branch/switch/checkout/stash/commit/tag/revert/cherry-pick/rebase/merge/worktree/push/PR | 默认全部禁止；每组枚举动作需单独授权 |
| `DESTRUCTIVE` | 删除、移动/重命名、clean、bulk rewrite、history rewrite、secret rotation/revoke | 默认禁止；需对象清单、影响分析、恢复/回滚和明确授权 |
| `EXTERNAL` | upload/deploy/publish/release、store/cloud/production、远端配置、生产扫描 | 默认禁止；需精确目标、owner、rollout/rollback 和独立授权 |

### 明确确认的定义

- 用户直接点名动作、目标和范围，或对无歧义确认单明确同意。
- “完成、维护、接管、继续、修好、清理、发布计划”不是相邻高风险动作的授权。
- 枚举后的动作可一次批准；未枚举动作不能推断。
- 范围、环境、remote、artifact 或副作用改变后重新确认。
- 授权是必要条件，不替代环境、安全、review 或 release gate。
- script、hook、Skill、sub-agent 和 compound command 的实际副作用按同一权限类判断。
- 之前的 task/turn、其他 agent 或历史批准不自动延续。

## 2. Explicit Automatic Prohibitions

### 2.1 禁止自动 Git

- 不自动 add/stage、branch/switch、stash、commit、tag、merge、rebase、push、PR。
- 不自动 fetch/pull 或改变 worktree/submodule 状态。
- Git 只读检查不授权任何后续写操作。
- commit 授权不包含 push；branch 授权不包含 PR/merge。

### 2.2 禁止自动编译和测试

默认不执行项目 build/test/analyze/codegen 或 dependency install/update。只有用户明确授权且环境被确认适用时才能执行对应命令。

禁止自动运行：

- `cargo build` / `cargo test` 及其他 Cargo product/feature build。
- `flutter build` / `flutter test` / project analyzer/codegen。
- Gradle/Android build、APK 安装或设备操作。
- `new-build.cmd`、`build.sh`、`build.py` product build。
- Docker build、package、sign 或 publish command。

需要验证时输出：

```text
《编译验证需求》
- 命令
- 环境要求
- 执行目录
- 验证目标
```

然后等待正式环境结果。只读源码搜索、文档 link check 和 skill schema validation 不等于项目编译。

### 2.3 禁止自动删除

- 不删除、移动或重命名源码、文档、历史资产、generated artifact、binary、driver 或用户文件。
- 不运行 destructive reset/checkout/clean、递归删除或批量清理。
- “过时、重复、未跟踪、ignored”都不是删除授权。
- 删除/迁移必须先列对象、引用、知识迁移、恢复点和回滚，再单独确认。

### 2.4 禁止自动发布

- 不自动改版本、tag、sign、package、upload、deploy、publish、release 或 store submission。
- build 成功不授权签名；签名不授权上传；G2 正式验证通过不授权 G3 发布。
- 不操作 production endpoint、database、server、cloud、CI secret、signing service 或 remote config。
- 发布必须由 release owner 提供正式环境证据、artifact provenance、SBOM、signature、rollout/rollback 和独立授权。

## 3. Source Hierarchy

1. 当前源码/manifest/protocol/build script。
2. `docs/AI_ENGINEERING/`。
3. accepted `docs/ADR/` 提供 decision context，`docs/BASELINE/` 提供固定 snapshot；二者与源码冲突时记录 drift，不覆盖实现事实。
4. 原 `docs/ENGINEERING_*` 和 `docs/TASK_ENTRYPOINTS.md`。
5. `.codex`、`AGENTS.md`、`CLAUDE.md` 入口摘要。
6. README、历史设计、upstream/community 文档。

文档冲突必须回到源码。外部服务未在仓库时标 `external/unverified`，不要补写想象中的后台架构。

## 4. Worktree Safety

- 开始前记录 `git status --short --branch`，但不自动修改 Git。
- 用户已有修改属于用户；保留、隔离并避免覆盖。
- 文档/技能编辑使用 `apply_patch`。
- 禁止 destructive reset/checkout/clean、递归删除或批量品牌替换。
- 不删除旧文档；先提取、映射、标记，再等待批准。
- 不编辑 generated bridge；先改 source of truth，再在正式环境 codegen。

## 5. Evidence Discipline

每个结论标记为：

- `verified`：当前源码直接支持。
- `inferred`：由多处证据推断。
- `external`：依赖仓库外资产。
- `verification-required`：需正式 build/device/server 证明。
- `historical`：只在 Git/旧文档中存在。

风险描述使用“可能/存在路径”，直到复现。完成声明必须列验证与未验证项。

## 6. Secret and Privacy

- 永不复述、复制或提交 secret/token/password/key/Authorization value。
- 生产 IP/domain/peer/device/UUID/PII 默认脱敏。
- 发现 credential 只报告路径、类型、影响和建议，不测试其有效性。
- 未经授权不调用远端 credential、不扫描生产、不轮换、不撤销。
- 日志/截图/diagnostic bundle 先 redaction。

## 7. Cross-Layer Rules

### Android

- 分开 core service、screen share、frame source、PC waiting。
- 只有明确用户操作可请求新的 MediaProjection permission。
- waiting/reconnect 只 normal refresh，禁止自动 ignore/screenshot。
- 任意真实 RGBA/Texture 到达必须清 waiting。
- JNI 以 `pkg2230.rs` 为 active source，同时检查 `ffi.rs` compatibility。
- 命令检查 Dart→FFI→client→protocol→server→JNI→Kotlin 全链。
- UI permission/password 不是 server-side authorization。
- ADB 视为本地高权限独立子系统；远程化需新 threat model。

### Rust/Network

- controller relay-only 与 endpoint compatibility code 分开描述。
- auth/crypto failure 应 fail-closed；兼容降级需显式批准。
- protobuf field number 不复用；跨版本矩阵必需。
- unsafe/JNI/raw pointer 变更必须写 ownership/threading invariant。

### Flutter

- 明确 global/session/window lifecycle。
- timer/subscription/controller 都要有 owner 和 teardown。
- FRB signature 变更需正式 codegen 三方一致。

### Windows

- 当前 virtual display 是 Amyuni；不要误改 dormant RustDesk IDD key。
- privacy/injection/driver 变更必须 security review 和异常恢复方案。
- 快速 virtual display plug/unplug 不作为默认恢复策略。

### API

- client validation 不替代 server auth。
- 本仓库不含 backend/DB；无证据不补写。
- endpoint 变更集中配置、HTTPS、timeout、size limit、auth 和 rollback。

## 8. Documentation Rules

- 中文解释，English code/path/class/function/constant anchor 保持原样。
- 完整架构只在 `docs/AI_ENGINEERING/`；`.codex` 只做薄摘要。
- 长期决定写入 `docs/ADR/`；source/version/dependency/toolchain snapshot 写入 `docs/BASELINE/`；每个开发任务引用 `TASK_TEMPLATE.md` 和 `TEST_MATRIX.md` case IDs。
- 只有本任务获准编辑且事实/决定发生变化时，才同步对应 domain doc、`DECISION_LOG.md`、`TASK_HISTORY.md`；只读审查不得为满足 checklist 擅自写文件。
- 历史文档保留适用范围和迁移映射，不静默重写历史。
- 不把计划写成已实现，不把静态审计写成正式验证。

## 9. Skill Routing

- 跨域/接管：`cloudsend-master`。
- Rust/unsafe/FFI：`cloudsend-rust-engineer`。
- Android runtime：`cloudsend-android-engineer`。
- Flutter/UI/state：`cloudsend-flutter-engineer`。
- relay/protocol/auth：`cloudsend-network-engineer`。
- HTTP/account/backend contracts：`cloudsend-api-engineer`。
- threat/secret/permission：`cloudsend-security-engineer`。
- build/sign/release planning：`cloudsend-release-engineer`。

领域交叉时由 master 协调，security/release constraints 优先于实现便利。

按行为语义选择主责，不按文件扩展名：

- Peer/rendezvous/wire semantics 即使位于 `.rs`，仍由 Network 主责，Rust 负责实现机制。
- Product HTTP/OIDC/backend contract 由 API 主责，Flutter/Rust 负责 client implementation。
- Kotlin/Android OS lifecycle 由 Android 主责；Dart presentation 归 Flutter；Rust unsafe/ABI 归 Rust。
- Security 和 Release 默认是 constraint reviewer；安全事件/审计或构建/发布任务时才成为主责。
- 两个以上领域、bridge 两端同时变化或 owner 冲突时由 Master 主责。

## 10. Safe Superpowers Profile

- CloudSend does not install or trust an external Superpowers package by default.
- The only enabled local adapter capabilities are `brainstorming`、`planning`、`debugging`、`verification` and `review`.
- `cloudsend-superpowers-safe` always remains read-only/C0；it cannot edit files or execute a plan.
- All other Superpowers-style capabilities are denied, including worktrees、plan execution、TDD execution、branch finishing and release workflows.
- The adapter never performs commit、push or release and cannot use another Skill/sub-agent to do so.
- V1—V5、implementation and persisted documentation route back through normal C1/C2/C3 gates.
- Full policy：`docs/AI_ENGINEERING/SAFE_SUPERPOWERS_PROFILE.md`；decision：ADR-0013.

## 11. Task Protocol Compliance

- 所有任务执行 `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md` 的 T0—T8 状态机。
- 回答/审查/诊断默认停留在 `OBSERVE`，不因发现问题自动修改。
- 修改前必须通过 C1/C2；Git/build/delete/version/release/production 等 C3 动作逐类确认。
- 范围扩大、外部资产缺失、新安全风险或验证失败时，回到影响分析/方案/确认门。
- sub-agent 继承相同或更窄权限；主 agent 对整合和最终 worktree 负责。
- 完成时列出实际验证等级、未执行项和下一确认门。

## 12. External Asset Boundary

- 不在仓库或不能由 clean clone 复现的 service、database、driver、binary、signing/build/release infrastructure 必须登记到 `EXTERNAL_ASSET_REGISTRY.md`。
- 登记只保存类型、用途、仓内锚点、owner、version/provenance/hash/license 状态和所需材料；不保存 credential、生产地址或 PII。
- `missing`、`external`、`local-only` 或 `unverified` 资产不能被描述为已接管、可复现或可发布。
- 外部资产访问、下载、安装、替换、验证或生产调用仍受 `EXECUTE` / `DESTRUCTIVE` / `EXTERNAL` 权限门控制。

## 13. Completion Checklist

- 授权边界未扩大。
- initial/final worktree 已检查。
- 无用户文件被覆盖、无文件删除。
- 无 secret/PII 泄露。
- 跨层调用和兼容层已检查。
- 若有获准的事实/决定变化，文档/memory/history/registry 已同步；只读任务保持只读。
- 已执行验证和未执行验证都明确。
- 需要正式环境时已输出《编译验证需求》。
- 未经批准没有 Git、build、version、release、upload 动作。
