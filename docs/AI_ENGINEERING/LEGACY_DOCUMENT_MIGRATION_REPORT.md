# CloudSend 旧文档迁移报告 / Legacy Document Migration Report

报告日期：2026-07-12  
源码基线：Git <code>HEAD 77062b4</code>  
迁移方式：知识提取和映射，不移动、不删除、不恢复现有或历史文件。

> 本报告只记录迁移策略与证据。
> 本次没有删除旧文档，没有恢复 Git 历史中已删除的文档，也没有修改任何业务代码。

---

## 1. 迁移目标

旧文档迁移不是把所有内容复制到 <code>docs/AI_ENGINEERING/</code>，而是：

1. 提取仍被当前源码支持的事实；
2. 将设计意图、历史实验、未来方案与当前实现分开；
3. 给每条知识确定唯一归属；
4. 保留旧文件和 Git 历史作为证据；
5. 不把任何真实 secret、密码、私有 token 或不透明运维值复制进新体系；
6. 对无法验证的内容显式标记为 unknown 或 historical。

---

## 2. 已采用的新真相层级

本轮用户已明确授权建立新的 AI 工程知识体系，接管任务已采用以下层级：

1. 当前源码；
2. <code>docs/AI_ENGINEERING/</code>；
3. <code>.codex/PROJECT_MEMORY.md</code>、<code>ARCHITECTURE_MEMORY.md</code>、<code>DECISION_LOG.md</code>、<code>TASK_HISTORY.md</code>、<code>AI_RULES.md</code>；
4. 原 <code>docs/ENGINEERING_*</code>、<code>TASK_ENTRYPOINTS.md</code> 和结构地图；
5. 专题、历史、上游和 vendor 文档。

约束：

- <code>.codex/</code> 应保存摘要、规则、决定和任务索引，不再复制完整架构正文。
- 原 ENGINEERING 主套件继续作为历史细节；本轮已在其主要入口顶部添加迁移状态和新入口，不删除正文。
- <code>AGENTS.md</code>、<code>CLAUDE.md</code> 应只做工具入口，不自建完整真相层。

---

## 3. 现存文档知识映射

