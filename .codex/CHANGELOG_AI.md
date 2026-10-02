# Tunnel AI Changelog

## T-2026-10-03-005：ADB会话持续授权

- 按当前用户指令取消600秒授权与生产helper一小时硬期限；conn scopes/status consentActive随连接持续，断连/撤销/退出ADB/故障清理。关共享只暂停采集。
- Helper protocol3/duration0，HMAC/manifest/Gradle同步；旧helper拒绝，P0有限诊断和故障守卫保持；PC移除倒计时并更新提示/面板。
- ADR-0017 / D-019限定替代ADR-0016期限；保留系统pair key及T003/T004源码补丁。
- V0源码/词法/版本/Python AST/引用/文档/diff；协议测试源码NOT_RUN，正式构建/设备/Git/发布未执行。STATE-20261003-005 / WORK-20261003-009；CE-20261003-T005-01；T6 handoff。

## T-2026-10-03-004：PC远程无线ADB配对与手机界面移除

- Source/V0交付：手机AdbPage与两card移除，内部native/helper/shell保留；PC draggable配对/连接/撤销，独立pairing状态不改video事务，状态入右上TunnelStatusMonitor。
- Root causes：-H 127.0.0.1不启动本机daemon；pair返回但未connect；localhost+独立连接发现/freshprobe修复。cancel/断线阻止lategrant，取消等待worker确认；临时撤权与身份注销分离，home空列表fallback。
- Policy：ADR-0016 / D-018，已授权secure controller显式pair/authorize→当前conn600s scopes；不是旧phone-local consent，不从历史paired自动grant。
- Verification：源码词法/引用/协议/秘密与交叉review/diff V0；新增Rust测试源码NOT_RUN；build/device/Android11—16/16KiB/Git/sign/release均未执行。保留T003 dirty patch。
- Guide：docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md；Task：ADB_REMOTE_PAIRING_TASK.md；Event CE-20261003-T004-01；STATE-20261003-004 / WORK-20261003-007。

## T-2026-10-03-003：Windows 构建/驱动/自解压修复

- Source/V0交付：统一三个CMD入口与仓内PS流程，修复DLL真实路径/Release复制、官方driver准备、缺失注入DLL源码构建、独立manifest和精确payload核验；日志/来源/输出manifest保留。
- Guide：docs/plans/WINDOWS_BUILD_GUIDE.md；Task：WINDOWS_BUILD_REPAIR_TASK；Baseline：TUN-BL-2026-10-03-WINDOWS-BUILD；Event：CE-20261003-T003-01。
- 未运行：合同测试、正式Windows编译/下载/解压/驱动、Git/签名/发布。T001 ADB设备验证仍待执行，T002 helper修复未改。

## T-2026-10-03-001：ADB全链源码交付与验证交接

- Result：受控remote视频/输入/模式/本机consent与PC顶栏源码接入；A11y授权返回/绑定处理、libadb供应缺口、portable打包链修复。
- Scope：用户改为先实现后服务器集中验证；OnePlus ACE6T/iQOO Neo9 Android16。源码覆盖P0—P6各层，实际阶段验收仍NOT_RUN；防触摸及A11y重新启用限制明确记录。
- Evidence：V0静态，不是编译或真机通过；没有本机build/test/analyze/codegen/device/Git/release。
- Guide：docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md；Event CE-20261003-T001-01；D-017/ADR-0014补充；TUN-BL-2026-10-03-ADB。

Schema Version：`1.0`  
Coverage Start：2026-07-12  
Last Updated：2026-10-03（Asia/Shanghai）
Mode：`append-only task-level index`

## T-2026-10-02-004：Tunnel 产品与工程命名迁移

- Record Type：contemporaneous；Status：source delivered / V0；formal verification pending。
- 用户明确授权全局品牌、包名和必要的路径迁移；T003按用户要求暂停，P0源码与原有未提交内容保留。
- Outcome：Tunnel / tunnel / 隧道、com.tunnel.app、com.tunnel；native/ABI/FRB/构建与安装链、Android/helper源码路径、状态字段、broker契约、文档/Skills同步。
- 特殊修正：portable marker长度推导与大小写同路径删除风险；FRB生成类名与native/Web消费者一致；非主平台直接native消费者同步。
- Event：CE-20261002-T004-01；Decision：D-016 / ADR-0015；Baseline：TUN-BL-2026-10-02-IDENTITY。
- 未执行：项目构建/测试/codegen、设备操作、签名/部署、Git写入；证书指纹/产物hash未伪造。
- 后续：用户正式环境配套重建并反馈证据，之后继续T003/P0。详细范围、验证需求见 `docs/plans/TUNNEL_IDENTITY_MIGRATION_TASK.md`。

