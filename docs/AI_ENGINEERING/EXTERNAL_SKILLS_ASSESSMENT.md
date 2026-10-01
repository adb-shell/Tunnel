# CloudSend 外部 Skills 评估 / External Skills Assessment

评估日期：2026-07-12  
当前决定：仅做只读来源评估，不安装、不执行外部脚本。  
适用阶段：CloudSend 项目资产接管与长期 AI 工程治理。

> 本轮通过只读 GitHub source inspection 核对了候选仓库、相关 Skill 路径和主要说明，没有修改远端状态，也没有安装或执行候选内容。该核对是 2026-07-12 的时间快照，不等于完成依赖、脚本、网络行为或许可证的全量供应链审计；正式采用前仍须固定 revision 并重新审核。

---

## 1. 结论摘要

CloudSend 不适合批量安装通用 Skills。项目具有以下强约束：

- 当前环境不是正式编译环境；
- 当前阶段禁止自动执行 build、test 和发布；
- 禁止自动 commit、push、merge、rebase；
- 禁止未经确认创建或切换 worktree/branch；
- 禁止删除源码或文档；
- 禁止上传产物、凭据和项目资料；
- Rust 基线是 1.75、edition 2021；
- Android、Flutter、Rust、Windows 和协议链路包含大量项目专属不变量；
- <code>AGENTS.md</code>、<code>.codex/AI_RULES.md</code> 和 CloudSend 专属 Skills 必须高于外部 Skill。

因此采用策略是：

1. 只选少量与审计、计划、验证和代码评审直接相关的 Skill；
2. 默认把外部 Skill 当作“检查清单和思考框架”，不授权其执行命令；
3. 不接受外部 Skill 自带的自动 worktree、自动测试、自动提交或依赖升级流程；
4. 每个 Skill 必须由 owner 单独批准、固定 revision、审核内容后再安装；
5. 任何冲突以用户当前指令、项目 <code>AI_RULES</code> 和源码事实为准。

---

## 2. 评估标准

| 维度 | 评估问题 |
|---|---|
| 项目相关性 | 是否直接帮助 Rust/Flutter/Android/网络/API/安全/发布维护 |
| 版本兼容性 | 是否假设比 Rust 1.75、edition 2021 或当前 Flutter/Gradle 更高的运行时 |
| 权限边界 | 是否默认执行 build、test、git、worktree、上传、安装或删除 |
| 可审计性 | Skill 指令、脚本、hooks、MCP、网络访问和输出是否可被人工审核 |
| 最小采用 | 能否只使用一个小子集，而不是引入完整工作流 |
| 结果复用 | 输出能否进入 CloudSend 的审计报告、Decision Log、Roadmap 或编译验证需求 |
| 维护成本 | 上游更新是否可能悄然改变命令、工具依赖或权限行为 |

采用等级：

| 等级 | 含义 |
|---|---|
| 推荐 | 与当前项目风险高度匹配，但仍需 owner 批准 |
| 按需 | 只在特定任务或正式环境使用 |
| 暂缓 | 当前阶段缺少环境、产物或授权 |
| 不采用 | 完整工作流与项目规则冲突，不应安装或启用 |

---

## 3. 总体评估矩阵

| 来源 | 建议子集 | 主要用途 | 主要冲突 | 当前决定 |
|---|---|---|---|---|
| <code>obra/superpowers</code> | systematic-debugging、verification-before-completion、writing-plans、review | 调试纪律、完成证据、计划和评审 | 完整工作流可能自动建 worktree、运行测试、驱动提交；与当前权限和环境冲突 | 只借鉴四个方法，不采用完整工作流 |
| <code>leonardomso/rust-skills</code> | unsafe/FFI、async、error、observability 审计 | Rust/JNI/并发/错误处理/可观测性 | 当前说明面向 Rust 1.96、edition 2024；不能直接套用新语法和 API | owner 批准后按子集试点，只读审计优先 |
| <code>trailofbits/skills</code> | rust-review、insecure-defaults、supply-chain-risk-auditor、differential-review、agentic-actions-auditor、audit-context-building、static-analysis | 安全上下文、Rust 评审、默认配置、供应链和变更审计 | 部分 Skill 可能安装扫描器、联网、运行构建或生成大量修复 | 推荐小集合；逐个审查和批准 |
| <code>flutter/skills</code> | 官方 Flutter/Dart 设计与实现指导的小子集 | Flutter UI、Dart 结构、测试与迁移参考 | 偏新应用/happy path，不能覆盖 legacy GetX、FRB、MethodChannel 和 CloudSend Android 高权限状态机 | 只作参考，按任务选择 |
| <code>anasfik/FlutterGuard</code> | 正式 APK/AAB 产物安全门 | 发布产物、签名、配置和安全属性检查 | 当前没有正式产物且禁止 build；可能处理签名和敏感发布资产 | 暂缓，正式发布环境按需使用 |
| <code>github/awesome-copilot</code> | GitHub Actions hardening/efficiency、runtime-upgrade、CodeQL | CI 工作流加固、升级规划、代码扫描 | 可能改 workflow、启用云扫描、升级运行时、提交配置；当前 Actions 只保留手动编排 | 按需，不安装整包 |

