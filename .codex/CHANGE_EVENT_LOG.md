# Tunnel Change Event Log

## CE-20261003-T001-01：ADB全链、首轮故障修复与交付知识同步

- Timestamp：2026-10-03 Asia/Shanghai；Task T-2026-10-03-001。
- Actor：root tunnel-master及Android/local、Rust/protocol、helper三个受托agent；相互V0复核。
- Permission：用户明确继续ADB、修复A11y/native/portable并直接完成P0—P6源码后自行在服务器编译；不包含本机执行/设备/Git/发布。
- Files：android-adb、android-helper、Android adb/mirror/probe/runtime/manifest配置、Flutter adb页面/授权卡/remote顶栏/model/input、message.proto、Rust client/server/JNI encoded、build.sh/new-build.cmd/pc-bulid.cmd/portable，及对应docs和.codex。
- Delta：固定LADB预编译来源/ABI/receipt；本地有限命令、手动连接/自动发现与租约；受控typed远程同意/视频/输入/side模式；候选/barrier/Flutter帧ACK；A11y保留系统ServiceInfo和绑定状态复查；portable真实Cargo产物和驱动载荷校验。
- Review fixes：generation过期响应、Activity取消入队竞态、本机授权卡晚轮询、端点终止phase、候选失败回退、输入hover/按下释放、权限/能力丢失、超时与队列边界；新epoch作废旧Flutter帧回调；presented须匹配已发barrier及有效sequence；撤键盘权限仍允许原加密会话回退确认；电源键不得绕过display scope。
- Source/Generated：新增源码和构建配方；没有运行protobuf/FRB/codegen，没下载native binary或创建APK/EXE。
- Decision / Baseline：D-017追记ADR-0014；TUN-BL-2026-10-03-ADB；旧历史记录保留。
- Verification：Python AST/JSON/XML语法、git diff --check、全链静态审查；V1—V5 NOT_RUN。完整限制及验证入口见docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md。
- Git / Build / Delete / External / Release：none；无移动/删除项目资产，无本机构建/测试/analyze/codegen，无原checkout修改，无签名或发布。
- State：源码交付/正式验证handoff；不是P0—P6真机全部通过。

Schema Version：`1.0`  
Coverage Start：2026-07-12  
Last Updated：2026-10-03（Asia/Shanghai）
Mode：`append-only logical persisted-change events`

> 一个 Change Event 是一次逻辑完整的代码/文档持久化修改批次，不是每次按键、每个 patch hunk、只读调查或对话消息。本文件建立前的精确 edit-event 历史无法重建，只能按已知 Task 做 aggregate backfill。日志自身的创建/追加属于对应事件，不递归生成第二条事件。

## CE-20260712-T001-BACKFILL：项目资产接管变更集

- Record Type：`retrospective/aggregate`；精确事件未知。
- Task ID：`T-2026-07-12-001`
- Event Type：documentation / memory / Skill creation and update。
- Files：`docs/AI_ENGINEERING/`、初始 `.codex/`、`.agents/skills/` 与入口/审计文档；精确 per-patch 清单见当期交付和 worktree。
- State Delta：repository-side knowledge takeover established；no business behavior change。
- Verification：V0 only。
- Git / Build / Delete / External / Release：none。

## CE-20260712-T002-BACKFILL：AI 工程体系强化变更集

- Record Type：`retrospective/aggregate`；精确事件未知。
- Task ID：`T-2026-07-12-002`
- Event Type：governance documentation and Skill update。
- Files：entry、AI rules、Task Protocol、Development Workflow、External Registry、memory/logs、eight Skills and navigation pointers。
- State Delta：T0—T8、C0—C3 and external-asset governance established；no business behavior change。
- Verification：V0 only。
- Git / Build / Delete / External / Release：none。

## CE-20260712-T003-BACKFILL：AI 工程体系最终封版变更集

- Record Type：`retrospective/aggregate`；精确事件未知。
- Task ID：`T-2026-07-12-003`
- Event Type：ADR/Baseline/task/test/Safe-Superpowers governance creation。
- Files：`docs/ADR/`、`docs/BASELINE/`、`TASK_TEMPLATE.md`、`TEST_MATRIX.md`、Safe profile/Skill and related indexes。
- State Delta：repository-side AI governance sealed；no business behavior change。
- Verification：V0 only。
- Git / Build / Delete / External / Release：none。

