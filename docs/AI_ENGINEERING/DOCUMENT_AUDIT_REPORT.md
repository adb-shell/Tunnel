# CloudSend 文档资产审计报告 / Document Audit Report

审计日期：2026-07-12  
源码基线：Git <code>HEAD 77062b4</code>  
审计方式：只读源码检索、Markdown 清单、路径核对、Git <code>log/show/diff</code>  
审计边界：未修改业务代码，未编译、测试、部署、上传，也未执行任何 Git 写操作。

> 本报告是 2026-07-12 的固定日期审计结果。源码仍是最终真相。
> A/B/C/D 是“当前可用边界”而不是删除建议；所有现存文件均应保留，任何删除、恢复或历史改写必须另行获得明确授权。

---

## 1. 资产范围

本次通过 <code>git ls-files</code> 盘点到：

- Git tracked Markdown：39 个
- 未跟踪 Markdown：0 个
- 总行数：9,813
- 总大小：约 472 KB
- 根目录 Markdown：5 个
- <code>docs/</code>：19 个
- 子系统、依赖与资源 Markdown：14 个
- 隐藏目录 Markdown：1 个

本次还对 481 个主要源码和脚本文件的注释进行了高信号扫描，用于识别 TODO/FIXME、品牌残留、上游来源说明和明显过时注释。

---

## 2. 审计等级

| 等级 | 含义 | 使用边界 |
|---|---|---|
| A | 真实有效 | 在文档声明的限定模块或用途内可直接使用；实现细节仍以源码为准 |
| B | 需要更新 | 主体方向有效，但存在时间戳、局部路径、内容混层、安全边界或可复现性问题 |
| C | 已经过时或仅供历史参考 | 不能直接驱动当前实现；只能用于上游背景、历史意图或迁移追溯 |
| D | 重复 | 与另一份文档高度重复；D 不等于应删除，翻译和工具专用入口仍可能有保留价值 |

本轮结果：

| A | B | C | D | 合计 |
|---:|---:|---:|---:|---:|
| 5 | 18 | 12 | 4 | 39 |

---

## 3. 全部 39 个 tracked Markdown 分类

