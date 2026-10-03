# Tunnel Task History

## T-2026-10-03-008：ADB可靠性、PC交互与Windows打包修复

- 用户直接授权修源码、简化UI、拉取LADB研究；source test/0720e49+保留T007dirty；Baseline ADB/WINDOWS-BUILD，D-020。
- Delta：私有ADBdaemon、取消回收、NSD多记录、UID就绪、服务重建资格、helper固定错误、PC本地焦点/输入租约/按钮状态/ACK超时与双行面板；VS工具集、x64、Release hash、自解压失败可见。
- LADB55文本已取得，固定commit与现有native锁一致；未下载执行binary。V0 Python/PS AST、Dart/Kotlin词法、跨层/来源/diff通过；新增测试源码未运行，服务器/两Android16设备待验。
- Task/hand-off：docs/plans/ADB_RELIABILITY_REPAIR_TASK.md；T6源码交付，非真机完成；STATE-20261003-011 / WORK-20261003-016 / CE-20261003-T008-01。无Git写入/产品build/test/device/release。

## T-2026-10-03-007：PC构建提前退出排查

- 后续日志CE-20261003-T007-02：Rust阶段完成、Flutter旧配对窗类型失败；test/0720e49已有修复，需服务器实际源码核对。补source commit/tracked changes/关键Dart SHA256及stderr消息显示，PS5.1 AST/V0；STATE-20261003-010 / WORK-20261003-014。此前“尚无日志”是原时点，不再是当前结论。

- 用户报告未完成编译即关闭窗口；已询问入口/运行方式/最后输出，尚无日志。Source test/0720e49起点干净；Baseline TUN-BL-2026-10-03-WINDOWS-BUILD。
- 确定源码缺口：CMD提前exit、PS Pause无法覆盖VS/PS启动、root解析在try外、清理失败可能跳过锁释放；不确定真实编译失败阶段。
- Delta：两脚本默认CMD统一pause和退出码，NoPause/CI、启动日志、PS追加transcript/stdout/stderr、阶段/失败行、受保护初始化和清理。驱动/native/portable合同不改。
- V0：Windows PS5.1/PS7语法解析、CMD引用/exit/标签/参数、文档/diff；build/test/driver/download/Git none。Formal handoff，见WINDOWS_BUILD_EXIT_FIX_TASK/BUILD_GUIDE；CE-20261003-T007-01；STATE-20261003-009 / WORK-20261003-013。

## T-2026-10-03-006：ADB配对弹窗类型编译修复

- Source：test/da2923e干净起点；Baseline TUN-BL-2026-10-03-ADB；用户服务器Flutter3.24.5类型编译失败，APK未输出。
- Fix：DialogBuilder要求CustomAlertDialog，使用私有子类override build返回原StatefulWidget；保留全窗口LayoutBuilder/拖动与FFI overlay关闭、controller/worker取消；通用common.dart/native/权限不改。
- Evidence：V0类型/词法/引用/文档/diff；正式重编、FLT-01/02/04/07与ADBP-11待验。无Git/build/test/device/sign/release。
- Handoff：docs/plans/ADB_PAIRING_DIALOG_BUILD_FIX_TASK.md；CE-20261003-T006-01；STATE-20261003-007 / WORK-20261003-011；无新ADR。

## T-2026-10-03-005：ADB授权与生产投屏随会话持续

- Authority：当前用户明确要求授权连接后保持，直到断连/手动撤销/切普通投屏；C2源码/文档，非Git/构建/测试执行授权。
- Source：test/3c22a25 + T003/T004工作树保留；Baseline TUN-BL-2026-10-03-ADB。
- Delta：移除600s scopes期限、PC授权timer，状态改consentActive/consentLifetime；stop/结束/失败清grant，重放不恢复；生产helper duration0，无一小时期限，protocol/HMAC/manifest/Gradle3，旧helper拒绝；关共享保留helper，P0有限诊断不变。
- Verification：V0静态源码/词法/引用/版本/Python AST/文档/diff；新增协议边界测试源码NOT_RUN。正式服务器同批PC/APK/helper，健康超过10/30分钟及1小时、暂停/恢复、退出/撤销/断线/故障/旧operationId验ADBP-08/09/10。
- Handoff/T6：docs/plans/ADB_SESSION_LIFETIME_TASK.md；ADR-0017/D-019；CE-20261003-T005-01；STATE-20261003-005 / WORK-20261003-009。Git/build/device/sign/release none。