| 现存文档或文档组 | 有效知识 | 新归属 | 迁移状态 | 原文件策略 |
|---|---|---|---|---|
| <code>AGENTS.md</code> | 项目身份、类名速查、关键不可变量、构建纪律 | <code>00_PROJECT_OVERVIEW.md</code>、<code>.codex/AI_RULES.md</code> | 部分提取 | 保留；未来缩减为 Codex 入口 |
| <code>CLAUDE.md</code> | 与 AGENTS 相同的工具入口 | <code>.codex/AI_RULES.md</code> | 不重复提取 | 保留；只保留 Claude Code 差异 |
| <code>docs/ENGINEERING_INDEX.md</code> | 阅读顺序、写作契约、真相优先级 | <code>00_PROJECT_OVERVIEW.md</code>、<code>.codex/AI_RULES.md</code> | 已提取主规则 | 保留并标记 historical entry |
| <code>docs/ENGINEERING_BASELINE.md</code> | 项目身份、顶层架构、主链、风险 | <code>00_PROJECT_OVERVIEW.md</code>、<code>01_ARCHITECTURE.md</code>、<code>03_MODULE_DESIGN.md</code>、<code>10_SECURITY_MODEL.md</code> | 已提取主链与风险 | 保留为历史详细基线 |
| <code>docs/ENGINEERING_ANDROID_RUNTIME.md</code> | Android service/frame/waiting 三状态、投屏授权不变量、黑屏与无视链 | <code>04_ANDROID_PIPELINE.md</code>、<code>09_DEBUG_SYSTEM.md</code>、<code>10_SECURITY_MODEL.md</code> | 已提取当前主链；旧修复日志保留 | 保留为 historical/no-regression evidence |
| <code>docs/TASK_ENTRYPOINTS.md</code> | 跨层入口、关键检索锚点、检查清单 | <code>02_SOURCE_MAP.md</code>、各专项文档、各 CloudSend skill | 已提取当前入口 | 保留旧任务导航 |
| <code>docs/REPO_TRUE_STRUCTURE_MAP.md</code> | 目录职责、平台层和跨层链路 | <code>02_SOURCE_MAP.md</code> | 已提取当前结构 | 保留；不存在路径仍由审计追踪 |
| <code>docs/DOCUMENT_AUDIT.md</code> | 旧可信等级、历史漂移记录 | 本报告、<code>.codex/DECISION_LOG.md</code> | 已提取主要结论 | 保留为上一代审计 |
| <code>docs/SOURCE_TRUTH_AUDIT_2026_05_18.md</code> | 2026-05-18 固定日期身份和构建审计 | 本报告的审计血缘、<code>.codex/DECISION_LOG.md</code> | 仅索引 | 保留，不提升为当前真相 |
| <code>docs/ADB_LADB_INTEGRATION_MEMORY.md</code> | 已落地本地 ADB 模块、无线调试自动化、未来 PC ADB 边界 | <code>03_MODULE_DESIGN.md</code>、<code>04_ANDROID_PIPELINE.md</code>、<code>11_ROADMAP.md</code> | 已拆分当前实现与未来边界 | 保留；外部研究仍需补 revision |
| ZEGO architecture/integration | ZEGO 媒体边界、控制协议、状态机、Flutter/Rust 锚点 | <code>03_MODULE_DESIGN.md</code>、<code>06_NETWORK_PROTOCOL.md</code>、<code>07_API_SYSTEM.md</code>、<code>10_SECURITY_MODEL.md</code> | 已提取当前边界与风险 | 保留专题历史和运维细节 |
| ZEGO Token deployment | 服务接口、systemd/Go 部署流程、验收步骤 | <code>07_API_SYSTEM.md</code>、<code>08_BUILD_SYSTEM.md</code>、私有运维 runbook | 只提取无敏感结构 | 保留；不得把真实凭据复制到新体系 |
| <code>PC-Build.md</code> | Windows Server 工具链背景和故障经验 | <code>08_BUILD_SYSTEM.md</code>、<code>09_DEBUG_SYSTEM.md</code> | 已提取 canonical path 与验证边界 | 保留上游背景；不把旧命令当现行入口 |
| <code>docs/CHANGELOG.md</code> | 2026-04 至 2026-06 的变更意图 | <code>.codex/DECISION_LOG.md</code>、<code>.codex/TASK_HISTORY.md</code> | 已筛选重大阶段；逐项产品日志仍待 owner | 保留；产品变更与 AI 会话元数据分离 |
| <code>terminal.md</code> | Terminal 设计意图、消息概念和未完成持久化方案 | <code>03_MODULE_DESIGN.md</code>、<code>06_NETWORK_PROTOCOL.md</code>、<code>11_ROADMAP.md</code> | 已提取当前临时会话与未来持久化边界 | 保留为历史设计，不复制过时 service id |
| <code>README.md</code>、<code>docs/README-ZH.md</code> | RustDesk 来源、上游项目背景、通用依赖 | <code>00_PROJECT_OVERVIEW.md</code> 的来源说明 | 只提取来源 | 保留；继续标注 upstream background |
| CONTRIBUTING/SECURITY/CODE_OF_CONDUCT | 上游社区治理模板 | <code>.codex/AI_RULES.md</code> 不承载；未来独立治理文档 | 不迁移当前联系人 | 保留，等待 CloudSend 所有者提供真实治理信息 |
| <code>docs/DEVCONTAINER.md</code> | 历史 devcontainer 构建意图 | <code>08_BUILD_SYSTEM.md</code> 的“未验证/当前缺失”项 | 只记录缺失 | 保留，不执行其中命令 |
| <code>flutter/README.md</code> | Flutter 模板背景 | 无 | 不迁移 | 保留模板遗留，后续可加状态说明 |
| <code>libs/clipboard/**/README.md</code> | Clipboard 子库协议和 macOS 实现 | <code>03_MODULE_DESIGN.md</code> 中的子系统摘要 | 摘要提取 | 原文件继续作为模块真相 |
| <code>libs/enigo/README.md</code> | 输入模拟库来源 | <code>03_MODULE_DESIGN.md</code> 的输入系统来源 | 摘要提取 | 原文件保留 |
| <code>libs/scrap/**/README.md</code> | capture 库来源、帧格式和 Wayland 背景 | <code>03_MODULE_DESIGN.md</code>、<code>05_WINDOWS_PIPELINE.md</code> | 摘要提取 | 原文件保留 |
| <code>libs/virtual_display/**/README.md</code> | 虚拟显示驱动来源和平台背景 | <code>05_WINDOWS_PIPELINE.md</code> | 摘要提取 | 原文件保留 |
| <code>res/msi/README.md</code> | MSI 子工程构建背景 | <code>08_BUILD_SYSTEM.md</code> | 待确认是否仍发布 | 原文件保留 |
| <code>src/lang/README.md</code> | 翻译模板格式 | <code>02_SOURCE_MAP.md</code> 的局部入口 | 摘要提取 | 原文件继续作为局部说明 |
| <code>.claude/commands/reflection.md</code> | Claude 指令反思流程 | <code>.codex/AI_RULES.md</code> 中的工具差异索引 | 不复制正文 | 原文件保留 |
| vendored enigo issue templates | 上游 crate issue 模板 | 无 | 不迁移 | 原文件保留，明确不代表 CloudSend 流程 |