| # | 文件 | 等级 | 审计结论 |
|---:|---|:---:|---|
| 1 | <code>.claude/commands/reflection.md</code> | B | 通用 Claude 指令反思命令可用，但不是 CloudSend 工程真相；引用的可选 settings 文件当前不存在 |
| 2 | <code>AGENTS.md</code> | B | 高价值入口大体准确，但“最后对齐 2026-06-09”已落后于 6 月下旬和 7 月源码；应只保留入口与不可变量摘要 |
| 3 | <code>CLAUDE.md</code> | D | 与 <code>AGENTS.md</code> 约 97% 的长文本行完全相同，同时缺少若干屏幕共享授权不变量；属于重复且略落后的工具入口 |
| 4 | <code>PC-Build.md</code> | B | 顶部 CloudSend 覆盖说明有效，正文主要是上游 RustDesk Windows Server 环境教程；只能作为环境背景，当前入口仍是 <code>new-build.cmd</code> |
| 5 | <code>README.md</code> | C | 顶部 CloudSend 身份覆盖有效，主体仍是上游 RustDesk 下载、社区、Sciter 和构建说明；不能作为当前工程入口 |
| 6 | <code>docs/ADB_LADB_INTEGRATION_MEMORY.md</code> | B | 当前本地 ADB 实现和未来设计区分较清楚，但混合实现记忆、外部项目评审和未来方案；外部 <code>ADB-CODE/</code>、<code>LADB/</code> 未跟踪且无 revision |
| 7 | <code>docs/CHANGELOG.md</code> | C | 有历史价值，但最新条目停在 2026-06-13，缺失后续重大变更，并混入大量“Codex 未执行某操作”的会话元数据 |
| 8 | <code>docs/CODE_OF_CONDUCT-ZH.md</code> | D | 英文行为准则的翻译型重复；保留语言价值，但继承上游 RustDesk 联系方式漂移 |
| 9 | <code>docs/CODE_OF_CONDUCT.md</code> | C | 通用行为准则正文仍可参考，但监督联系人属于上游 RustDesk，不代表当前 CloudSend 治理主体 |
| 10 | <code>docs/CONTRIBUTING-ZH.md</code> | D | 英文贡献规范的翻译型重复；保留语言价值，但继承上游仓库、邮箱和社区入口漂移 |
| 11 | <code>docs/CONTRIBUTING.md</code> | C | 面向 RustDesk 上游 PR、issue、邮箱和 Discord，不是当前 CloudSend 商业项目贡献流程 |
| 12 | <code>docs/DEVCONTAINER.md</code> | C | 所有命令依赖 <code>.devcontainer/build.sh</code>，但当前仓库没有 <code>.devcontainer/</code> |
| 13 | <code>docs/DOCUMENT_AUDIT.md</code> | B | 既有审计提供了重要可信边界，但时间戳仍是 2026-06-09、没有 D 类，并将自身继续列为 A |
| 14 | <code>docs/ENGINEERING_ANDROID_RUNTIME.md</code> | B | Android 状态机主体与 7 月初源码高度一致，但文件头仍声称最后核验于 2026-06-09；后续应拆出稳定不变量与历史修复日志 |
| 15 | <code>docs/ENGINEERING_BASELINE.md</code> | B | 覆盖面完整、源码锚点丰富，但时间戳过时，且关于 <code>docs/CHANGELOG.md</code> 的“已废弃”表述与现状直接冲突 |
| 16 | <code>docs/ENGINEERING_INDEX.md</code> | B | 阅读顺序和写作契约仍有价值，但“Current Source Truth (2026-06-09)”及禁止新 memory 文档的规则需要与本次新体系同步 |
| 17 | <code>docs/README-ZH.md</code> | D | <code>README.md</code> 的中文翻译型重复；CloudSend 覆盖说明有效，主体仍是上游 RustDesk 背景 |
| 18 | <code>docs/REPO_TRUE_STRUCTURE_MAP.md</code> | B | 大部分结构锚点有效，但最后核验仍写 2026-06-03，并列出不存在的 <code>src/version.rs</code>；未完整体现后续 Dev 自动点选链 |
| 19 | <code>docs/SECURITY.md</code> | C | 将漏洞报告发送给上游 RustDesk，不能作为 CloudSend 安全响应政策 |
| 20 | <code>docs/SOURCE_TRUTH_AUDIT_2026_05_18.md</code> | C | 明确是固定日期审计且已被后续工程主套件覆盖；只用于审计血缘 |
| 21 | <code>docs/TASK_ENTRYPOINTS.md</code> | B | 跨层任务入口总体有效并包含后续 Dev 链路，但文件头仍写 2026-06-09，且部分章节已膨胀为设计说明 |
| 22 | <code>docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md</code> | B | 当前部署流程有操作价值，但保存字面服务端凭据类型，不应作为普通 Git-tracked 工程知识继续扩散 |
| 23 | <code>docs/ZEGO_VOICE_CALL_ARCHITECTURE.md</code> | B | 主要源码符号仍存在，媒体/控制边界有效；“最后同步源码 2026-06-07”与后续 6 月、7 月更新不符，并重复运维 endpoint |
| 24 | <code>docs/ZEGO_VOICE_CALL_INTEGRATION.md</code> | B | 协议和运行时锚点仍有效，但同步日期过时，且架构、实现、Token 运维与 UI 规则存在混层 |
| 25 | <code>flutter/README.md</code> | C | 未定制的 Flutter 模板“新项目”说明，不能描述当前 Flutter UI 架构 |
| 26 | <code>flutter/ios/Runner/Assets.xcassets/LaunchImage.imageset/README.md</code> | A | iOS LaunchImage 资源目录的局部说明有效 |
| 27 | <code>libs/clipboard/README.md</code> | A | clipboard 协议、Windows/FUSE 模型和 <code>unix-file-copy-paste</code> feature 与当前源码结构相符；仅限该子库 |
| 28 | <code>libs/clipboard/src/platform/unix/macos/README.md</code> | A | macOS pasteboard 临时文件前缀与当前源码一致；历史 RustDesk 前缀在这里是当前兼容实现而非单纯文档漂移 |
| 29 | <code>libs/enigo/.github/ISSUE_TEMPLATE/bug_report.md</code> | C | vendored enigo 项目的上游 issue 模板，不属于 CloudSend issue 流程 |
| 30 | <code>libs/enigo/.github/ISSUE_TEMPLATE/feature_request.md</code> | C | vendored enigo 项目的上游 issue 模板，不属于 CloudSend 产品需求流程 |
| 31 | <code>libs/enigo/.github/ISSUE_TEMPLATE/question.md</code> | C | vendored enigo 项目的上游问答模板，不属于 CloudSend 支持入口 |
| 32 | <code>libs/enigo/README.md</code> | B | 可说明输入模拟库来源和基本能力，但内容停留在上游支持矩阵，不能覆盖 CloudSend 输入注入链 |
| 33 | <code>libs/scrap/README.md</code> | B | 上游 screen capture API 背景仍有价值，但未覆盖 CloudSend Android JNI/raw frame 和平台定制 |
| 34 | <code>libs/scrap/src/wayland/README.md</code> | B | Wayland 来源和依赖背景有效，但测试平台版本陈旧，需由正式环境重新确认 |
| 35 | <code>libs/virtual_display/README.md</code> | A | 仅作为指向 <code>dylib/README.md</code> 的有效局部索引 |
| 36 | <code>libs/virtual_display/dylib/README.md</code> | B | 虚拟显示来源和 Win10 驱动背景有效，但支持矩阵和测试版本不足以代表当前 Windows 维护面 |
| 37 | <code>res/msi/README.md</code> | B | MSI 子工程的局部上游说明可参考，但不是 CloudSend 当前推荐发布流程，TODO 尚未闭环 |
| 38 | <code>src/lang/README.md</code> | A | 语言模板位置和键值格式说明与当前目录相符；只限翻译子目录 |
| 39 | <code>terminal.md</code> | C | 已自报 <code>tmp_</code>/<code>persist_</code> service id 漂移；持久化恢复大量属于设计意图，当前真相应回到 Rust/Flutter 源码 |

