# 《CloudSend AI工程体系强化报告》

完成日期：2026-07-12  
范围：AI 工程入口、权限、任务协议、开发流程、CloudSend Skills、仓外资产登记和长期记忆  
变更性质：仅文档、规则与 Skills；未修改业务代码

## 1. 强化结论

CloudSend 已建立一套可执行、可审计、默认不越权的长期 AI 维护体系：

```text
PROJECT_START_HERE.md
  -> .codex/AI_RULES.md
  -> AI_TASK_EXECUTION_PROTOCOL.md
  -> current architecture/domain truth
  -> domain Skill
  -> DEVELOPMENT_WORKFLOW.md
  -> verification + memory + handoff
```

这套体系把“工程事实”“任务状态”“行为权限”和“正式发布”分开管理。源码仍是实现真相；规则和流程不能授权 AI 执行 Git 写入、编译、删除、版本、签名、上传、部署或发布。

## 2. 新的第一入口

根目录 `PROJECT_START_HERE.md` 现在是所有 AI、sub-agent、开发者和审查者的唯一第一入口，负责：

- 给出五分钟阅读顺序。
- 识别 CloudSend 身份、关键 active path 和高价值不变量。
- 要求记录 branch、HEAD 和既有 dirty state，但只允许 Git 只读检查。
- 按任务语义选择最窄的 CloudSend Skill，跨两个以上领域时由 `cloudsend-master` 协调。
- 将开发、外部资产和正式验证分别路由到对应规范。
- 明示 C0—C3 确认门和默认禁止事项。

`README.md`、`AGENTS.md`、`CLAUDE.md`、旧工程索引和 AI overview/memory 已同步指向该入口；旧文档继续保留，不删除。

## 3. 逐任务执行协议

`docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md` 建立 T0—T8 状态机：

| 状态 | 必需结果 |
|---|---|
| T0 初始化与授权 | Task Brief、基线、dirty state、禁止范围 |
| T1 需求分析 | 当前/预期行为、验收、非目标、未知项 |
| T2 影响分析 | 跨层调用链、资产分类、信任边界、Impact Map |
| T3 方案设计 | 推荐方案、替代项、不变量、兼容、回滚、验证矩阵 |
| T4 确认 | C0—C3 confirmation record；范围扩大时重新确认 |
| T5 修改 | 只在授权范围内做最小、可逆、可审查变更 |
| T6 验证 | V0—V5 分层证据；未执行项保持未验证 |
| T7 文档与记忆 | Architecture、Decision、Task、External Asset 按事实同步 |
| T8 交付 | 结果、变更、证据、风险、回滚和下一确认门 |

协议明确：sub-agent、Skill、脚本和工具不能继承不存在的权限；静态审查不能冒充 build/device/integration evidence。

## 4. 权限与自动化边界

`.codex/AI_RULES.md` 现在按动作类型管理权限：

| 动作类 | 默认状态 |
|---|---|
| `OBSERVE` | 任务范围内允许，只读且不改变外部状态 |
| `EDIT` | 只有用户明确要求的文档/规则/代码范围才允许 |
| `EXECUTE` | build/test/analyze/codegen/install/package/sign 默认禁止 |
| `GIT-WRITE` | 所有改变 Git/worktree/remote 状态的动作默认禁止 |
| `DESTRUCTIVE` | 删除、移动、清理、历史改写、credential rotation 默认禁止 |
| `EXTERNAL` | upload/deploy/release/production/cloud/store 默认禁止 |

确认不能跨任务、跨动作类或跨环境自动继承。`commit` 不包含 `push`，开发确认不包含 build，build 确认不包含签名/发布，发布计划也不构成发布授权。

## 5. 八个 CloudSend Skills 审查

八个 Skill 均已逐项检查并强化：frontmatter description 明确触发条件，正文明确职责、工作流、禁止事项和验证方式。