---

## 4. obra/superpowers

### 4.1 适合采用的四类方法

#### systematic-debugging

适用：

- Android <code>MediaProjection</code>、首帧、重连、黑屏与无视状态问题；
- Rust/Flutter/Kotlin 跨层命令链；
- Windows privacy mode 和 virtual display；
- ZEGO 控制状态与 Flutter RTC 媒体边界；
- endpoint、账号、ADB 和状态同步问题。

采用方式：

- 强制先建立可证伪假设；
- 分清 service state、frame state、UI waiting state；
- 记录源码锚点、观察证据和未确认项；
- 需要正式运行验证时输出《编译验证需求》或《设备验证需求》。

不得自动：

- 启动构建或测试；
- 修改代码来“试一下”；
- 重启外部服务；
- 捕获或上传真实用户数据。

#### verification-before-completion

适用：

- 检查文件、符号、协议发送端/接收端是否完整；
- 静态验证文档、路径、调用链和 diff；
- 在正式环境结果返回后核对验收条件。

CloudSend 改写：

- 当前阶段“verification”不等于必须本机 build/test；
- 无正式环境时必须明确写出哪些只完成了静态验证；
- 不得声称编译通过、设备通过或服务器通过；
- 需要运行验证时生成命令、环境、目录和目标清晰的验证需求。

#### writing-plans

适用：

- 高风险跨层修改；
- Android runtime、协议、FFI、构建、账号和安全任务；
- 版本升级、发布和文档迁移。

采用方式：

- 计划必须包含不变量、影响层、禁止事项、回退条件和验证方式；
- 计划不是执行授权；
- 不在计划中暗含 commit、push、worktree 或部署。

#### review

适用：

- 定向代码评审；
- 变更风险复核；
- 文档与源码一致性检查。

采用方式：

- 先报告可操作问题，再给摘要；
- 使用 CloudSend 风险等级；
- 不因为发现问题就自动修复；
- 不以通用最佳实践覆盖项目明确不变量。

### 4.2 不采用完整 superpowers 工作流

潜在冲突：

- 自动创建 Git worktree 或分支；
- 默认进入 red/green 测试循环；
- 将 build/test 视为完成前的强制本机动作；
- 在任务尾部自动 commit；
- 按通用 feature workflow 扩大修改范围。

决定：

- 不安装或启用完整工作流；
- 只将四类方法转写为 CloudSend 专属 Skill 的检查清单；
- 项目规则优先，外部 Skill 不获得额外权限。

---

## 5. leonardomso/rust-skills

### 5.1 建议子集

#### unsafe / FFI audit

高相关路径：

- <code>libs/scrap/src/android/pkg2230.rs</code>
- <code>libs/scrap/src/android/ffi.rs</code>
- <code>src/flutter_ffi.rs</code>
- <code>src/ui_session_interface.rs</code>
- Kotlin JNI bridges
- Windows native loader 和 virtual display FFI

重点：

- <code>static mut</code> 和跨线程读写；
- JNI local/global reference 生命周期；
- 指针、buffer 长度、像素格式和 ownership；
- Dart/Rust/Kotlin ABI 与动态库符号；
- panic、异常和超时跨 FFI 边界传播。

#### async / concurrency audit

高相关路径：

- rendezvous、relay、client/server connection；
- Android auto reconnect timer；
- terminal service；
- file transfer、record upload、sync；
- ZEGO 邀请状态和 Flutter timers。

重点：

- timer 堆叠和取消；
- channel 关闭；
- lock 顺序；
- async task 生命周期；
- 重连、断开和 stale state；
- 阻塞操作进入 async runtime。

#### error-handling audit

重点：