---

## 4. 已确认的主要漂移

### 4.1 文档时间戳与 Git 事实不一致

| 文件 | 文件自报日期 | Git 最后变更 |
|---|---|---|
| <code>AGENTS.md</code> | 2026-06-09 | 2026-06-24 |
| <code>CLAUDE.md</code> | 2026-06-09 | 2026-06-24 |
| <code>docs/DOCUMENT_AUDIT.md</code> | 2026-06-09 | 2026-07-10 |
| <code>docs/ENGINEERING_INDEX.md</code> | 2026-06-09 | 2026-07-01 |
| <code>docs/ENGINEERING_BASELINE.md</code> | 2026-06-09 | 2026-07-10 |
| <code>docs/ENGINEERING_ANDROID_RUNTIME.md</code> | 2026-06-09 | 2026-07-01 |
| <code>docs/TASK_ENTRYPOINTS.md</code> | 2026-06-09 | 2026-07-01 |
| <code>docs/REPO_TRUE_STRUCTURE_MAP.md</code> | 2026-06-03 | 2026-07-01 |
| 两份 ZEGO 架构/集成文档 | 2026-06-07 | 2026-07-10 |

时间戳漂移不代表正文全部失效，但会让 AI 和人工维护者错误判断审计覆盖范围。

### 4.2 直接内部冲突

