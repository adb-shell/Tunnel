# T-2026-10-02-002：远程 ADB 投屏方案与实施方向

## 0. Task Metadata

| Field | Value |
|---|---|
| Status / T-State | complete / T8；仅方案文档任务完成，业务未实施 |
| Requester / Product Owner | 项目用户 |
| Primary Skill | tunnel-master |
| Domain Owners | Android、Rust/Network、Flutter；三个只读协作审查 |
| Security / Release reviewer | 主 agent 按 tunnel-security-engineer / tunnel-release-engineer 约束评估 |
| Created / Updated | 2026-10-02，Asia/Shanghai |
| Source Branch / HEAD | test / 532637a7b4084ec8ebf7deddbab12982628b1c1c |
| Baseline | CS-BL-2026-10-02-5cee692；至当前 HEAD 仅 41 份 Markdown 变化，业务源码基线未改 |
| Initial dirty state | clean |
| Risk | 文档变更 low；未来 shell 权限、采集/输入切换实现 high |
| Verification | 本轮 V0；产品实施至少 V1—V4 |
| Related ADR | ADR-0003/0004/0005/0006/0007；新 ADR-0014 仅 proposed |
| Related cases | AND-01—06/08、FLT-02—07、NET-01—06、E2E-01；计划新增 ADBM 系列 |
| External assets | EXT-BIN-ADB-001；候选 shell helper 源码/产物尚未引入 |
| Recovered state / registry | STATE-20261002-001 / WORK-20261002-002；历史 Git 快照已漂移，当前 Git 事实优先 |
| Events | CE-20261002-T002-01、CE-20261002-T002-02 |

## T0. Authority and Baseline

- 用户要求先扫描、制定完整方案/教程/开发方向，后续才修改业务。C0/C1 为当前范围。
- 用户已选择以后开发在独立工作区；本轮不操作原项目目录。
- Git 合并/推送的前一任务已结束；本轮不执行 Git 写入、项目 build/test/analyze/codegen、设备命令、依赖引入、签名或发布。
- 保留所有当前代码；只写方案、proposed ADR、导航和必要 memory/registry。
- ADR-0007 仍为 accepted：远程化需要新 threat model/protocol。新 ADR 未被产品 owner 接受前不改变这个事实。
- 发现旧状态为 detached/dirty，但当前是 test/clean：可解释 drift，不是并发冲突。CURRENT_WORK 初始无活动任务。

## T1. Requirements

- 覆盖用户九项要求：本地无线调试授权、PC 请求 ADB 视频、无障碍暂停/恢复/关闭/开启、侧按钮、模块隔离、执行路由、可靠接管、PC 顶栏、双模式共存。
- “无限调试”按 Android “无线调试”理解；不承诺授权永不失效。
- 不将配对历史、普通 app shell、ADB shell 在线和投屏首帧混为同一 ready 状态。
- 不将节点重绘、截图兼容模式、受保护画面绕过混为同一功能。
- 精确机型/OS/按钮效果向用户异步征询；未回复按文档列明的首批目标假设设计，不将其写成正式支持清单。

## T2. Impact Map

| Domain | Existing entry / planned impact | Evidence |
|---|---|---|
| Android | adb Runner/Manager、MainService、AccessibilityService → transport owner/helper/provider/切换协调器 | 当前源码 + Android 审查 |
| Rust/JNI | pkg2230 raw plane → 新 owned encoded ingress；兼容 ffi.rs 必须核对 | Rust/Network 审查 |
| Protocol | message.proto、connection/video_service/client → typed operations、capability、epoch、授权与有界队列 | 当前源码 |
| Flutter | remote_toolbar、overlay、model、input_model → 模式状态、动作路由、真实结果 | Flutter 审查 |
| Windows | decoder runtime probe / source epoch reset；不变更本机采集 | 当前 decoder 源码 |
| Security | 远程 typed action → Android shell sink；本地 helper IPC 和 consent/lease/revoke | 新设计，不是已实施 |
| Build | ADB native assets + pinned helper source/build/hash/license | 当前资产缺失；新资产 proposed |
| Docs | 本任务、主方案、ADR/roadmap/test index/registry/memory | C1 授权 |

## T3. Design and Alternatives

完整设计、证据、九项要求映射、模块接口、状态机、兼容矩阵、实施阶段和教程保存在 [ADB_REMOTE_MIRRORING_PLAN.md](ADB_REMOTE_MIRRORING_PLAN.md)。本任务只保存授权和交付记录。

首选：Android 本地 ADB 启动受控 shell helper，APK 收取编码视频后复用现有 Tunnel relay session；PC 不直连 Android 的 adbd。旧文本 shell、shell screenshot loop、PC 裸 ADB 隧道不是主视频方案。

## T4. Confirmation

文档由当前请求授权。业务实现、引入第三方源码/产物、项目编译/真机/集成执行留给后续精确任务。本轮不索取重复确认，不提前执行。

## T5. Change Record

实际文件与最终差异列在 CE-20261002-T002-02：主方案/本任务/proposed ADR及相关导航、资产、验收和memory共14份Markdown。不修改generated/protobuf/business/build files。

三领域交叉复核后修正：候选解码与呈现分离、输入barrier/rollback新epoch、订阅lease原子化、帧源准确命名、baseMode/override规则、pause代次和迟到回调、SWITCH_COMMIT禁止隐式ignore、bootstrap先后边界、无CAS系统设置不保证并发安全。三组再次V0复核均无剩余设计阻断，未将其当成产品运行通过。

## T6. Verification

- V0：当前源码锚点、官方技术资料、跨域审查、文档链接/围栏/差异范围检查；30个ADBM编号唯一；scope与敏感值只作静态检查。
- V1—V5：NOT_RUN；本轮不具备产品可用性/所有 ROM 兼容证明。
- 必须把可实现的架构路线与尚未通过的设备验收分开，不用“100%支持”替代验证。

## T7. Documentation / Memory Assessment

- Canonical 当前实现不改写成未来功能；新方案独立放在 docs/plans，roadmap 提供 proposed 导航。
- ADR-0014 / D-015 为 proposed；ADR-0007 不静默废止。
- 固定 Baseline 不重写；task/memory 记录当前 HEAD。
- TEST_MATRIX 增加 proposed suite 索引，外部资产登记候选 helper，不虚构 binary 已到位。
- 完成时同步 Project State / Current Work / Changelog / Task History / Change Events / Project Memory。

## T8. Handoff

- Outcome：九项要求已映射到完整合同，P0—P6和实现后教程完成；文档任务关闭，架构仍proposed。
- Authority/result：C1；本轮无Git写入、业务变更、项目构建/测试、设备操作、删除或发布，原项目目录未改。
- Memory sync：State/Current Work/Changelog/Task History/Change Event/Project Memory已更新；D-015/ADR-0014为proposed，D-014和ADR-0007接受状态不变。
- Baseline/domain truth：固定baseline不改；当前架构保持local-only事实，roadmap仅加提案导航。External Registry登记未来helper资产；TEST_MATRIX加ADBM索引。
- Remaining：首批机型/OS由owner补充；已安装APK的native资产来源、helper正式准入、P0及V1—V4证据待后续；未保证所有ROM、自动系统启用或防触等价性。
- Next：用户确认具体实施范围后从P0开始；构建/真机/集成命令按实际环境分别执行授权流程，不在本轮提前运行。
- Rollback：仅文档变更，可在后续授权任务修订；没有runtime迁移需回滚。