> 本文件记录可由 `.codex/TASK_HISTORY.md` 和交付物证明的 AI-assisted engineering task 结果。它不是产品 changelog、Git history、Decision Log 或逐文件修改日志。2026-07-12 之前的提交没有可靠 AI attribution，因此不补猜；精确修改事件在本日志建立前不可重建，统一标记为 retrospective/backfill。

## T-2026-07-12-001：项目资产接管

- Record Type：`retrospective/backfill`
- Status：`completed — repository-side only`
- Scope：全仓 archaeology、架构/文档审计、长期 memory 与八个领域 Skills。
- Outcome：建立 `docs/AI_ENGINEERING/`、初始 `.codex/` 和 `.agents/skills/`；识别外部资产、安全与可复现性缺口。
- Business Behavior Change：none。
- Highest Verification：V0；未执行项目 build/test。
- Decisions：D-001—D-011；ADR backfill 由后续任务完成。
- Detailed Record：`TASK_HISTORY.md` 的同 Task ID。

## T-2026-07-12-002：AI 工程体系强化

- Record Type：`retrospective/backfill`
- Status：`completed — repository-side governance`
- Scope：统一入口、AI rules、T0—T8、Development Workflow、External Asset Registry 与八个 Skills 审查。
- Outcome：形成固定任务/权限/外部资产链路；未扩大业务或外部权限。
- Business Behavior Change：none。
- Highest Verification：V0；未执行项目 build/test。
- Decisions：D-012 / ADR-0012。
- Detailed Record：`TASK_HISTORY.md` 的同 Task ID。

## T-2026-07-12-003：AI 工程体系最终封版

- Record Type：`retrospective/backfill`
- Status：`completed — repository-side governance seal`
- Scope：ADR、Baseline、Task Template、Test Matrix 与只读 Safe Superpowers adapter。
- Outcome：建立 14 项 ADR、工程 Baseline、52-case test matrix 和安全推理边界。
- Business Behavior Change：none。
- Highest Verification：V0；V1—V5 未执行。
- Decisions：D-013 / ADR-0000 / ADR-0013。
- Detailed Record：`TASK_HISTORY.md` 的同 Task ID。

## T-2026-07-12-004：AI 全局记忆增强

- Record Type：`contemporaneous`
- Status：`completed — repository-owned global session memory`
- Scope：Project State、multi-session Current Work、AI Changelog、Change Event ledger、Session Start Protocol，以及入口/任务协议同步。
- Outcome：新 Codex 会话可按项目文件恢复 Baseline、当前状态、并行任务、AI 历史、重大决定、相关 ADR/Skill 和下一确认门；旧会话权限明确不继承。
- Persistent Changes：五个新 `.codex` 文件；入口、AI Rules、Task Protocol、Task Template、Decision/Task history integration。
- Business Behavior Change：none。
- Change Event IDs：`CE-20260712-T004-01`—`CE-20260712-T004-06`。
- Highest Verification：V0；blind session recovery + ambiguity-fix regression PASS；未执行项目 build/test。
- Decision / ADR：D-014；ADR-0011、ADR-0012、ADR-0013 context。
- Residual Risk：file-based memory is as-of and cooperative, not a lock or production telemetry；每个新会话仍须重查 HEAD/dirty state 和当前授权。
- Next Gate：下一个用户任务从 Session Start Protocol + T0 开始；无 standing C2/C3。
- Detailed Record：`TASK_HISTORY.md` 的同 Task ID。

## T-2026-10-02-001：当前源码复核与接管文档整理

