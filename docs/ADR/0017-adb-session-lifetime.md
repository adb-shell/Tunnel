# ADR-0017：ADB 授权与投屏随当前会话持续

- Status：accepted；2026-10-03；Task T-2026-10-03-005；Decision D-019。
- Approval：当前用户要求授权连接后保持ADB，直到断连、手动撤销或切回普通投屏。
- Baseline：TUN-BL-2026-10-03-ADB；source/V0；服务器构建/设备验收 NOT_RUN。
- Supersedes：ADR-0016 的600秒期限；其控制者认证、系统配对、fresh probe、scopes、typed协议及秘密处理不变。

## 生命周期

成功 pair/authorize 后，scopes 仅属于当前已认证加密且有输入/视频权限的 conn；不设置倒计时、不持久化。状态使用 consentActive 与 consentLifetime=session。断连、撤输入/视频权限、手动 revoke、退出ADB切回普通投屏、核心退出或helper故障结束授权，并释放本实例输入与helper资源。旧operationId重放只返回结果，不能恢复已结束的授权。

生产 helper Bootstrap durationSeconds=0（SESSION_DURATION），开始采集后取消独立的一小时截止时间；有限诊断允许1—3600秒，P0仍20秒helper/10秒采样。关共享只暂停ADB采集，保留helper/control/scopes；重新开共享不需要重新配对。退出ADB则不同：清理helper/scopes，下次通过PC显式连接授权，复用系统仍信任的原key。

只取消固定使用期限。启动15/45秒、控制者心跳15秒、本机control心跳5秒、操作10秒、写阻塞2秒、未暂停视频无帧10秒、帧提交及回滚守卫仍用于取消故障会话。系统无线调试关闭、系统忘记key、进程被杀或网络故障不能被持续授权承诺覆盖。退出不删除系统pair key、不kill-server；系统ADB daemon的寿命由Android决定。

## 兼容与验证

Helper wire升为VERSION=3，HMAC域同步，构建manifest与Gradle检查同步；APK运行时按AdbWire.VERSION严格拒绝旧helper。PC/APK/helper必须在正式服务器同批重编，不能沿用protocol2的二进制或receipt。构建脚本仅允许在旧两件套经校验后安全替换，不把旧helper当作运行兼容。

V0核对源码生命周期、引用、Python AST、词法闭合、版本及文档/diff；不等于编译通过。新增协议测试源码验证duration=0往返、旧version拒绝及负duration拒绝，NOT_RUN。ADBP-09验证健康运行超过10/30分钟及1小时、暂停/恢复、撤销/断线/退出清理；ADBP-08/10验证显式重新授权和重放不恢复。正式构建及Android11—16/双Android16设备均待验。

## 回滚

仅回滚T005授权字段、PC提示/状态、helper wire/构建检查和对应文档，保留T003 Windows及T004配对变更；APK/helper/PC配套回滚，不混用协议。旧期限是否恢复须遵循当前用户决定，不能从历史ADR自动恢复。