- <code>docs/ENGINEERING_BASELINE.md:16</code> 要求“不恢复已废弃的 docs/CHANGELOG.md”。
- 当前 <code>docs/CHANGELOG.md</code> 存在并持续维护。
- <code>docs/ENGINEERING_INDEX.md:219</code> 和 <code>docs/DOCUMENT_AUDIT.md:337</code> 又把它定义为保留的历史记录。

应统一为：“CHANGELOG 是历史记录，不是当前实现真相；是否继续维护由文档策略决定”，不能同时称其为已废弃和现存同步对象。

### 4.3 路径与入口漂移

- <code>docs/REPO_TRUE_STRUCTURE_MAP.md:138</code> 的 <code>src/version.rs</code> 不存在。
- <code>docs/DEVCONTAINER.md</code> 引用的 <code>.devcontainer/build.sh</code> 和目录不存在。
- 全仓 Markdown 标准本地链接检查未发现断链；问题主要出现在代码格式路径和命令入口，而非 Markdown link。

### 4.4 变更日志不完整

- <code>docs/CHANGELOG.md</code> 最新条目日期为 2026-06-13。
- 仓库之后仍发生 Dev 自动点选、黑屏帧恢复、Actions 编排、Token 部署和多次服务器迁移。
- 文件中至少 21 处记录“Codex 未编译/未提交”等会话事实，建议迁入任务历史，不与产品变更混写。

---

## 5. 重复与竞争性真相层

### 5.1 明确重复

- <code>AGENTS.md</code> 与 <code>CLAUDE.md</code> 约 97% 的长文本唯一行完全相同。
- <code>CLAUDE.md</code> 缺少 <code>AGENTS.md:126-131,137</code> 中若干 <code>MediaProjection</code> 授权、重复启动、settle window 和 <code>captureStarting</code> 不变量。
- README、CONTRIBUTING、CODE_OF_CONDUCT 的中英文对属于翻译型重复，保留语言价值，但不应各自演化工程事实。

### 5.2 结构性重复

- <code>ENGINEERING_BASELINE</code>、<code>ENGINEERING_ANDROID_RUNTIME</code>、<code>TASK_ENTRYPOINTS</code> 和 <code>AGENTS/CLAUDE</code> 多次复制 Android 首帧、重连、授权提示和 relay-only 不变量。
- <code>ENGINEERING_BASELINE</code> 与 <code>TASK_ENTRYPOINTS</code> 至少有 66 条长度不低于 20 字的完全相同行。
- <code>TASK_ENTRYPOINTS</code> 已从“入口地图”膨胀为部分实现文档，增加同步成本。

### 5.3 新体系的规则冲突

现有文档禁止创建竞争性 memory docs：

- <code>docs/ENGINEERING_INDEX.md:71,111,343</code>
- <code>docs/DOCUMENT_AUDIT.md:100</code>
- <code>docs/ENGINEERING_BASELINE.md:916</code>

本轮用户已明确授权创建 <code>docs/AI_ENGINEERING/</code> 和 <code>.codex/</code>。因此新体系完成后必须显式更新真相层级，不能让新旧两套文档同时自称 canonical。

---

## 6. 敏感信息审计

本节只记录敏感信息类型与路径，不记录、引用或转写任何具体值。

### 6.1 严重：服务端凭据进入 tracked 文档和脚本

- <code>docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md:33,35,259</code>
- <code>scripts/deploy_zego_token_service.sh:12,14,112,114</code>

涉及类型：

- ZEGO server secret
- Token 服务 API key

相关内容已经进入 Git 历史，最早可追溯至 2026-05-31/2026-06-01。未来即使从当前文件移除，也必须先轮换真实凭据；Git 历史处置需要单独授权，当前不得自动执行。

### 6.2 需要所有者确认的 tracked 不透明文件

- <code>.info</code>

