# Tunnel 验证清单

静态检查、编译成功与实机可用分别记录；未执行项保持“未验证”。不要求为每次任务新建报告，结果直接随交付说明或相关指南更新。

| 范围 | 必须观察的结果 |
|---|---|
| Android 编译 | Linux 构建机执行 `./build.sh 1` / `2`；APK 包名、各 ABI 的 native ADB 与 helper 资产正确 |
| Windows 编译 | 正式服务器执行 `new-build.cmd`；Cargo locked、DLL、Flutter runner、驱动、portable 全流程成功 |
| Windows 自解压 | 在独立测试目录启动生成 EXE，核对 payload、驱动与 DLL；失败有可见提示和非零退出，路径含空格也能启动 |
| ADB 首次配对 | 手机保持配对窗口，PC 输入端口和 6 位码；收到 ACK、配对完成、连接完成、真实 shell UID 2000 |
| ADB 失败恢复 | 错码、过期端口、未开无线调试、断网、取消和超时有明确反馈；随后正确输入可重试 |
| ADB 并存 | LADB 与 Tunnel 使用各自 daemon/key，不互相终止；应用重启后用自身 key 重新连接 |
| ADB 投屏 | 普通 → ADB → 普通连续切换，PC 真正显示首帧后输入生效，旋转/锁屏/恢复无资源冲突 |
| ADB 生命周期 | 超过 10 分钟仍可用；断开/撤销/退出 ADB 释放会话；关共享仅暂停视频 |
| 无障碍 | OnePlus / iQOO Android 16 开启后返回仍保持实际状态；暂停/恢复不破坏 ADB |
| 侧按钮 | 普通与 ADB 模式分别验证共享、无视、穿透等支持项；不支持项明确反馈，不伪报成功 |
| 普通远控回归 | 首帧、输入、关闭/打开共享、断线重连，不隐式发起 MediaProjection 授权或切无视 |
| 协议与授权 | 未授权/已断开的会话不能操作 ADB；错误字段、重复/迟到请求不改变当前会话 |
| 安全与日志 | 配对码、密码、密钥不进入日志、持久配置或命令行；二进制来源/hash/license 可核对 |

当前 ADB 修复与 Windows 自解压完整流程仍需正式服务器与真机验收。目标手机为 OnePlus ACE 6T、iQOO Neo9（Android 16）；其他 Android 版本需另行覆盖。

操作入口：[ADB 指南](docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)、[Windows 指南](docs/plans/WINDOWS_BUILD_GUIDE.md)。外部服务与依赖见 [资产登记](EXTERNAL_ASSET_REGISTRY.md)。