## T-2026-10-03-004：远程ADB配对与界面迁移

- Request/authority：项目用户明确五项修改及PC输入无线调试码/端口立即发APK；C2代码/政策/文档，明确三项Android UI移除。无Git写入、构建/测试/codegen、依赖安装/二进制执行、设备、签名/发布。
- Source：test/3c22a25f0151f0dea408c0c03c6e818cdf31fe98 + 保留T003 Windows未提交变更；Baseline TUN-BL-2026-10-03-ADB。
- Delta：PC独立pairing model/dialog，typed pair/authorize/revoke/cancel，Root端权限/secret-safe errors/异步worker/scopes，localhost daemon启动与独立connect+nonce UID2000；取消确认、lategrant防护、权限恢复、home fallback；右上状态/授权倒计时。
- Removed：flutter/lib/mobile/pages/adb_page.dart、mobile/widgets/adb_mirror_probe_card.dart、adb_remote_consent_card.dart；Dart引用0；native suite/prototype/helper保留，恢复点为HEAD。
- Evidence：九个关键Dart/Kotlin/Rust文件词法闭合、引用/文档链接/diff V0与专项交叉review；不等于编译通过。新Rust负例测试源码NOT_RUN；Android11—16及16KiB device runtime NOT_RUN。
- Decision：D-018 / ADR-0016限定替代phone-local consent。Formal handoff/T6；文档ADB_REMOTE_PAIRING_TASK/IMPLEMENTATION_GUIDE，ADBP-01—12服务器配套构建测试。
- 《编译验证需求》：Android Linux源码根 ./build.sh 1（或2），Windows x64源码根 new-build.cmd；同批PC/APK/helper，按ADBP/ADBM验收，记录ROM/ABI/页大小与产物hash，不回传配对码/key。
- Git/release：none。Event CE-20261003-T004-01；STATE-20261003-004 / WORK-20261003-007；回滚须UI/parser/授权政策一起恢复，不覆盖T003。

## T-2026-10-03-003：Windows 构建链修复

- 请求：用户报告产物整理/驱动下载/自解压失败，要求对照build.cmd与RustDesk官方流程完善源码。用户未提供本次Windows服务器错误日志；断点以源码审查记录。
- Source：test/3c22a25f0151f0dea408c0c03c6e818cdf31fe98，起点干净；保留T002 Android helper编译修复。Baseline：TUN-BL-2026-10-03-WINDOWS-BUILD。
- Delta：统一CMD→PS流程、.info默认值/日志/新staging/OS文件锁；Cargo实际DLL路径与CMake；官方driver供应锁/有限下载/安全解压/receipt/缺失WindowInjection源码构建；独立manifest与packer真实artifact+payload完整核验。
- Evidence：V0源码、Python AST/JSON/PowerShell语法/diff；tests/windows_build_contract_test.py新增但未执行。完整构建/asset下载/解压运行/驱动及V1—V3仍NOT_RUN。
- 状态：源码交付/服务器验证handoff；执行new-build.cmd，已有Release可-PackageOnly。Guide：docs/plans/WINDOWS_BUILD_GUIDE.md；Event：CE-20261003-T003-01；State：STATE-20261003-003。
- 权限：本轮只代码/构建配方/文档/V0，没有Git写入、构建/测试、二进制获取、设备、签名、安装或发布。ADR评估无新增产品决定。

## T-2026-10-03-002：ADB helper Lambda 编译入口修复

- 请求及授权：用户提供服务器 `javac` 日志，明确要求修复后提交GitHub，提交说明“修复编译脚本”；目标沿用当前 `origin/test`。本侧会话独立执行，不续做主线程ADB功能。
- 起点：`test/c492b12df4fdf8eca0f64a85d4d16ebab5e4fa2c`，工作区干净；基线引用 `TUN-BL-2026-10-03-ADB`。
- 原因：helper Java源码使用Lambda，独立javac的bootclasspath仅有android.jar，缺少SDK build-tools的LambdaMetafactory编译stub。
- 修改：`android-helper/build_helper.py` 加入同一build-tools的core-lambda-stubs.jar，校验JAR入口、跨平台类路径分隔、工具哈希与输入变动检查；同步helper README。
- 验证及边界：Python AST和类路径/来源记录静态检查、git diff空白检查；未运行helper/APK编译或设备测试。正式服务器需重新执行原build.sh入口确认javac及D8成功。
- Git：用户授权stage/commit/普通push；远端结果以当前提交和origin/test核对。无架构决定、版本、签名或发布变更。