## CE-20260712-T004-01：创建全局记忆核心文件

- Timestamp：2026-07-12T06:31:52+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：create。
- Files：`.codex/PROJECT_STATE.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGELOG_AI.md`、`.codex/CHANGE_EVENT_LOG.md`、`.codex/SESSION_START_PROTOCOL.md`。
- Change Summary：建立 current snapshot、multi-session work registry、task-level AI history、logical modification ledger and session recovery algorithm。
- Behavior / State Delta：AI governance only；no product/runtime behavior change。
- Related Decision / ADR：D-014 pending synchronization；ADR-0011、ADR-0012、ADR-0013 context。
- Verification：V0 pending final task audit。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20260712-T004-02：接入入口、任务协议与治理索引

- Timestamp：2026-07-12T06:31:52+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：update。
- Files：`PROJECT_START_HERE.md`、`docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`、`TASK_TEMPLATE.md`、`.codex/DECISION_LOG.md`、`.codex/CHANGE_EVENT_LOG.md`。
- Change Summary：固化八项 session recovery、T0/T5/T7/T8 memory transaction、Task artifact 字段和 D-014 governance decision。
- Behavior / State Delta：新会话与 AI task governance behavior changed；product/runtime behavior unchanged。
- Related Decision / ADR：D-014 / ADR-0011、ADR-0012、ADR-0013。
- Verification：V0 pending final task audit。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20260712-T004-03：消除新会话恢复顺序歧义

- Timestamp：2026-07-12T06:40:28+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：update。
- Files：`PROJECT_START_HERE.md`、`.codex/AI_RULES.md`、`.codex/SESSION_START_PROTOCOL.md`、`docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`、`.codex/PROJECT_STATE.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGE_EVENT_LOG.md`。
- Change Summary：明确 Session Start Protocol 位于第 1、2 项之间作为执行细则；统一 AI Rules 的全局恢复顺序；明确 active task 与 Task History closing record 的时序。
- Behavior / State Delta：session recovery ambiguity removed；product/runtime behavior unchanged。
- Related Decision / ADR：D-014 / ADR-0011、ADR-0012、ADR-0013。
- Verification：first blind recovery identified the ambiguity；regression pending。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20260712-T004-04：完成全局记忆同步与任务关闭

- Timestamp：2026-07-12T06:42:13+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：update / task closure。
- Files：`.codex/SESSION_START_PROTOCOL.md`、`.codex/TASK_HISTORY.md`、`.codex/CHANGELOG_AI.md`、`.codex/CHANGE_EVENT_LOG.md`、`.codex/PROJECT_STATE.md`、`.codex/CURRENT_WORK.md`。
- Change Summary：修正 protocol metadata；写入 canonical task/changelog/event record；更新 final State pointers；最后关闭 Current Work active row。
- Behavior / State Delta：global memory task completed；active task count returns to zero；product/runtime behavior unchanged。
- Related Decision / ADR：D-014 / ADR-0011、ADR-0012、ADR-0013。
- Verification：V0 structural/sensitive/scope checks and blind recovery regression PASS；final closure recheck required immediately after this event。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20260712-T004-05：修正最终恢复索引

- Timestamp：2026-07-12T06:43:50+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：corrective update。
- Files：`.codex/AI_RULES.md`、`.codex/PROJECT_STATE.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGELOG_AI.md`、`.codex/TASK_HISTORY.md`、`.codex/CHANGE_EVENT_LOG.md`。
- Change Summary：将 AI Rules 第 8 项固定为完整 Skill 路径；把 Last Closed Task 的 Changelog 指针改为 T-004；统一最终 State/Work/Event references。
- Behavior / State Delta：recovery index consistency corrected；product/runtime behavior unchanged。
- Related Decision / ADR：D-014 / ADR-0011、ADR-0012、ADR-0013。
- Verification：final V0 relation/order audit found both mismatches；full recheck follows this event。
- Corrects / Supersedes：corrects final pointer metadata produced around `CE-20260712-T004-04`；does not change its task-closure meaning。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20260712-T004-06：规范机器可验证的恢复标识