---

## 4. 2026-04-14 已删除项目文档

提交 <code>c75932a</code> 删除 7 份旧项目文档，并创建第一代 ENGINEERING 主套件。本次没有恢复这些文件。

| 历史文件 | 历史内容 | 当前判断 | 新归属 | 保留策略 |
|---|---|---|---|---|
| <code>DOCS.md</code> | DaXianMeeting 大型全量手册：身份、结构、JNI、三模式捕获、命令、像素、账号、构建 | 大量身份和实现已过时，部分架构知识已进入现有主套件 | <code>00</code>、<code>01</code>、<code>02</code>、<code>03</code>、<code>04</code>、<code>08</code> | 不整份恢复；只从 Git 历史提取仍有源码证据的事实 |
| <code>docs/ANDROID_KEEPALIVE_LOG_ANALYSIS.md</code> | ColorOS/Oplus 实机日志对比、前台通知与悬浮窗保活观察 | 有独特实验价值，但原始日志目录已不存在，不能复现 | <code>04_ANDROID_PIPELINE.md</code>、<code>09_DEBUG_SYSTEM.md</code> 的 historical experiment | 不恢复；保留 commit/path 和“不可复现”状态 |
| <code>docs/ANDROID_STATE_MACHINE.md</code> | 锁屏、断网、关共享、开共享、waiting 状态机 | 已被现有 runtime 文档覆盖；旧版包含现已禁止的 waiting 自动开无视策略 | <code>04_ANDROID_PIPELINE.md</code> 只接收与当前源码一致的不变量 | 不恢复；不得迁入旧自动 fallback 逻辑 |
| 旧 <code>docs/CHANGELOG.md</code> | 2026-04 Android hotfix 记录 | 历史有价值，部分状态已被后续回退或覆盖 | <code>.codex/TASK_HISTORY.md</code>、<code>DECISION_LOG.md</code> | 不恢复原文件；通过 Git 历史引用 |
| <code>docs/KNOWN_BUGS.md</code> | 早期已修复和待评估问题 | 部分已修复，部分仍存在，部分外部要求需重新确认 | <code>10_SECURITY_MODEL.md</code>、<code>11_ROADMAP.md</code> | 不恢复；逐项重新核验后登记 |
| <code>docs/PROJECT_INDEX.md</code> | 旧阅读顺序、可信层级、高风险入口 | 已被 ENGINEERING_INDEX/TASK_ENTRYPOINTS 取代，且含旧 auto-ignore 规则 | <code>00_PROJECT_OVERVIEW.md</code>、<code>.codex/AI_RULES.md</code> | 不恢复 |
| <code>docs/PROJECT_MEMORY.md</code> | 旧项目身份、文件地图、JNI、捕获、terminal、plugin、build memory | 大部分已被现有主套件覆盖；旧身份和状态不得直接迁入 | <code>00</code>、<code>01</code>、<code>03</code>、<code>.codex/PROJECT_MEMORY.md</code> | 不恢复；新 memory 必须短小并指向 canonical docs |