## T-2026-10-03-001 后续：首次测试源码云端交付

- 用户明确授权提交当前ADB源码与文档并推送 `origin/test`，提交说明为“ADB版本首次测试”。
- 目标：`https://github.com/adb-shell/Tunnel.git` 的 `refs/heads/test`；提交前远端和本地起点均为 `bc50fe5b2061970e4e88ff58bbd76c7827de1159`。
- 操作范围：stage/commit/普通push；结果核验当前commit与远端ref。服务器编译、真机验收及发布不在此次操作范围，V1—V5仍NOT_RUN。

## T-2026-10-03-001：ADB全链源码与首轮故障修复

- 状态：source delivered / handoff至服务器验证；未宣称P6验收通过。
- 用户明确补充正式Android/Windows构建均不在本地，LADB为native来源，OnePlus ACE6T/iQOO Neo9均Android16；要求直接完成P0—P6源码后测试。
- 开始：test/bc50fe5b2061970e4e88ff58bbd76c7827de1159，干净工作树；全程既有worktree。
- 源码：本地ADB配对/连接/互斥、LADB锁定供应、helper和H264/JNI/relay、PC事务切换/输入/顶栏、手机scoped consent、截图/节点/显示、A11y复查/暂停/自身关闭/设置、Windows portable。
- 文档：ADB_REMOTE_IMPLEMENTATION_GUIDE、任务/基线、AI_ENGINEERING相关领域、ADR0014/D017、External Registry、测试矩阵和全局记忆。
- 证据：V0跨域review，配置及Python AST、diff静态检查；build/test/analyze/codegen/device NOT_RUN。未取得binary/已生成安装包，不声称ROM兼容实测。
- 限制：ADB防触摸不能保证保留注入故明确拒绝；安全A11y重新启用需要手机settings确认；secure画面不绕过；单owner。指南记录其余输入/设备验证边界。
- 下一步：用户在正式服务器配套构建PC/APK/helper，按指南双Android16与Windows自解压/驱动验收，带版本/hash/脱敏日志反馈。无需修改原checkout或自动同步云端。
- Git/发布：未stage/commit/push；未版本/签名/服务变更。CE-20261003-T001-01；STATE-20261003-001。

## T-2026-10-02-004：Tunnel 产品身份迁移

- Timestamp：2026-10-02 Asia/Shanghai；Owner：tunnel-master。
- Authority：当前用户明确要求产品/项目旧名全局迁移为 Tunnel/tunnel，中文隧道，Android com.tunnel.app，并暂缓后续ADB实施；必要文件/目录移动和机械生成文件命名同步属于该指令范围。
- Baseline：test/532637a7 + T002/T003未提交工作；新增 TUN-BL-2026-10-02-IDENTITY，不把本次工作冒充既有commit。
- Source changes：跨语言产品身份、原生库/exports、Android namespace/package及源码路径、helper identity、状态事件、broker示例、构建安装产品元数据、工程文档和9个Skills路径。见任务与当前路径清单。
- Delegate review：Android/JNI/MethodChannel/P0；Rust/portable/非主平台loader与打包；Flutter/docs/外部契约。限定子任务曾授权agent直接修改，主agent复核。
- Generated：现存Dart桥接机械改名，生成入口锁定Tunnel类名；没有执行codegen。Windows证书/外部驱动身份未改。
- Moves：仅当前worktree内部必要品牌/包/Skill/报告路径迁移，先验证绝对边界与目标不存在；没有删除用户文件或清理目录。
- Evidence：V0静态检索/契约对照/链接及配置检查；构建、单元测试、分析器、设备、发布和签名均未运行。
- Historical normalization：按用户指令改历史文档名称，保留ID/日期/hash含义；不能将规范化文档当历史逐字快照。
- Handoff：源码改名交付；新旧应用独立，需新授权及配套artifact/服务；T003仍paused/P0未验证。无Git写、网络部署或证书轮换。
- Decision / Event：D-016、ADR-0015、CE-20261002-T004-01；Task：`docs/plans/TUNNEL_IDENTITY_MIGRATION_TASK.md`。