- Timestamp：2026-07-12T06:45:42+08:00
- Task ID：`T-2026-07-12-004`
- Actor / Primary Skill：Tunnel Principal Engineer / `tunnel-master`
- Permission：`C1`，来自项目 owner 当前明确请求。
- Event Type：corrective metadata update。
- Files：`.codex/AI_RULES.md`、`.codex/PROJECT_STATE.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGELOG_AI.md`、`.codex/TASK_HISTORY.md`、`.codex/CHANGE_EVENT_LOG.md`。
- Change Summary：把 AI Rules 第 2 项从代词改为完整路径；把 task/changelog event range 的两端都改为完整 Event ID；同步最终 State/Work pointer。
- Behavior / State Delta：machine-verifiable recovery references normalized；product/runtime behavior unchanged。
- Related Decision / ADR：D-014 / ADR-0011、ADR-0012、ADR-0013。
- Verification：addresses the only remaining order/relation failures from the final V0 audit；full recheck follows this event。
- Corrects / Supersedes：metadata references only；previous event meanings remain unchanged。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20261002-T001-01：登记接管计划与并行审计范围

- Timestamp：2026-10-02（Asia/Shanghai）
- Task ID：`T-2026-10-02-001`
- Actor / Primary Skill：primary agent / `tunnel-master`
- Permission：C1，当前用户明确文档接管及多 agent 请求。
- Event Type：create / update。
- Files：`docs/AI_ENGINEERING/audits/2026-10-02/TAKEOVER_TASK.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGE_EVENT_LOG.md`。
- State Delta：登记独立的本轮任务，保护旧历史并记录 HEAD/历史对象漂移；不改变产品行为。
- Verification：T0 只读 Git 状态 clean；HEAD detached；旧对象不可解析；V0 文档最终检查待执行。
- Decision / ADR：N/A — no decision delta；沿用 ADR-0011/0012。
- Git / Build / Delete / Move / Version / External / Release：none。

## CE-20261002-T001-02：当前源码审计、文档纠偏与接管记忆同步

- Timestamp：2026-10-02（Asia/Shanghai）
- Task ID：`T-2026-10-02-001`
- Actor / Primary Skill：primary `tunnel-master` + Android/Flutter、Rust/Network/Windows、API/Security/Release协作审计。
- Permission：C1，当前用户明确请求文档/地图/记忆整理及多agent；未扩大到业务或C3。
- Event Type：create / update / task closure。
- Source of Truth：所有持久变更为手写Markdown；源码/配置/协议/manifest/lock/build script/generated输出未改。
- State Delta：新建当前baseline、功能对接地图与dated audit；修正历史/current混用、terminal与Android/API/build事实；登记SEC-017—019；保留旧记录；同步memory并关闭任务。
- Decision / ADR：N/A — no decision delta；既有D-014/ADR不变。
- Verification：V0源码与交叉复核、9个blob hashes独立复算、local links/fences/path exceptions、diff whitespace、敏感新增内容、scope/无删除/无staging与Task/Event/State指针检查。修正初轮符号/语义/空白问题；V1—V5 NOT_RUN。
- Git / Build / Delete / Move / Version / Sign / Package / External / Release：none。
- Corrects：当前事实更正不抹去7月固定日期快照；不宣称可重放旧Git历史、credential有效性或设备/后端已验证。

实际文件（41份Markdown，包含本事件记录与任务关闭）：