该文件只有两行，两行均呈高熵不透明文本特征，并多次随“更换服务器IP”提交变化。未在本报告中展示内容；需由所有者确认它是 endpoint、账号、密码、token 还是其他运维资产。

### 6.3 客户端或构建内置凭据类型

- <code>src/client/helper.rs:12,88</code>：Token 服务客户端 key 类型
- <code>libs/hbb_common/src/config.rs:2527</code>：构建内置连接密码配置类型

这些值是否被定义为公开客户端标识、共享秘密或临时兼容值，需要在 <code>10_SECURITY_MODEL.md</code> 中明确威胁模型、轮换方式和泄露后果。

### 6.4 运维元数据扩散

Token endpoint、rendezvous/relay endpoint 和服务器地址同时出现在多个源码、文档和运维文件中，并在 2026-06-27、06-29、07-10 多次整体迁移。应建立单一配置来源和变更清单，避免漏改。

---

## 7. 代码注释与注释型技术债

高信号扫描结果：

- 近似注释行：12,586
- TODO/FIXME：91 行
  - <code>src/</code>：26
  - <code>libs/</code>：27
  - <code>flutter/</code>：34
  - 其他：4
- 大小写不敏感的 RustDesk 注释：181 行
- CloudSend 注释：20 行

典型问题：

- <code>flutter/android/app/build.gradle:68</code> 仍提示设置唯一 application ID，但下一行已经是当前 package。
- 同文件 <code>:87-89</code> 仍称 release 使用 debug key，但实际指定了 release signing config。
- <code>DFm8Y8iMScvB2YDw.kt:1240-1243</code> 的 <code>useVP9</code> 分支只有裸 TODO 并返回空 surface。
- <code>flutter/lib/common.dart:2497,2668-2670</code> 的注释仍使用旧 URI scheme 示例，实际代码依赖动态 prefix。
- <code>src/server/input_service.rs:572</code> 保留输入竞态 TODO。
- <code>src/privacy_mode/win_virtual_display.rs:421</code> 记录快速拔插虚拟显示器可能导致服务端崩溃。
- <code>flutter/lib/web/bridge.dart</code> 有多处未实现 TODO。

RustDesk 注释不能批量删除或改名。它们包含四类不同含义：

1. 上游 issue、commit 和算法来源；
2. 驱动、协议、临时目录等真实兼容标识；
3. 仍需迁移的旧品牌示例；
4. ZEGO 隔离等“不得恢复旧路径”的有效 guardrail。

后续 TODO 建议统一包含责任模块、风险、完成条件和验证方式。

---

## 8. Git 历史证据

当前本地仓库：

- 非 shallow repository
- 共 59 个提交
- 无 tag
- 根提交：<code>96a9c7c</code>，2026-04-13，主题为 DaXianDesk
- 文档相关提交：46
- 同时修改文档与代码的提交：35

主要演进：

| 时间 | 提交 | 主要内容 |
|---|---|---|
| 2026-04-13 | <code>96a9c7c</code> | DaXianDesk/上游代码一次性导入 |
| 2026-04-14 | <code>c75932a</code> | 旧项目文档重组为 ENGINEERING 主套件 |
| 2026-04-16—04-22 | <code>f740129</code> 至 <code>9eac836</code> | 黑屏、防触、状态监测、双通道、无障碍守卫 |
| 2026-05-05 | <code>df6ede7</code> | CloudSend package、品牌、构建与 Android 路径迁移 |
| 2026-05-13 | <code>6ff35b3</code>、<code>495efd6</code> | Windows 新环境构建、云计划显示名与 5.2.1 |
| 2026-05-20—05-31 | 多提交 | Android 本地 ADB/LADB、mDNS、无线调试自动化 |
| 2026-05-31—06-07 | 多提交 | ZEGO 替换原语音媒体、Token 服务、开发者登录旁路 |
| 2026-06-09—06-24 | 多提交 | relay-only 重连、核心服务/屏幕共享拆分、首帧和授权稳定 |
| 2026-06-25 | <code>e149a68</code> | Dev 自动点选跨层链路 |
| 2026-06-27—07-01 | 多提交 | 黑屏帧恢复、点选兼容、亮度和提示文字 |
| 2026-07-01 | <code>5927388</code> | ZEGO Token 一键部署脚本 |
| 2026-06-27—07-10 | 多提交 | endpoint 与运维配置频繁迁移 |