最后更新：2026-07-12

> 本文件记录 AI 参与的工程任务、授权边界和验证结果。它不是产品 changelog，也不替代 Git history。

## T-2026-07-12-001：项目资产接管

- 状态：completed（repository-side asset takeover；external infrastructure 与正式构建仍待后续授权）。
- 请求者：项目 owner。
- 角色：Tunnel Principal Engineer。
- 授权范围：读取/分析源码和历史；创建文档、规则、memory、skills。
- 禁止范围：业务代码修改；删除；build/test；commit/push/merge/rebase；version bump；release/upload。
- 源码基线：`HEAD 77062b4`，分支 `main`。
- 初始工作树：clean。
- 执行内容：
  - 扫描 Rust workspace、Flutter、Android、Windows、network、HTTP/API。
  - 审计全部 39 个 tracked Markdown 与高信号源码注释。
  - 回溯本地 59 个 commits；确认导入前历史缺口。
  - 建立 `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`—`11_ROADMAP.md`、审计/迁移/外部技能/接管报告。
  - 建立 `.codex` 长期 memory 和 `.agents/skills` 专属工程技能。
- 重要发现：
  - public repository/history credential exposure。
  - Android raw frame ownership、`static mut`、permission 和 ZEGO consent 风险。
  - transport/crypto/local storage 风险。
  - Windows privacy/injection/Amyuni 高权限面。
  - backend/hbbs/hbbr/DB 不在仓库。
  - ignored binary/driver assets 破坏 clean-clone reproducibility。
- 变更类型：仅文档、memory、skills 与入口规则；无业务源码修改。
- 验证：交付清单、Markdown code fence、活动源码路径、敏感字面值复制、Skill schema/metadata 与三类纸面 forward-test 全部通过；独立只读验收未发现剩余明确问题。未执行项目 build/test。
- 正式验证：见 `docs/AI_ENGINEERING/09_DEBUG_SYSTEM.md`《编译验证需求》。
- Git：未 stage、commit、push、merge 或 rebase。
- 删除/发布：无。

## T-2026-07-12-002：AI 工程体系强化

- 状态：completed（V0 repository-side governance strengthening；正式环境与外部资产仍待 owner 接管）。
- 请求者/批准者：项目 owner。
- 源码基线与 dirty state：`main` / `HEAD 77062b4`；开始时已有上一轮接管产生的文档、memory 和 Skill changes，均保留并在其上增量修改。
- 授权范围：创建入口、任务协议、开发流程、外部资产登记；完善 AI rules；审查并完善八个 Tunnel Skills；同步文档和长期记忆。
- 禁止/非目标：业务代码；build/test/analyze/codegen；Git 写入；删除/移动；版本、签名、打包、上传、部署、发布；credential 使用或验证。
- 方案与决定：建立入口 → 权限 → T0—T8 → domain truth/Skill → workflow → verification/memory 的固定链；外部资产使用统一 registry；见 `D-012`。
- 修改文件：根入口/流程/registry，新 Task Protocol 和强化报告，`.codex` rules/memory/logs，八个 Skills，以及 README/AGENTS/CLAUDE/工程索引中的入口指针。
- 安全/隐私/license：registry 不保存 secret、private endpoint 或 PII；记录 credential 类型与处置状态，不复述值；登记 external binary/driver、signing、AGPL/source-offer blockers。
- 验证：V0 静态检查；八个 Skill 通过官方 validator 规则和 metadata 检查；跨域 protobuf→Rust→Android→Flutter 前向案例正确触发 Master/Network/domain/Security/Release 路由及 C2/C3；入口/引用/资产分类复核。未执行项目 build/test。
- 《编译验证需求》：本轮没有业务或 build-system 行为变更，不申请编译；正式环境验证继续按 `09_DEBUG_SYSTEM.md` 管理。
- 回滚：仅通过用户批准的文档 patch 回退；不删除旧文档、不重写 Git history。
- Git/release actions：无。
- Related decisions/docs：`D-012`、`docs/AI_ENGINEERING/TUNNEL_AI_ENGINEERING_STRENGTHENING_REPORT.md`。