- `.codex/ARCHITECTURE_MEMORY.md`
- `.codex/CHANGELOG_AI.md`
- `.codex/CHANGE_EVENT_LOG.md`
- `.codex/CURRENT_WORK.md`
- `.codex/PROJECT_MEMORY.md`
- `.codex/PROJECT_STATE.md`
- `.codex/TASK_HISTORY.md`
- `AGENTS.md`
- `CLAUDE.md`
- `EXTERNAL_ASSET_REGISTRY.md`
- `PROJECT_START_HERE.md`
- `README.md`
- `TEST_MATRIX.md`
- `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`
- `docs/AI_ENGINEERING/01_ARCHITECTURE.md`
- `docs/AI_ENGINEERING/02_SOURCE_MAP.md`
- `docs/AI_ENGINEERING/03_MODULE_DESIGN.md`
- `docs/AI_ENGINEERING/04_ANDROID_PIPELINE.md`
- `docs/AI_ENGINEERING/05_WINDOWS_PIPELINE.md`
- `docs/AI_ENGINEERING/06_NETWORK_PROTOCOL.md`
- `docs/AI_ENGINEERING/07_API_SYSTEM.md`
- `docs/AI_ENGINEERING/08_BUILD_SYSTEM.md`
- `docs/AI_ENGINEERING/09_DEBUG_SYSTEM.md`
- `docs/AI_ENGINEERING/10_SECURITY_MODEL.md`
- `docs/AI_ENGINEERING/11_ROADMAP.md`
- `docs/AI_ENGINEERING/12_FEATURE_MAP.md`
- `docs/AI_ENGINEERING/TUNNEL_AI_ENGINEERING_FINAL_SEAL_REPORT.md`
- `docs/AI_ENGINEERING/TUNNEL_AI_ENGINEERING_STRENGTHENING_REPORT.md`
- `docs/AI_ENGINEERING/TUNNEL_AI_PRINCIPAL_ENGINEER_HANDOVER_REPORT.md`
- `docs/AI_ENGINEERING/DOCUMENT_AUDIT_REPORT.md`
- `docs/AI_ENGINEERING/LEGACY_DOCUMENT_MIGRATION_REPORT.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/ANDROID_FLUTTER_AUDIT.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/API_SECURITY_RELEASE_AUDIT.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/DOCUMENT_REGISTER.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/README.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/RUST_NETWORK_WINDOWS_AUDIT.md`
- `docs/AI_ENGINEERING/audits/2026-10-02/TAKEOVER_TASK.md`
- `docs/BASELINE/2026-10-02_SOURCE_BASELINE.md`
- `docs/BASELINE/BASELINE_INDEX.md`
- `docs/ENGINEERING_INDEX.md`
- `terminal.md`


## CE-20261002-T002-01：登记 ADB 投屏规划任务

- Timestamp：2026-10-02（Asia/Shanghai）。
- Task ID / Actor：T-2026-10-02-002 / tunnel-master。
- Permission：C1；用户明确要求扫描、方案/教程/开发方向文档；后续再实施业务。
- Files：`docs/plans/ADB_REMOTE_MIRRORING_TASK.md`、`.codex/CURRENT_WORK.md`、`.codex/CHANGE_EVENT_LOG.md`。
- State Delta：新文档规划任务进入T2—T3；恢复当前test/532637a/初始clean；原项目不改。
- Source of Truth：手写文档；业务、协议、配置、生成文件无变化。
- Decision / ADR：评估新proposed ADR-0014；当前ADR-0007仍生效。
- Verification：V0入口/任务边界/当前源码与分支观察；无运行证明。
- Git / Build / Device / Delete / Release：none。仅公开官方资料读取，不调用项目外部服务。

## CE-20261002-T002-02：ADB远程投屏方案、ADR提案与记忆交付

- Timestamp / Task：2026-10-02（Asia/Shanghai） / T-2026-10-02-002。
- Actor：tunnel-master；三个Android/Flutter/Rust-Network协作者只读审查，主agent统一文档写入。
- Permission：C1，用户本轮明确要求规划/教程/开发方向，后续才实施。
- Event Type / Source：create/update/closure；全部手写Markdown，没有业务/config/proto/generated改动。
- Delta：完整九项映射、源码证据、受控helper/relay路线、正交状态和切换事务、侧按钮兼容、P0—P6、ADBM-01—30及教程；proposed ADR/Decision、资产/test导航与memory同步。
- Review corrections：首帧与input barrier、回滚新epoch、订阅lease原子性、真实帧源与互斥override、pause迟到回调、源替换禁止自动ignore、bootstrap例外、settings无CAS限制。
- Decision：ADR-0014/D-015 proposed；当前ADR-0007不废止。
- Verification：V0官方与仓内证据、三领域二次审查、文档link/fence/编号/scope/diff/敏感值和memory指针检查；V1—V5及设备case NOT_RUN。
- Git / Build / Device / Delete / Move / Version / Sign / Package / Release：none；仅公开官方资料读取，不访问项目生产系统。
- As-of：test/532637a；文档dirty，未stage/commit；原项目目录未改。
- Corrects：只更新当前memory快照，不改写旧baseline/审计报告或声称新功能已实施。

实际文件（14份Markdown，含本事件与关闭记录）：

- `docs/plans/ADB_REMOTE_MIRRORING_PLAN.md`
- `docs/plans/ADB_REMOTE_MIRRORING_TASK.md`
- `docs/ADR/0014-controlled-remote-adb-mirroring.md`
- `docs/ADR/README.md`
- `docs/AI_ENGINEERING/11_ROADMAP.md`
- `EXTERNAL_ASSET_REGISTRY.md`
- `TEST_MATRIX.md`
- `.codex/PROJECT_MEMORY.md`
- `.codex/PROJECT_STATE.md`
- `.codex/CURRENT_WORK.md`
- `.codex/DECISION_LOG.md`
- `.codex/TASK_HISTORY.md`
- `.codex/CHANGELOG_AI.md`
- `.codex/CHANGE_EVENT_LOG.md`