### 4.1 已确认仍值得重新登记的旧问题

- <code>libs/scrap/src/android/pkg2230.rs</code> 和兼容 <code>ffi.rs</code> 仍有多组 <code>static mut</code> 像素/JNI 全局状态。
- 当前 Android <code>targetSdkVersion</code> 仍为 33；是否升级、目标环境要求和兼容风险需要在正式环境重新验证，不能直接继承旧文档的外部政策结论。
- <code>ffi.rs</code> 与 <code>pkg2230.rs</code> 仍是双份相似实现，但主路由只有 <code>pkg2230</code>；未来应建立差异检查，而不是盲目复制。

### 4.2 明确不得回迁的旧事实

- DaxianMeeting/DaXianDesk 作为当前产品名。
- <code>com.daxian.dev</code> 作为当前 Android package。
- waiting-for-first-frame 自动发送“开无视”或 screenshot fallback。
- 把 Android 服务存活等价为已有首帧。
- 把 <code>ffi.rs</code> 当作当前 Android JNI 主路由。
- 旧 Windows DLL、Android SO 和 deep-link 名称。
- <code>tmp_</code>/<code>persist_</code> 作为当前 terminal service id。

---

## 5. 2026-05-18 已删除的 55 份上游翻译文档

提交 <code>483a0a0</code> 删除 55 份翻译 Markdown。本次没有恢复这些文件。

这些文件主要是 RustDesk 上游社区内容，不含已知 CloudSend 专属工程真相。迁移策略是记录审计血缘，不将其复制到 AI 工程体系。

### 5.1 CODE_OF_CONDUCT 翻译：6

- <code>docs/CODE_OF_CONDUCT-JP.md</code>
- <code>docs/CODE_OF_CONDUCT-NL.md</code>
- <code>docs/CODE_OF_CONDUCT-NO.md</code>
- <code>docs/CODE_OF_CONDUCT-PL.md</code>
- <code>docs/CODE_OF_CONDUCT-RU.md</code>
- <code>docs/CODE_OF_CONDUCT-TR.md</code>

### 5.2 CONTRIBUTING 翻译：10

- <code>docs/CONTRIBUTING-DE.md</code>
- <code>docs/CONTRIBUTING-ID.md</code>
- <code>docs/CONTRIBUTING-IT.md</code>
- <code>docs/CONTRIBUTING-JP.md</code>
- <code>docs/CONTRIBUTING-KR.md</code>
- <code>docs/CONTRIBUTING-NL.md</code>
- <code>docs/CONTRIBUTING-NO.md</code>
- <code>docs/CONTRIBUTING-PL.md</code>
- <code>docs/CONTRIBUTING-RU.md</code>
- <code>docs/CONTRIBUTING-TR.md</code>

### 5.3 DEVCONTAINER 翻译：7

- <code>docs/DEVCONTAINER-DE.md</code>
- <code>docs/DEVCONTAINER-IT.md</code>
- <code>docs/DEVCONTAINER-JP.md</code>
- <code>docs/DEVCONTAINER-NL.md</code>
- <code>docs/DEVCONTAINER-NO.md</code>
- <code>docs/DEVCONTAINER-PL.md</code>
- <code>docs/DEVCONTAINER-TR.md</code>

