# ADB 授权改为会话生命周期

- Task T-2026-10-03-005；tunnel-master / Android / Flutter / Security；Baseline TUN-BL-2026-10-03-ADB；Source test / 3c22a25 + T003/T004 dirty patch保留。
- T6 handoff；权限风险 high；C2由当前用户明确要求取消10分钟期限并在断连/手动撤销/切回普通模式结束提供；无Git/build/test/codegen/device/sign/release授权。
- State STATE-20261003-005；Registry WORK-20261003-009；Event CE-20261003-T005-01；D-019 / ADR-0017；ADBP-08/09/10与ADBM。

## T0—T4 范围、设计与批准

用户要求连接健康时持续ADB，移除授权600秒倒计时。Android以当前conn/scopes是否存在判断授权，新增consentActive/consentLifetime=session，不使用无限大时间戳。PC移除授权timer，状态显示“本次会话有效”；停止ADB/切回普通模式同时撤销，下一次显式连接授权复用原配对key。断线/撤权/撤销/核心退出或helper失败清理scopes；保留心跳和操作超时用于识别故障，不将它们变成固定使用期限。关ADB采集/截图覆盖只是内部模式暂停，不等于切回普通共享。

影响：TunnelAdbRuntime、Activity兼容grant入口、AndroidModeModel、PC菜单/弹窗/TunnelStatusMonitor与当前指南/记忆/测试矩阵。额外发现生产helper Bootstrap硬编码3600秒，按同一用户目标改为SESSION_DURATION=0；wire/HMAC/manifest/Gradle同步protocol3。P0有限诊断保留，原生suite、Rustvideo事务、LADB供应锁、Windows构建patch不改。

## T5/T6 验证、回滚与交付

本地V0已完成：10个Java/Kotlin/Dart关键文件词法闭合、build_helper Python AST、字段/引用/版本/期限源契约、文档链接与diff。新增协议测试源码覆盖零期限往返、旧version和负duration拒绝，未执行；不代表编译或设备通过。

《编译验证需求》：Linux正式源码根 ./build.sh 1（或2），Windows x64正式源码根 new-build.cmd；同批PC/APK/helper protocol3，拒绝旧helper。验证健康运行超过10/30分钟及1小时仍授权，侧按钮关共享不撤销；停止回普通、断线、撤权、revoke/helper失败后grant=false，旧operationId重放不恢复，显式重新authorize可以用已有系统key恢复。保留超时与心跳故障注入测试；记录ROM/ABI/页大小/产物hash，不回传系统码或key。

回滚仅本任务差异与相应授权文档，保留T003/T004；旧600秒政策不得从历史记录误恢复。V1—V5 NOT_RUN。