## T-2026-07-12-003：AI 工程体系最终封版

- 状态：completed（repository-side governance seal；formal build/runtime/external infrastructure evidence remains open）。
- 请求者/批准者：项目 owner。
- 源码基线与 dirty state：`main` / `HEAD 77062b4`；开始时已有前两阶段的文档、memory、Skill changes，全部保留并增量扩展。
- 授权范围：创建 ADR、BASELINE、task template、test matrix；安全整合五项 Superpowers-style reasoning；更新入口、规则、memory 和报告。
- 禁止/非目标：业务代码；build/test/analyze/codegen；Git 写入；删除/移动；版本、签名、打包、上传、部署、发布；credential/production 操作；外部 Superpowers 安装。
- 方案与决定：见 D-013、ADR-0000 和 ADR-0013。
- 修改类型：仅 Markdown/YAML 文档、规则、memory 和 project Skill metadata；无业务源码。
- 主要交付：`docs/ADR/`、`docs/BASELINE/`、`TASK_TEMPLATE.md`、`TEST_MATRIX.md`、`SAFE_SUPERPOWERS_PROFILE.md`、`tunnel-superpowers-safe`。
- 安全/隐私/license：Superpowers 只作官方只读参考，未安装或执行；adapter allowlist 仅 brainstorming/planning/debugging/verification/review，commit/push/release hard-denied；无 secret/PII 值进入新文档。
- 验证：V0 only；required files、ADR index/status、baseline anchors、Skill schema/metadata、Markdown fences/local links、sensitive patterns、delete/scope 和 diff whitespace checks。`tunnel-superpowers-safe` 的 PC→Android Remote ADB 前向案例在同时收到实现、测试、commit、push、release 请求时仍保持 C0/T4，只输出 brainstorming/planning artifact，并在所有 mutation、project command、Git write、external/production 与 release 前停止。未执行项目 build/test。
- 正式验证：未来任务从 `TEST_MATRIX.md` 选 case 并按 `TASK_TEMPLATE.md` 输出《编译验证需求》；本次无业务/runtime 变化，不申请编译。
- 回滚：通过后续 patch/superseding ADR 调整；不删除 ADR、baseline 或历史记录。
- Git/release actions：无。
- Related decisions/docs：D-013、ADR-0000、ADR-0013、`docs/AI_ENGINEERING/TUNNEL_AI_ENGINEERING_FINAL_SEAL_REPORT.md`。

## T-2026-07-12-004：AI 全局记忆增强

- 状态：completed（repository-owned session memory established；external/runtime/release blockers unchanged）。
- 请求者/批准者：项目 owner。
- 源码基线与 dirty state：`main` / `HEAD 77062b4` / `CS-BL-2026-07-12-77062b4`；开始时已有前三阶段未提交的文档、memory 和 Skill changes，全部保留。
- 授权范围：创建 `PROJECT_STATE`、`CURRENT_WORK`、`CHANGELOG_AI`、`CHANGE_EVENT_LOG`、`SESSION_START_PROTOCOL`；更新项目入口和 Task Protocol；同步必要的 Task Template、Decision/Task history。
- 禁止/非目标：业务代码；Git write；build/test/analyze/codegen；删除/移动；版本、签名、打包、上传、部署、发布；production/credential 操作。
- 方案与决定：五文件职责分离；Current Work 支持 0..N 并行 Task；全局记忆恢复与 T0—T8 任务激活分阶段；历史授权不继承；见 D-014。
- 修改文件：五个新 `.codex` memory 文件；`PROJECT_START_HERE.md`、`.codex/AI_RULES.md`、`AI_TASK_EXECUTION_PROTOCOL.md`、`TASK_TEMPLATE.md`、`DECISION_LOG.md`、`TASK_HISTORY.md`。
- 安全/隐私：状态与日志只保存 repository-relative path、Task/Event/ADR/Asset ID 和证据标签；不保存 secret、生产地址、设备标识、PII、绝对用户路径、prompt/transcript 或完整 diff。
- 验证：V0 required-file、8+8 order、protocol sync、Task/Event linkage、unique Event ID、Markdown fence/table/local-link、sensitive pattern、scope/delete 和 diff whitespace checks；独立无聊天上下文的新会话成功恢复 Baseline、State、Current Work、Decision、Skill、权限上限和下一停止门。首次演练发现并修复两个顺序歧义，回归结果 PASS。
- 《编译验证需求》：N/A；仅治理文档变更，无业务/runtime/build-system 行为变化，且本任务明确禁止编译。
- 回滚：通过后续获准的文档 patch / governance decision 修正；append-only Changelog/Event/Decision/Task history 不删除。
- Git/build/delete/version/release actions：none。
- Change Events：`CE-20260712-T004-01`—`CE-20260712-T004-06`。
- Related decisions/docs：D-014、ADR-0011、ADR-0012、ADR-0013、`.codex/SESSION_START_PROTOCOL.md`。