### 5.4 README 翻译：24

- <code>docs/README-AR.md</code>
- <code>docs/README-CS.md</code>
- <code>docs/README-DA.md</code>
- <code>docs/README-DE.md</code>
- <code>docs/README-EO.md</code>
- <code>docs/README-ES.md</code>
- <code>docs/README-FA.md</code>
- <code>docs/README-FI.md</code>
- <code>docs/README-FR.md</code>
- <code>docs/README-GR.md</code>
- <code>docs/README-HU.md</code>
- <code>docs/README-ID.md</code>
- <code>docs/README-IT.md</code>
- <code>docs/README-JP.md</code>
- <code>docs/README-KR.md</code>
- <code>docs/README-ML.md</code>
- <code>docs/README-NL.md</code>
- <code>docs/README-NO.md</code>
- <code>docs/README-PL.md</code>
- <code>docs/README-PTBR.md</code>
- <code>docs/README-RU.md</code>
- <code>docs/README-TR.md</code>
- <code>docs/README-UA.md</code>
- <code>docs/README-VN.md</code>

### 5.5 SECURITY 翻译：8

- <code>docs/SECURITY-DE.md</code>
- <code>docs/SECURITY-IT.md</code>
- <code>docs/SECURITY-JP.md</code>
- <code>docs/SECURITY-KR.md</code>
- <code>docs/SECURITY-NL.md</code>
- <code>docs/SECURITY-NO.md</code>
- <code>docs/SECURITY-PL.md</code>
- <code>docs/SECURITY-TR.md</code>

### 5.6 处理结论

- 不恢复。
- 不把上游联系人迁入 CloudSend 安全或治理模型。
- 若未来需要多语言产品文档，应从 CloudSend 自有 canonical policy 重新翻译，而不是恢复旧 RustDesk 版本。
- Git 历史仍可通过 <code>96a9c7c:&lt;path&gt;</code> 查看原内容；是否恢复必须由用户明确决定。

---

## 6. 敏感信息迁移规则

以下内容不得迁入 <code>docs/AI_ENGINEERING/</code> 或 <code>.codex/</code>：

- 字面 ZEGO server secret；
- Token 服务 API key 的具体值；
- 服务器密码、面板密码、私有 token；
- <code>.info</code> 中未确认语义的不透明内容；
- 可直接登录服务器或调用私有服务的运维凭据。

只允许迁移：

- 变量名；
- 配置来源；
- 所需权限；
- 轮换方式；
- 失败模式；
- secret owner；
- 验证清单。

当前敏感路径清单见 <code>DOCUMENT_AUDIT_REPORT.md</code>，本报告不重复任何值。

---

## 7. 尚未迁移或无法确认的事实

| 未迁事实 | 原因 | 建议落点 |
|---|---|---|
| 2026-04-13 之前的 RustDesk/DaXian 演进、原始 fork commit | 当前 Git 根提交已是完整快照，无更早父提交 | <code>00_PROJECT_OVERVIEW.md</code> 标记 unknown；等待上游仓库或所有者资料 |
| ColorOS/Oplus 锁屏保活对比的原始日志 | 历史日志目录当前不存在 | <code>09_DEBUG_SYSTEM.md</code> 仅登记 historical observation |
| <code>ADB-CODE/</code>、<code>LADB/</code> 的 commit、license 和获取方式 | 两目录被忽略，文档未写 revision | <code>03_MODULE_DESIGN.md</code> 的 external provenance |
| Android target SDK 升级决定 | 需要正式 Android 构建、设备和商店政策验证 | <code>08_BUILD_SYSTEM.md</code>、<code>11_ROADMAP.md</code> |
| <code>.info</code> 的所有者和含义 | 为避免泄露，本轮不展示内容；现有文档无定义 | <code>10_SECURITY_MODEL.md</code> 的 asset inventory |
| tracked ZEGO/Token 凭据是否仍有效 | 需要凭据所有者和服务器侧确认 | <code>10_SECURITY_MODEL.md</code>，先轮换后处置 |
| Windows/macOS/Linux 实际发布范围 | 本地文档保留上游路径，但没有可靠发行矩阵和 tag | <code>00_PROJECT_OVERVIEW.md</code>、<code>08_BUILD_SYSTEM.md</code> |
| GitHub issue、PR、Release、事故和服务器变更历史 | 本地 Git 不包含完整外部平台数据 | <code>.codex/TASK_HISTORY.md</code>，等待外部资料 |
| 构建命令是否可执行 | 本阶段禁止编译且当前机器非正式环境 | <code>08_BUILD_SYSTEM.md</code> 的《编译验证需求》 |
| TODO/FIXME 的真实优先级 | 91 条标记没有统一 owner、issue 或验证条件 | <code>11_ROADMAP.md</code>，需逐项 triage |

