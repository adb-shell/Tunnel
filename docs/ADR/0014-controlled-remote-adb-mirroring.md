# ADR-0014: Controlled Remote ADB Mirroring over Tunnel Sessions

- Status：`accepted`（分阶段实施；P0验收前保持local-only运行边界）
- Record Type：`contemporaneous`
- Decision / Recorded / Last Reviewed：2026-10-02；Next Review：P0开始前。
- Decision Owner / Approvers：项目owner / 2026-10-02用户要求按已交付方案开始实施。
- Security / Release Reviewer：主agent按对应领域Skill做V0；正式执行/发布owner待指定。
- Scope：Android本地ADB启动受控helper、PC请求投屏与有限操作、采集/输入接管。
- Related Task / Decision：T-2026-10-02-002、T-2026-10-02-003 / D-015。
- Baseline：CS-BL-2026-10-02-5cee692；规划HEAD为532637a，仅后续文档差异。
- Cases / Assets：ADBM-01—30；EXT-BIN-ADB-001、EXT-SRC-ADB-HELPER-001、EXT-BIN-ADB-HELPER-001。
- Supersedes：ADR-0007仅在P0验证后、受控typed投屏范围内被限定扩展；任意shell仍local-only；当前未开放远程入口。
- Implementation / Evidence：P0 local prototype source added / V0 source and upstream review；V1—V5 NOT_RUN；P0整体未通过。

## Context / Problem

已有本机ADB配对和文本shell，没有生产远程ADB投屏、能力协议或模式事务。用户要求PC顶栏切换ADB视频、侧按钮分流及无障碍共存/管理。ADR-0007要求远程化先有新协议和威胁模型；本记录已获分阶段实施接受，不能替代具体构建/设备/发布授权。

## Decision

固定版本、有限修改的scrcpy-server作为shell helper。APK内本机ADB启动helper，APK收取编码视频，后续经Rust owned/bounded入口转入既有relay会话，PC继续现有窗口。P0源码参考v4.1/2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0，license/provenance/build recipe位于android-helper；产物尚未构建。

PC只发送typed有限操作，不接触配对secret、不直连adbd、不开放任意shell/隧道。AndroidModeCoordinator统一frame/input/provider事务；视频、节点与系统设置分别探测。完整合同见[方案](../plans/ADB_REMOTE_MIRRORING_PLAN.md)。

## Scope, Non-goals and Invariants

- 首期Windows PC、Android11+；精确ROM/ABI由P0证据确定，一个ADB视频owner。
- endpoint auth+scope+lease+generation；保持strict relay，补齐新权限路径依赖的安全gate。
- pause与系统disable分开；只有替代frame/input已接管才允许远程暂停/关闭。
- 不改其他无障碍服务；无安全单组件更新机制时，开启返回LOCAL_ACTION_REQUIRED，不用整体settings写冒充并发安全。
- 节点provider用DONT_SUPPRESS并处理UiAutomation独占；无视=截图、穿透=节点重绘，不承诺secure/DRM/锁屏突破。
- 不隐式重开MediaProjection。ADB/core/relay生命周期与无障碍service分离。
- 不包含root、远程任意shell、跨用户、系统音频或应用管理。

## Alternatives Considered

| Option | Benefit | Cost / Disposition |
|---|---|---|
| 有限helper + 原relay | 复用Android机制和network/render | 新协议/owner/生命周期；推荐 |
| PC裸ADB/通用隧道 | 原型简单 | 权限面及网络边界过大；不采用 |
| screenrecord/screencap循环 | 诊断方便 | 长稳、交互和延迟不足；不作主线 |
| 外装Shizuku必需依赖 | 权限代理 | 新用户依赖；后续可选 |

## Consequences

正面：ADB视频和输入独立于本App无障碍，可共存、可撤销、按能力升级。

成本：维护hidden API、helper产物、JNI和PC切源。raw像素算法不自动适用H264直通；防触/节点/系统设置是分项ROM能力，需分别验收。

## Compatibility, Protocol, Data and Migration

新增typed messages、video/input epoch，不复用旧mouse/terminal提权。旧peer保留普通模式；新功能默认关闭。decoder/capture变化用同一barrier，实际render后启用新input；generated内容仅在批准正式环境生成。

## Security, Privacy, Compliance and License

手机无线配对不是PC权限。remote scopes本机同意、可见可撤销；helper IPC认证、受信任staging、stdin bootstrap，不记录secret/节点/画面。任意文本shell保持local-only，只管理自身component。双侧watchdog/lease退出时release输入并恢复本feature改动的显示状态。

第三方源码/NOTICE/SBOM由准入审查记录；本轮未下载或分发可执行产物。

## Build, Operations, Release and External Assets

native ADB来源闭环仍缺；helper受限源码已引入，binary仍NOT_BUILT。正式Android/Windows环境及脚本副作用按08_BUILD_SYSTEM确认；本ADR不授权Git、构建、真机或发布。

## Verification Plan and Evidence

| Level | Cases / Environment | Result |
|---|---|---|
| V0 | 源码/官方实现/三领域交叉审查 | 文档验证；不代表运行 |
| V1—V2 | provider/protocol/decoder fixtures、正式Android/Windows/helper构建 | NOT_RUN |
| V3—V4 | ADBM-01—30、目标ROM/ABI/Windows、隔离relay | NOT_RUN |
| V5 | 未来另行授权运营观察 | NOT_RUN |

## Rollback / Reversal / Kill Switch

激活前保留旧source；激活后回滚使用新epoch/barrier，不能恢复旧epoch。撤销ADB lease后退出helper，不kill全局ADB。MP已失效时本机重新授权，不声称无提示恢复。

## Approval Record

| Date | Actor | Decision | Scope |
|---|---|---|---|
| 2026-10-02 | 项目用户 | 请求制定方案 | C0/C1；尚未批准ADR或业务/构建/设备执行 |
| 2026-10-02 | 项目用户 | 方案交付后要求开始实施 | C2分阶段源码实施；P0 gate不取消；无构建/真机/Git/发布授权 |

## References and Amendment History

- [ADR-0007](0007-keep-android-adb-local.md)：当前仍accepted。
- [任务](../plans/ADB_REMOTE_MIRRORING_TASK.md)、[方案](../plans/ADB_REMOTE_MIRRORING_PLAN.md)。
- 2026-10-02：首次proposed记录，不改变当前业务行为。
- 2026-10-02：用户要求开始；接受分阶段实施。T003加入默认关闭的本机采集诊断源码，remote feature仍未开放；见[P0交接](../plans/ADB_P0_VALIDATION_RUNBOOK.md)。