## T-2026-10-02-001：源码复核与接管文档整理

- 状态：completed — 本轮repository inventory、关键链V0接管与文档整理；全系统/运行/发布未验收。
- 请求者/批准者：当前项目用户；明确要求深入接管、修订项目文档/地图/记忆并允许多agent。
- Source：detached HEAD `5cee6921ec10971bb4654bc010f9328d7f70d02b`，initial clean；新Baseline `CS-BL-2026-10-02-5cee692`。旧`77062b4`不存在，本地非shallow且单root，不猜测历史改变原因。
- 授权：C0本地调查+C1文档；业务实现留待后续具体需求；未继承旧授权。
- 方案：沿用既有治理/00—11；三个agent独占领域文档，主agent整合全仓库存/地图/记忆并做第二轮交叉复核。
- 实际变化：8份新文档（Task、接管索引、文档登记、三个领域审计、12功能地图、新Baseline）；既有canonical、入口、historical补注、External Registry、TEST_MATRIX边界与memory更新。全部Markdown，逐项路径见CE-20261002-T001-02。
- 关键纠偏：当前Git/库存与旧快照分开；terminal已有进程内persistence；Android开/关/refresh及JNI状态分层；API双传输/先写状态；broker既有控制与缺口；native资产MISSING；build.py参数与workflow_call。
- 安全：保留既有风险并补SEC-017认证前PortForward connect、SEC-018敏感日志、SEC-019terminal owner条件路径；没有漏洞复现或credential有效性测试。新文档不含secret、生产endpoint或用户绝对路径。
- 验证：源码锚点/guard/owner检查；3域交叉复核；9个blob hashes由独立agent重算一致；文档local links/fences/literal paths、diff whitespace、仅Markdown范围、无删除/staging、memory IDs复核。V0发现的引用/语义/尾随空格已校正。
- 《编译验证需求》：本轮仅文档不需编译；后续正式命令/脚本副作用/环境/cases登记在本轮README及09，V1—V5均NOT_RUN。
- 未覆盖：全源码逐行、Linux/macOS/iOS/Web完整运行、所有widget/codec/unsafe路径、仓外infra/DB、正式签名产物与发布。
- Rollback：后续获准文档patch修正；旧历史/ADR/Baseline保留，未移动删除。
- Decision / ADR：reviewed-no-change；N/A — no decision delta。External Registry已按当前存在性更新，未取得资产。
- Global memory：Project State、Project/Architecture Memory、Task/Changelog/Event已同步，Current Work最后关闭；本轮记录替代旧snapshot的当前性，不抹去旧事件。
- Git/build/test/analyze/codegen/device/delete/move/version/sign/package/external/release actions：none。
- Events：`CE-20261002-T001-01`、`CE-20261002-T001-02`；交付`docs/AI_ENGINEERING/audits/2026-10-02/README.md`。

## 任务记录规则

每个未来任务必须记录：

- request/owner/explicit authority。
- source baseline 与 initial dirty state。
- problem statement 和 affected domains。
- assumptions 与 out-of-scope。
- files changed，不覆盖用户已有改动。
- security/privacy/license impact。
- validation performed 与未执行项。
- build requirement/result。
- rollback。
- decision log references。
- Git/release 动作只有明确批准后记录。