---

## 8. 非破坏性保留策略

### 8.1 现存文件

- 原路径保留。
- 新体系已完成；本轮已在 `AGENTS.md`、`CLAUDE.md`、`README.md`、旧索引、基线、审计和任务入口顶部增加状态标识与新入口。其余专题文档保留原貌，由本报告提供映射。
- 旧文档不再新增与新 canonical docs 重复的大段事实。
- 专题文档继续服务专题，但不得覆盖全仓真相。

### 8.2 历史删除文件

- 不自动恢复到工作树。
- 通过 commit hash 和历史 path 引用。
- 若某条独特知识需要恢复，先提取到新文档并标注来源、日期和验证状态。
- 恢复整个文件、创建 archive 目录或改写 Git 历史都需要用户明确确认。

### 8.3 重复文档

- 翻译型重复保留语言价值。
- 工具入口型重复只保留工具差异。
- 架构事实只写入一个 canonical 位置，其他文件链接过去。

### 8.4 变更记录

- 产品行为变化进入产品 changelog。
- 架构选择及其原因进入 <code>DECISION_LOG.md</code>。
- AI 或人工执行过的任务进入 <code>TASK_HISTORY.md</code>。
- “未执行编译/提交”等会话事实不再写入产品 changelog。

---

## 9. 迁移验收清单

- [x] 已盘点当前 39 个 tracked Markdown。
- [x] 已记录 2026-04-14 删除的 7 个项目文档。
- [x] 已记录 2026-05-18 删除的 55 个翻译文档。
- [x] 已给出当前文档到新体系的知识映射。
- [x] 已列出不得回迁的旧事实。
- [x] 已列出未迁和无法确认事实。
- [x] 未复制任何具体 secret 值。
- [x] 未删除现存文档。
- [x] 未恢复历史删除文档。
- [x] 未修改业务代码。
- [x] 未编译、测试、部署或执行 Git 写操作。
- [x] 新 AI 工程文档已完成，主要旧入口已更新状态和真相层级。
- [ ] 待所有者确认敏感资产并完成凭据轮换。
- [ ] 待正式环境执行构建和平台验证。

---

## 10. 结论

旧文档中可复用的资产主要是：

- 已经源码验证的 Android 运行时不变量；
- 跨层源码入口；
- ADB/LADB 与 ZEGO 的专题设计；
- Windows 构建环境经验；
- RustDesk 来源和兼容性背景；
- 2026-04 至 2026-07 的二开演进记录。

不能直接迁移的内容主要是：

- 已过时的 Daxian/RustDesk 品牌和构建入口；
- waiting 自动切无视等已被禁止的旧策略；
- 未落地的 terminal 持久化设计；
- 未固定 revision 的外部研究结论；
- 上游治理联系人；
- 任何真实运维凭据。

本轮采用“提取、映射、保留、标记”的方式完成第一轮知识迁移与迁移报告，没有采用删除、整份复制或历史恢复。尚未逐份重写旧专题正文；其剩余漂移由 `DOCUMENT_AUDIT_REPORT.md` 持续追踪。