- Record Type：contemporaneous。
- Status：completed — repository inventory / key-path V0 / documentation only。
- Source：detached HEAD `5cee6921ec10971bb4654bc010f9328d7f70d02b`，初始clean；`CS-BL-2026-10-02-5cee692`。
- Scope：三个领域agent源码核验及交叉复核；975文件/104份原有Markdown登记；canonical文档定点更正、功能/对接地图、baseline、memory同步。
- Outcome：旧HEAD/单root历史漂移、terminal进程内persistence、Android桥/帧/owner、API wrapper/状态、CI/build入口、缺失原生资产等事实修正；新增SEC-017—019条件性静态风险。
- Persistent Changes：8份新文档及相关既有Markdown；精确路径见`CE-20261002-T001-02`。
- Business Behavior Change：none；未改源码/配置/协议/依赖/脚本/版本。
- Highest Verification：V0；本地引用/路径/围栏、diff、敏感新增内容、scope及指针复核；独立复算9个HEAD blob hashes一致。V1—V5 NOT_RUN。
- Decision / ADR：N/A — no decision delta；既有ADR与D-014不变。
- Change Events：`CE-20261002-T001-01`、`CE-20261002-T001-02`。
- Residual Risk：不是逐行全仓审计；完整其他OS、外部API/DB/hbbs/hbbr/RTC、native资产、正式运行与发布仍缺证据。
- Next：从`12_FEATURE_MAP.md`定位用户后续具体需求；按Task/TEST_MATRIX规划实现与验证；无C3授权。
- Detailed Record：`TASK_HISTORY.md`及`docs/AI_ENGINEERING/audits/2026-10-02/TAKEOVER_TASK.md`。

## T-2026-10-02-002：远程ADB投屏方案和教程

- Record Type / Status：contemporaneous / completed documentation only。
- Source：test/532637a；初始clean；业务基线CS-BL-2026-10-02-5cee692。
- Outcome：九项需求映射、源码地图、helper/relay路线、模块/权限/切换/侧按钮合同、P0—P6、30个ADBM用例、实现后教程。
- Review：Android、Rust/Network、Flutter分别源码核查并二次审查；已修正首帧/input时序、settings并发和生命周期等合同。
- Business Behavior Change：none。Git/build/device/delete/release：none。
- Decision：D-015/ADR-0014 proposed；ADR-0007仍accepted。
- Highest Verification：V0；V1—V5 NOT_RUN；不代表所有ROM或运行已支持。
- Change Events：CE-20261002-T002-01、CE-20261002-T002-02；14份Markdown。
- Memory / Preference：以后默认独立worktree开发；原项目本轮未改；State/CW/History/ProjectMemory同步。
- Residual / Next：native/helper准入、目标机型、P0原型与正式验证未完成；后续按具体用户实施任务推进。
- Detailed Record：`TASK_HISTORY.md`与`docs/plans/ADB_REMOTE_MIRRORING_TASK.md`。

## T-2026-10-02-003：ADB P0本机原型源码交接

- Record Type / Status：contemporaneous / handoff milestone，T6等待正式验证；P0整体未通过。
- Source：test/532637a + 本次源码；保留T002文档；未提交。
- Outcome：本机身份探针、共享认证协议、固定来源shell H264原型、APK有界supervisor、默认关闭的Flutter诊断、离线build recipe及JUnit源码。
- Evidence：跨域V0源码/来源/合同复核；正式build/test/device、decode/render/relay NOT_RUN。
- Boundary：无PC顶栏/remote protocol/JNI采集或无障碍运行改动；旧Runner重连和长shell命令仍需P1隔离。
- Decision：D-015/ADR-0014分阶段accepted；ADR-0007 local-only边界保留。
- Event / Memory：CE-20261002-T003-01/02；STATE-20261002-003、WORK-20261002-006。
- Git / Build / Device / Delete / Sign / Release：none。
- Next：正式环境/native来源/目标设备与具体执行授权，按docs/plans/ADB_P0_VALIDATION_RUNBOOK.md验证并补齐P0。
- Detailed Record：TASK_HISTORY.md、docs/plans/ADB_REMOTE_MIRRORING_IMPLEMENTATION_TASK.md。

## Update Rules

- 每个 completed、blocked 或 cancelled AI task 追加一条；长任务仅在正式 handoff milestone 时追加。
- 只保存 scope、outcome、state delta、event IDs、verification、decision/ADR、residual risk 和 next gate 的摘要。
- 完整授权、设计、文件、验证与 rollback 仍以 `TASK_HISTORY.md` / Task artifact 为准。
- 历史纠正使用新的 `corrects/supersedes` 条目，不静默删除旧条目。
- 不保存 prompt/transcript、完整 diff、secret、生产地址、peer/device ID、PII 或绝对用户路径。