由于根提交已经是完整代码快照，当前 Git 无法证明 2026-04-13 之前多年二开历史、上游基线 commit、原作者边界或发行谱系。

---

## 9. 处理优先级

### P0：敏感信息

1. 由所有者确认 tracked 凭据是否仍有效。
2. 先轮换，再讨论文档和脚本模板化。
3. 对 <code>.info</code> 做用途确认和归属登记。
4. Git 历史改写、强推或仓库清理必须单独授权。

### P1：真相层

1. 明确 <code>docs/AI_ENGINEERING/</code> 是否成为新的 canonical engineering docs。
2. 将原 ENGINEERING 主套件标记为 active、transitional 或 historical。
3. 让 <code>AGENTS.md</code>、<code>CLAUDE.md</code> 只保存入口和工具差异。
4. 修复时间戳、<code>src/version.rs</code> 和 CHANGELOG 内部冲突。

### P2：所有权与治理

1. 建立 CloudSend 自有 security contact。
2. 建立真实贡献、issue、发布和事故响应入口。
3. 将上游社区文档统一标注为 inherited/vendor background。

### P3：持续维护

1. 将产品 CHANGELOG、AI 任务历史和重大技术决定分开。
2. 给外部 ADB/LADB 研究记录补 repo URL、revision、license 和获取方式。
3. 建立 Markdown 路径检查、敏感词检查和事实更新时间检查。

---

## 10. 未确认项

- 2026-04-13 之前的真实开发历史和 RustDesk/DaXian 上游基线。
- 外部 <code>ADB-CODE/</code>、<code>LADB/</code> 的准确 revision。
- 外部链接、上游在线文档和版本支持矩阵当前是否有效。
- <code>.info</code> 的准确业务语义。
- tracked 凭据当前是否仍在线有效。
- GitHub issue、PR、Release 和服务器运维历史。
- 构建、DevContainer 和部署命令的实际可执行性；本阶段按要求未执行验证。

---

## 11. 审计结论

### 11.1 本轮审计后的入口处置

本报告的 39 项分类以接管开始时的 `HEAD 77062b4` 为固定基线。项目 owner 已在同一接管任务中明确授权建立新的 AI 工程体系；因此审计完成后仅对 `AGENTS.md`、`CLAUDE.md`、`README.md` 和原工程索引/基线/审计/任务入口的顶部指针做了迁移更新：

- `docs/AI_ENGINEERING/` 已成为当前架构真相入口。
- `.codex/` 只保存薄摘要、决定、任务历史和规则。
- 原 `ENGINEERING_*` 主套件保留为历史细节，没有删除。
- A/B/C/D 分类仍描述初始基线及文档主体质量，不因顶部新增迁移提示而自动升级。

这一处置关闭了第 9 节 P1 的“真相层归属”决策项，但没有自动修正旧文档正文中的全部漂移。

CloudSend 已经形成一套较强的工程文档主套件，并且 35 个提交体现了代码与文档同步意识。但当前主要问题不是“没有文档”，而是：

- 真相层过多且重复；
- 文件自报更新时间失真；
- 历史、入口、设计、任务记录和运维值混写；
- 上游治理文档没有完成所有权迁移；
- 部署凭据进入 Git-tracked 文档和脚本；
- 2026-04-13 之前的历史在本仓库中不可追溯。

新 AI 工程体系应以“单一事实归属、源码锚点、状态标记、无敏感值、可验证迁移”为原则，而不是再复制一套同样庞大的记忆文档。