## T-2026-10-02-002：远程 ADB 投屏规划

- 状态：completed — C1方案/教程交付；业务实现not-started。
- 请求者：项目用户；要求先方案、后续修改，并选择以后在独立worktree开发。
- 基线：test/532637a，初始clean；CS-BL-2026-10-02-5cee692业务源码未变。
- 授权：只读源码/公开官方资料、方案和必要文档memory；不改业务/配置/协议/依赖/脚本，不执行Git写入/build/test/device/release。
- 方案：本机ADB→固定版本shell helper→APK二进制入口→原relay/video→PC；typed scopes/lease、真实capability、唯一mode actor、输入/画面barrier、无障碍pause与disable分离。
- Review：三个领域只读源码审查+二次交叉复核；修正切源时序、设置无CAS、pause回调、source teardown、订阅竞争、actualFrameSource与override语义。
- 交付：主方案、任务、proposed ADR和导航/registry/memory共14份Markdown，精确路径见CE-20261002-T002-02。
- Verification：V0本地引用/围栏/编号/scope/diff及证据审查；ADBM-01—30、P0—P6、正式编译需求已规划；V1—V5 NOT_RUN。
- Decision：D-015/ADR-0014仅proposed，ADR-0007仍accepted；不把静态方案复核当owner批准。
- Security/assets：固定helper来源/hash/版本、本地IPC鉴权、endpoint权限、最小操作；不承诺secure突破或所有ROM自动启用/防触；新增两个proposed helper资产，已有native缺口未关闭。
- Git/build/device/delete/release actions：none；原项目未改，仅worktree文档。
- Next：owner确定目标机型并确认P0具体实施；先证实纵向链路和关键provider，再完整实现。
- Record：`docs/plans/ADB_REMOTE_MIRRORING_TASK.md`、`docs/plans/ADB_REMOTE_MIRRORING_PLAN.md`。

## T-2026-10-02-003：ADB P0 本机原型源码

- 状态：handoff / T6，等待正式环境验证；P0整体未完成。
- 请求/授权：项目用户在方案后要求开始，C2分阶段源码与文档；C3未授权。
- 基线：test/532637a；CS-BL-2026-10-02-5cee692；保留T002的14份已有文档修改。
- 结果：新增本机target/UID探针、共享认证framing、受限shell H264 helper、APK staging/supervisor、默认关闭的Flutter本地诊断、独立build recipe与JUnit源码。
- 来源：scrcpy v4.1固定2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0，来源hash/LICENSE/NOTICE保存在android-helper/server；未取得native ADB或构建helper binary。
- 审查：三个子agent+主agent交叉V0，修正卡片位置、native assets目录、Activity重建计数、认证错误收尾及时间/API合同。旧Runner重连和持久shell长命令仍是P1需隔离边界。
- 实际范围：约10秒编码样本计数；decoded/rendered=false；PC顶栏、relay视频、输入、无障碍系统启停/暂停、ADB无视/节点均待后续。
- 验证：仅V0；项目build/test/analyze/codegen/device与实际decoder/render/relay未运行。不能标ADBM通过。
- 交付/命令：docs/plans/ADB_P0_VALIDATION_RUNBOOK.md、ADB_REMOTE_MIRRORING_IMPLEMENTATION_TASK.md；CE-20261002-T003-02记录完整文件清单。
- 回滚：默认flag关闭，本地取消本次helper/socket；不kill全局ADB或停止core。不自动删除源码/Git回退。
- Git/build/device/delete/sign/release：none；全部在既有worktree，原目录未改。
- 下一步：补正式工具链/native来源与测试设备，并按具体执行范围取得C3后验证；先补齐P0，不能提前启用生产remote。
- Decision：D-015/ADR-0014 staged accepted；ADR-0007 local-only边界在P0通过前保留。

## 新任务模板

```text
## T-YYYY-MM-DD-NNN：标题
- 状态：planned/in-progress/blocked/completed
- 请求者/批准者：
- 源码基线与 dirty state：
- 授权范围：
- 禁止/非目标：
- 问题与证据：
- 方案与决定：
- 修改文件：
- 安全/隐私/license：
- 验证：
- 《编译验证需求》或正式结果：
- 回滚：
- Git/release actions：
- Related decisions/docs：
```