- 不能把 JNI 失败伪造成 false-ready 状态；
- 不能把 projection failure 升级为 core service failure；
- 网络/API 错误不得吞掉安全或账号状态；
- 错误信息不得泄露密码、token、endpoint 或用户数据；
- FFI 和平台异常必须保留可诊断上下文。

#### observability audit

重点：

- Android 状态包节流、超时和 single-flight；
- reconnect、relay、projection、ADB、ZEGO、upload 的结构化事件；
- 敏感字段 redaction；
- 可区分控制状态、媒体状态和 UI 状态；
- 日志级别和生产环境噪声。

### 5.2 版本冲突

候选技能可能以 Rust 1.96 和 edition 2024 为默认背景，而 CloudSend 明确是：

- <code>rust-version = 1.75</code>
- <code>edition = 2021</code>

因此禁止直接采用：

- edition 2024 专属语义；
- Rust 1.75 之后才稳定的 API；
- 自动改 edition；
- 自动更新 toolchain、Cargo.lock 或依赖；
- 以最新 Clippy/rustfmt 输出作为本项目强制标准；
- 未核验 MSRV 的 crate 推荐。

采用方式：

1. 先固定外部 Skill revision；
2. 把所有建议降级为审计问题；
3. 对照 Rust 1.75 标准库和当前依赖；
4. 需要编译验证时转成正式环境需求；
5. owner 批准后再做单模块试点。

当前决定：不安装整套，只保留 unsafe/FFI、async、error、observability 四个审计方向。

---

## 6. trailofbits/skills

Trail of Bits 候选与 CloudSend 的安全接管最相关，但也必须保持小集合。

### 6.1 rust-review

适用：

- Android Rust JNI；
- relay/client/server connection；
- terminal、file transfer、clipboard；
- privacy mode 和 virtual display；
- HTTP、sync、upload。

采用边界：

- 只读评审默认；
- findings 不自动转化为代码修改；
- 结论必须区分 CloudSend 自定义代码与 vendored/upstream code；
- 必须受 Rust 1.75/edition 2021 限制。

结论：推荐。

### 6.2 insecure-defaults

适用：

- 构建内置连接密码；
- Token 服务客户端 key；
- ZEGO 服务端配置；
- relay-only、TLS、HTTP endpoint；
- Android exported component、权限和调试入口；
- ADB 配对和 shell 行为；
- developer login bypass。

注意：

- 发现默认值不等于立即删除；
- 兼容性、离线部署和产品需求必须进入 Decision Log；
- 不在报告中复制 secret 值。

结论：推荐。

### 6.3 supply-chain-risk-auditor

适用：

- Cargo crates 和 vendored <code>libs/</code>；
- Flutter packages；
- Gradle/Android dependencies；
- packaged <code>libadb.so</code>；
- ZEGO SDK；
- Windows driver、DLL、vcpkg 和构建脚本；
- GitHub Actions 中的第三方 action。

限制：

- 本轮不联网拉取漏洞数据库；
- 不自动升级依赖或 lockfile；
- 不删除 vendor code；
- 任何许可证结论需要人工复核。

结论：推荐，先做离线资产清单，正式环境再做联网核验。

### 6.4 differential-review

适用：

- Android runtime 关键修复；
- protocol/protobuf 变化；
- endpoint 整体迁移；
- FFI 双路由差异；
- privacy mode、virtual display 和发布脚本；
- 安全修复前后行为比较。

采用方式：

- 只比较用户指定 diff/commit 范围；
- 不自动 checkout、rebase、merge；
- 不创建 worktree；
- 输出行为差异、风险和遗漏层。

结论：推荐。

### 6.5 agentic-actions-auditor

适用：

- AI 自动化权限；
- GitHub Actions；
- deploy scripts；
- Codex/Claude rules；
- CloudSend 专属 Skills；
- 外部 MCP、hooks 和插件。

重点：

- 是否扩张权限；
- 是否能执行 git、上传、删除或部署；
- 是否把 secret 写入输出；
- 是否存在 prompt injection 或不可信输入；
- 是否默认调用外部网络。

结论：推荐，尤其用于本次新建的 AI 工程规则和 release/security Skills。

### 6.6 audit-context-building

适用：

- 建立模块威胁模型；
- 在安全评审前生成资产、信任边界、入口和数据流；
- 避免只扫单文件而忽略 Flutter/Rust/Kotlin/协议跨层关系。

结论：推荐，可作为 <code>cloudsend-security-engineer</code> 的前置阶段。