## CE-20261002-T003-01：开始分阶段实施 P0 本机原型

- Task / Timestamp：T-2026-10-02-003 / 2026-10-02。
- Authority：当前用户要求按方案开始；C2，P0证据门和C3边界保留。
- Files：`docs/plans/ADB_REMOTE_MIRRORING_IMPLEMENTATION_TASK.md`、`.codex/CURRENT_WORK.md`、`docs/ADR/0014-controlled-remote-adb-mirroring.md`、`.codex/CHANGE_EVENT_LOG.md`。
- Delta：登记P0源码任务及分阶段方案接受记录；原T002未提交文档保留。当前不开放remote feature。
- Verification：V0源码/状态/资产和入口观察；原生产物与正式工具链/设备证据待取得。
- Git / Build / Test execution / Device / Delete / Release：none。

## CE-20261002-T003-02：P0本机原型源码与验证交接

- Task / Timestamp：T-2026-10-02-003 / 2026-10-02。
- Actor / Authority：tunnel-master及三个分域agent；当前用户接受方案后要求开始，C2；无C3。
- Type：create/update；非generated。
- Delta：default-off本机身份/认证协议/helper采样/有界supervisor/Flutter诊断；固定官方来源与LICENSE/NOTICE、离线build recipe、JUnit源码；无PC远程入口。
- Review：跨域V0，修正UI位置、assets路径、进程级操作计数、错误包失败关闭；旧Runner自动重连和shell长命令不在P0强隔离范围，已记录。
- Evidence：只读源码/API/来源、scope/diff、文档引用检查；正式build/test/device/codegen与decode/render/relay均NOT_RUN；P0整体未通过。
- Decision：D-015/ADR-0014分阶段接受；ADR-0007 local-only边界保留。Source imported不等于binary可用。
- Handoff：STATE-20261002-003 / WORK-20261002-006 / T6；正式命令与结果模板在docs/plans/ADB_P0_VALIDATION_RUNBOOK.md。
- Git / Build / Test execution / Device / Delete / Release：none；不改原项目目录；保留T002未提交文档。
- Files（本次修改及文档memory同步）：