| Skill | Primary trigger / 职责边界 | 五项要求 |
|---|---|---|
| `cloudsend-master` | 两个以上领域、架构、迁移、事件、接管；统一 acceptance owner | 通过 |
| `cloudsend-rust-engineer` | Rust/Cargo/unsafe/FFI/JNI/Windows runtime mechanics | 通过 |
| `cloudsend-android-engineer` | Kotlin/Java/Android OS lifecycle、capture、Accessibility、ADB | 通过 |
| `cloudsend-flutter-engineer` | Dart UI/state/window/bridge consumer lifecycle | 通过 |
| `cloudsend-network-engineer` | peer/rendezvous/session/protobuf/auth/relay semantics，不按文件扩展名路由 | 通过 |
| `cloudsend-api-engineer` | product HTTP/OIDC/account/sync/backend/schema/database boundary | 通过 |
| `cloudsend-security-engineer` | 安全审计、漏洞、credential incident、threat model；普通任务作为 reviewer | 通过 |
| `cloudsend-release-engineer` | build/release planning、provenance、signing、SBOM、rollout/rollback | 通过 |

共同规则：先读新的入口/规则/协议；按 T0—T8 工作；domain Skill 不增加权限；文档同步只发生在已授权且事实确实变化时。

## 6. 仓外资产登记

根目录 `EXTERNAL_ASSET_REGISTRY.md` 已建立统一 ID、状态词汇、owner/provenance 要求、release impact 和 intake template，覆盖：

- Product API/OIDC backend、业务 database、DNS/TLS/observability。
- `hbbs`、`hbbr` 与 relay-only 所需服务端边界。
- ZEGO token broker、RTC tenant/SDK 和 credential ownership。
- Android ignored/local-only `libadb.so` 及 ADB/LADB 研究材料。
- Windows Amyuni driver、installer、injection DLL、printer driver/adapter、OS-derived helper 和 dormant IDD。
- Android/Windows 正式 build host、签名、artifact registry、CI/CD、distribution channel。
- RustDesk/DaXianDesk upstream baseline、Git/package dependency provenance 和 AGPL corresponding source。

关键结论：repository-side 架构已接管，但 backend/hbbs/hbbr/database、正式 ZEGO、driver/binary provenance、签名和发布控制面尚未接管；clean clone 目前不能证明 Android ADB 或 Windows canonical package 可复现。

## 7. 长期开发流程

根目录 `DEVELOPMENT_WORKFLOW.md` 规定未来开发必须留下五类证据：为什么改、影响哪里、怎么改、如何证明、如何维护。固定流程为：

```text
分析 -> 计划 -> 确认 -> 修改 -> 验证 -> 记录 -> 交付
```

G0—G4 review gates 分别覆盖安全处置、开发、正式验证、发布、删除/迁移。Gate 通过只是工程准入，不会自动授权 AI 执行动作；C2/C3 用户确认仍然独立必需。

## 8. 验证结果

本轮只执行 V0 静态验证：

- 八个 `SKILL.md` 通过 `skill-creator` 官方 `quick_validate.py` 规则；环境未安装 PyYAML，因此未安装新依赖，而是为这些简单 frontmatter 注入只读的最小 YAML parser 后执行原 validator 逻辑。
- 八个 `agents/openai.yaml` 的 short description 长度和 `$skill-name` prompt reference 全部通过。
- 所有 Skill 均包含 trigger、responsibilities、workflow、forbidden actions、verification。
- 跨域前向案例“新增 peer protobuf 字段并联动 Rust/Android/Flutter”正确路由为 Master acceptance、Network semantic owner、Rust/Android/Flutter implementers、Security/Release reviewers；在 C2 前停止修改，在单独 C3 前停止 codegen/build/Git/release。
- 新入口与主导航引用已做静态检索；外部资产存在性按 tracked/ignored/generated/external 分类复核。
- 未执行任何 CloudSend build、test、analyze、codegen、设备或服务集成验证。

## 9. 本轮权限遵守

- 业务源码：未修改。
- Git：未 stage、commit、branch、merge、rebase、push 或创建 PR。
- 编译/测试：未执行。
- 删除/移动：未执行。
- 版本/签名/打包/上传/部署/发布：未执行。
- credential：未验证、未复述其值、未轮换或撤销。

## 10. 后续治理重点

1. 由 owner 为 `EXTERNAL_ASSET_REGISTRY.md` 的 `BLOCKING` 条目补齐负责人、source/version、contract、hash/signature、license 和 runbook。
2. 安全 owner 优先处理已登记的 credential exposure，不把“删除当前字面量”误认为完整处置。
3. Release owner 在正式环境建立 immutable dependency/artifact manifest，并回填 V2—V4 证据。
4. 每次事实变化同步 AI Engineering 文档、Decision Log、Task History；每次 release readiness 复核外部资产状态。
5. 旧文档只在知识迁移和用户明确批准后进入删除/归档流程。