### 6.7 static-analysis

适用：

- 组织已有静态检查结果；
- 根据语言和风险选择扫描策略；
- 输出正式环境要执行的工具和目标。

限制：

- 不自动安装扫描器；
- 不自动联网下载规则；
- 不在当前机器运行需要 build database 的工具；
- 不把工具告警直接视为漏洞；
- CodeQL、Clippy、Android lint 等需要正式环境时只输出验证需求。

结论：按需推荐。

### 6.8 当前 Trail of Bits 白名单

只考虑：

1. rust-review
2. insecure-defaults
3. supply-chain-risk-auditor
4. differential-review
5. agentic-actions-auditor
6. audit-context-building
7. static-analysis

不批量安装该仓库的其他 Skills。

---

## 7. anasfik/FlutterGuard

### 7.1 适用场景

FlutterGuard 只适合作为正式发布环境中的 APK/AAB 产物安全门，例如：

- release artifact 配置检查；
- manifest、debuggable、backup、exported component 检查；
- 签名、证书和打包属性检查；
- Dart/Flutter release 产物暴露面检查；
- 发布前安全验收。

### 7.2 当前不适用原因

- 当前环境不是正式 Android 构建机；
- 当前阶段禁止 <code>flutter build</code> 和 Gradle build；
- 没有本轮新生成的可信 APK/AAB；
- release signing、keystore 和服务器凭据属于敏感资产；
- 未安装或执行工具，因此扫描行为、产物处理和数据外发特征仍需隔离环境验证。

### 7.3 采用条件

1. owner 明确批准；
2. 在隔离的正式构建/安全环境运行；
3. 使用已批准的 release artifact 副本；
4. 确认工具不会上传 APK/AAB、证书或符号；
5. 固定工具版本和规则；
6. 将结果作为发布门，不自动修改源码或重新签名。

当前决定：暂缓，不安装。

### 7.4 Flutter 官方 Skills

`flutter/skills` 更适合作为 Flutter/Dart 的通用设计、实现、迁移和测试参考。CloudSend 的实际边界包含 legacy GetX/Provider 混用、session/global ownership、FRB 1.x generated bridge、手写 C FFI、Android MethodChannel、多窗口和高权限 Android runtime，因此不能直接套用“新建标准 Flutter app”的默认架构。

采用方式：

- 只选择与当前 Dart/Flutter 基线兼容的单项指导。
- 先由 `cloudsend-flutter-engineer` 对照 active route、state owner 和 bridge contract。
- 不自动升级 Flutter/Dart/package、改 state framework、生成新项目或运行测试。
- 对测试建议转成正式环境《编译验证需求》。

当前决定：只作为参考来源，不安装整套。

---

## 8. github/awesome-copilot

### 8.1 GitHub Actions hardening / efficiency

适用：

- action 固定 SHA；
- permissions 最小化；
- secret 使用边界；
- cache key 和 artifact retention；
- 并发、超时和手动触发；
- 避免不可信 fork 输入进入高权限 job。

CloudSend 特殊边界：

- 2026-06-25 曾删除大批 workflow，随后只保留手动 Actions 编排；
- 不得由外部 Skill 自动恢复 scheduled、push 或 release trigger；
- 不得自动写 secret、启用发布或上传 artifact；
- workflow 修改必须 owner 单独审查。

结论：按需推荐。

### 8.2 runtime-upgrade

适用：

- Rust 1.75/edition 2021 升级研究；
- Flutter、Dart、Gradle、Android Gradle Plugin、NDK 升级；
- target SDK 和 Windows toolchain 升级；
- dependency compatibility matrix。

限制：

- 只生成升级计划和风险矩阵；
- 不自动改版本号、edition、lockfile 或 workflow；
- 不把上游最新版本直接当 CloudSend 目标；
- 必须在正式环境分阶段验证。

结论：按需，当前不执行升级。

### 8.3 CodeQL

适用：

- Rust/C++/Java/Kotlin/JavaScript 等支持范围内的云端或 CI 静态分析；
- GitHub Actions 安全扫描；
- 安全基线和 differential scan。

限制：

- 未在本轮验证当前仓库权限、runner、语言支持和 workflow 执行要求；
- 可能需要 GitHub Advanced Security、runner 或云端上传；
- 不得自动创建 workflow、开启仓库功能或上传私有代码；
- 需要 owner、仓库管理员和安全负责人共同批准。

结论：按需评估，不在当前接管阶段启用。