- `.codex/CHANGELOG_AI.md`
- `.codex/CHANGE_EVENT_LOG.md`
- `.codex/CURRENT_WORK.md`
- `.codex/DECISION_LOG.md`
- `.codex/PROJECT_MEMORY.md`
- `.codex/PROJECT_STATE.md`
- `.codex/TASK_HISTORY.md`
- `.gitignore`
- `EXTERNAL_ASSET_REGISTRY.md`
- `TEST_MATRIX.md`
- `android-helper/.gitignore`
- `android-helper/README.md`
- `android-helper/build_helper.py`
- `android-helper/protocol/src/main/java/com/tunnel/adb/protocol/AdbWire.java`
- `android-helper/protocol/src/test/java/com/tunnel/adb/protocol/AdbWireTest.java`
- `android-helper/server/LICENSE.scrcpy`
- `android-helper/server/NOTICE`
- `android-helper/server/PROVENANCE.md`
- `android-helper/server/README.md`
- `android-helper/server/src/main/java/com/tunnel/adbhelper/DisplayCapture.java`
- `android-helper/server/src/main/java/com/tunnel/adbhelper/H264AnnexB.java`
- `android-helper/server/src/main/java/com/tunnel/adbhelper/Server.java`
- `android-helper/server/src/main/java/com/tunnel/adbhelper/ShellEnvironment.java`
- `android-helper/server/src/main/java/com/tunnel/adbhelper/VideoEncoder.java`
- `docs/ADR/0014-controlled-remote-adb-mirroring.md`
- `docs/ADR/README.md`
- `docs/AI_ENGINEERING/02_SOURCE_MAP.md`
- `docs/AI_ENGINEERING/04_ANDROID_PIPELINE.md`
- `docs/AI_ENGINEERING/08_BUILD_SYSTEM.md`
- `docs/AI_ENGINEERING/11_ROADMAP.md`
- `docs/plans/ADB_P0_VALIDATION_RUNBOOK.md`
- `docs/plans/ADB_REMOTE_MIRRORING_IMPLEMENTATION_TASK.md`
- `docs/plans/ADB_REMOTE_MIRRORING_PLAN.md`
- `flutter/android/app/build.gradle`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/mirror/TunnelAdbPrototype.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/mirror/PackagedAdbHelper.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/probe/AdbIdentityOutputParser.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/probe/BoundedProcessRunner.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/probe/LocalAdbIdentityProbe.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/probe/LocalAdbTarget.kt`
- `flutter/android/app/src/main/kotlin/com/tunnel/app/oFtTiPzsqzBHGigp.kt`
- `flutter/android/app/src/test/kotlin/com/tunnel/app/adb/probe/AdbIdentityOutputParserTest.kt`
- `flutter/android/app/src/test/kotlin/com/tunnel/app/adb/probe/BoundedProcessRunnerTest.kt`
- `flutter/android/app/src/test/kotlin/com/tunnel/app/adb/probe/LocalAdbTargetPolicyTest.kt`
- `flutter/lib/mobile/pages/adb_page.dart`
- `flutter/lib/mobile/widgets/adb_mirror_probe_card.dart`

## Event Schema

## CE-20261002-T004-01：Tunnel 身份迁移与跨层消费者对齐

- Timestamp：2026-10-02 Asia/Shanghai。
- Task ID / Actor：T-2026-10-02-004 / root tunnel-master；受托Android/Rust agents限定范围修改，Flutter/docs agent只读review。
- Permission：用户本轮明确全局更名、包名与产品信息迁移；必要命名路径移动/生成文件机械命名同步纳入范围；无执行/发布授权。
- Event Type：update / create / move；generated文件现存文本同步，codegen未执行。
- Files：逐文件当前路径见 `docs/plans/TUNNEL_IDENTITY_MIGRATION_FILES.md`（243项）；Git diff保留旧路径到新路径的来源。含原T002/T003未提交内容，本事件不独占全部文件的创建归属。
- Delta：Tunnel/tunnel/隧道/com.tunnel.app/com.tunnel；native库、ABI/FRB、MethodChannel、helper package/magic/HMAC、状态字段、broker示例、portable格式/启动路径、产品资源、文档/Skills/记忆同步。
- 特殊修复：portable marker长度推导与同路径删除；非主平台直接consumer、FRB native/Web类与显式生成参数；既有Linux发行服务命名债务明确保留，不虚报全平台发布。
- Historical normalization：旧文档品牌名称与路径按用户要求统一；旧ID/日期/hash/验证结果不变，原文须查Git原对象。Supersedes ADR-0002 current identity；ADR-0015 / D-016。
- Source of truth：源文件/Cargo/manifest/proto/build配置；生成桥接需正式环境重生核验；Baseline TUN-BL-2026-10-02-IDENTITY。
- Verification：V0静态扫描与跨域review；构建/单测/analyze/codegen/设备/签名 NOT_RUN。
- Git / Build / Delete / External / Release：none；没有清理或丢弃内容，23个必要名称/包路径移动在worktree内完成；外部服务/签名/原checkout未修改。
- State：T004 source delivered；T003保持paused/P0未通过；STATE-20261002-004 / WORK-20261002-008。

### Event 字段模板

```text
## CE-YYYYMMDD-TNNN-NN：标题
- Timestamp：
- Task ID：
- Actor / Primary Skill：
- Permission / Confirmation Reference：
- Event Type：create / update / delete / move / generated
- Files：repository-relative paths only
- Change Summary：
- Behavior / State Delta：
- Source of Truth / Generated Status：
- Related Decision / ADR：
- Verification：
- Git / Build / Delete / External / Release：
- Corrects / Supersedes：
```

## Logging Rules

- 每次有授权的 logical persisted-change batch 追加一个唯一 Event ID；每个 Event 必须关联 Task ID。
- 多会话使用 `CE-YYYYMMDD-TNNN-NN` 的 task-local sequence，写前重读，禁止复用 ID。
- 只读调查、计划、工具输出和未落盘建议不写 event。
- 不复制完整 diff、源码正文、prompt/transcript、secret/token/password/key、生产地址、IP、peer/device/UUID、PII 或绝对用户路径。
- 删除、移动、generated、Git、build、external、release 若实际发生必须逐项记录；未发生明确写 `none`。
- 错误记录通过新 Event `corrects/supersedes`，旧事件保留。
