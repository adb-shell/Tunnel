# ADR-0016：PC 无线 ADB 配对与会话授权

> 2026-10-03 T005：[ADR-0017](0017-adb-session-lifetime.md)替代本文600秒期限。当前授权随连接持续，断连/撤销/退出ADB或故障结束；下文期限保留为当时决定，其他政策仍有效。

- Status：accepted；2026-10-03 用户明确要求移除 Android ADB 界面、PC 输入端口与码立即发给 APK 配对。
- Record Type：contemporaneous；Task T-2026-10-03-004；Decision D-018。
- Baseline：TUN-BL-2026-10-03-ADB；Implementation source/V0；build/device/integration NOT_RUN。
- 限定替代 ADR-0014 中 PC 不接触配对码、必须在手机 ADB 页面同意的部分；其视频/input 事务、helper、endpoint 权限、relay 及非任意 shell 边界继续有效。ADR-0007 的 local-only 操作边界相应扩大到本记录的 typed pair/authorize/revoke。

## 决定与授权来源

PC 的可拖动弹窗输入本机 pairing port 与六位 code，经现有加密已认证、具有视频订阅与输入权限的 Tunnel 会话发送给 APK。本地后台 worker 用 APK 打包的 libadb.so 执行配对、发现独立连接端口、连接并 fresh 验证本机 nonce/uid2000。PC 不直连手机 adbd，也不需要和手机在同一局域网；手机仍需系统无线调试支持及可用网络。

当前产品政策允许该已授权控制角色显式请求 pair 或 authorize，并在验证成功后取得当前 conn 的 600 秒 scopes：video/input/accessibility/display/snapshot/hierarchy/overlay。PC 提交按钮明示用途与有效期；endpoint 不能证明一个 UI 对话框实际被点击，安全授权来自加密/认证/输入与视频权限及本政策，不能继续声称 phone-local consent。pair 仍需要有效系统码，authorize 只复用系统已信任的 key；历史 paired 标记、status 查询与普通连接不会自动授予 scopes。

## 安全与生命周期

- 严格 typed payload：端口 1—65535、code 六位数字；不接受主机、serial、command、任意 shell。
- 只有当前有效 conn/op 的 VERIFIED 且本次 error 为空、fresh probe 成功才 grant；连接取消/断开/撤权使迟到结果无效。
- code 仅短暂内存与有界 stdin；不进 argv、日志、偏好、回包和 Runtime completed signature 缓存；JVM/Dart 字符串不能声称可物理清零。
- pair/authorize 的结果与视频 epoch/phase 隔离；单后台 worker、45 秒操作预算、PC 90 秒截止、有限重试与显式终态。ADB helper 活跃时拒绝重新配对/授权，不杀 daemon、不停止普通共享。
- 当前连接可以 PC 撤销授权；断线/端权限丢失/TTL 撤销，恢复本模块的输入/显示/无障碍暂停。系统无线调试关闭或忘记 key 撤销系统信任；无任意远程 shell。
- fresh uid2000 不等于录屏、节点或输入 provider 支持；helper capability、首帧与 rendered ACK 仍是提交条件。

## 兼容、风险与验证

删除 AdbPage 及其两个诊断/consent widget，保留 APK 内 Manager/Runner/probe/helper/有限 shell。PC 菜单仅动作，状态迁到 TunnelStatusMonitor。旧客户端/旧 APK 不具备新操作；必须配套重建，超时与未知能力不能视为成功。

系统配对弹窗关闭会令端口和码失效；NSD 受网络/ROM影响，提供独立 connectPort 备用。实现最低 SDK30，无 Android11—16 分支硬阻断；不同原生版本、OEM、ABI、16KiB 页设备仍需真实验证。固定 LADB prebuilt 不是已证明的 AOSP 可复现 native 构建，不能保证全部设备。

回滚必须同时恢复旧 phone-local consent/UI 和旧 parser；不通过回退授权检查强行兼容。测试覆盖见 TEST_MATRIX ADBP-01—12 与 ADBM；本任务不授权 Git/构建/设备/签名/发布。

## 主要备选

保留手机页会违背当前产品要求；无条件从 pairedBefore 自动 grant 会把系统历史信任误当所有远控连接权限；直接向 PC 开放 ADB/tcp/shell 扩大边界，均不采用。系统页仍负责生成码和删除配对，不由 APK 伪造。