### 8.4 当前 awesome-copilot 白名单

- github-actions-hardening
- github-actions-efficiency
- runtime-upgrade
- codeql

上述 Skill 名称和目录已在本次只读检查中核对；正式采用前仍须在固定 revision 上复核，本轮不安装。

---

## 9. 与 CloudSend 专属 Skills 的关系

外部 Skills 只能作为下列项目 Skills 的辅助检查清单：

| CloudSend Skill | 可引用外部能力 |
|---|---|
| <code>cloudsend-master</code> | writing-plans、verification-before-completion、differential-review |
| <code>cloudsend-rust-engineer</code> | rust-review、unsafe/FFI、async、error、observability |
| <code>cloudsend-android-engineer</code> | systematic-debugging、static-analysis、insecure-defaults |
| <code>cloudsend-flutter-engineer</code> | systematic-debugging、Flutter 官方 Skills 小子集；正式产物阶段的 FlutterGuard |
| <code>cloudsend-network-engineer</code> | audit-context-building、insecure-defaults、differential-review |
| <code>cloudsend-api-engineer</code> | insecure-defaults、supply-chain-risk-auditor、observability |
| <code>cloudsend-security-engineer</code> | Trail of Bits 白名单小集合、agentic-actions-auditor |
| <code>cloudsend-release-engineer</code> | GitHub Actions hardening/efficiency、runtime-upgrade、CodeQL、FlutterGuard |

优先级：

1. 用户当前明确指令；
2. 仓库 <code>AGENTS.md</code>；
3. <code>.codex/AI_RULES.md</code>；
4. CloudSend 专属 Skill；
5. 外部 Skill。

外部 Skill 不能反向修改此优先级。

---

## 10. 正式采用流程

任何外部 Skill 在安装前必须完成：

1. owner 明确批准 Skill 名称和用途；
2. 联网核验仓库所有者、准确路径、许可证和维护状态；
3. 固定 commit SHA 或 release version；
4. 完整阅读 Skill 指令、脚本、hooks、MCP 和配置；
5. 列出它可能执行的 shell、git、网络、文件和上传动作；
6. 检查是否读取环境变量、凭据、keystore、SSH 或云端 token；
7. 对照 Rust 1.75/edition 2021 和当前 Flutter/Gradle 基线；
8. 明确禁止动作和项目规则优先级；
9. 先在低风险只读任务试点；
10. 把采用决定、revision、适用范围和回退方式写入 <code>.codex/DECISION_LOG.md</code>。

安装后仍不得默认授权：

- build/test；
- git commit/push/merge/rebase；
- worktree/branch；
- 依赖或 runtime 升级；
- workflow 修改；
- 部署、上传或发布；
- secret 读取或输出；
- 源码、文档或历史删除。

---

## 11. 推荐顺序

### 第一优先级：安全上下文与变更审计

- audit-context-building
- differential-review
- rust-review
- insecure-defaults
- agentic-actions-auditor

原因：直接对应 CloudSend 当前的 FFI、默认凭据、部署脚本、AI 规则和跨层风险，同时可以只读使用。

### 第二优先级：工程纪律

- systematic-debugging
- verification-before-completion
- writing-plans
- review

原因：适合转写进项目专属 Skills，不需要安装完整 superpowers 工作流。

### 第三优先级：专项风险

- unsafe/FFI
- async
- error
- observability
- supply-chain-risk-auditor
- static-analysis

原因：价值高，但需要更强的 Rust 版本、工具和环境约束。

### 第四优先级：CI 与升级

- GitHub Actions hardening/efficiency
- runtime-upgrade
- CodeQL

原因：需要仓库管理员、正式 CI 和外部平台授权。

### 暂缓

- FlutterGuard

原因：只有正式 APK/AAB 和发布安全门场景才有意义。

---

## 12. 当前决定

- 除本次只读 GitHub source inspection 外，不执行候选 Skill 的网络调用或外部服务操作。
- 不安装任何候选 Skill。
- 不批量安装任何仓库。
- 不执行候选仓库中的脚本、hooks、MCP 或 workflow。
- 不修改现有 CloudSend Skills、<code>AI_RULES</code>、依赖或工具链。
- 不执行 build、test、Git 写操作、worktree、上传或发布。
- 仅将本报告作为未来 owner 审批和固定 revision 审计的依据。

正式采用时，项目 <code>AI_RULES</code> 和用户授权始终优先；外部 Skill 只提供方法，不提供额外权限。
