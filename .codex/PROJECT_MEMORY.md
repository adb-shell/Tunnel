# Tunnel 项目记忆

更新：2026-10-03。这里只保存长期约定与当前未验证边界，历史通过 Git 查询。

## 身份与环境

- RustDesk 二次开发；产品 Tunnel，Android 显示“隧道”，包名 `com.tunnel.app`，组织 `com.tunnel`。
- Rust `tunnel`，Flutter `flutter_hbb`；版本 5.2.1 / 5.2.1+59。
- Native 产物：`libtunnel.so` / `tunnel.dll`；scheme：`tunnel://`。
- 用户要求在独立工作区开发。Android 在 Linux 服务器构建，Windows 在 Windows Server 构建；本地没有完整正式构建环境。
- 当前目标设备：OnePlus ACE 6T、iQOO Neo9，均 Android 16。Android 11–16 全覆盖尚无测试证据。

## 关键实现约束

- 地图见 [源码地图](../docs/AI_ENGINEERING/02_SOURCE_MAP.md) 与 [功能地图](../docs/AI_ENGINEERING/12_FEATURE_MAP.md)。
- Android 主 JNI 为 `libs/scrap/src/android/pkg2230.rs`；`ffi.rs` 是兼容路径，不能当成当前主路由。
- 核心服务、普通共享、无视/穿透、ADB 视频源、PC waiting 分开管理。
- MediaProjection 14+ token 一次性。仅显式共享操作请求授权；重连/首帧刷新不得弹授权、切无视或截屏。
- 普通共享停止不销毁核心服务；PC 断开不自动停止普通共享；真实帧到达清 waiting。
- Controller strict relay-only；输入/视频/ADB 操作保留服务端授权检查。
- ZEGO 媒体走 SDK，Rust 仅参与邀请控制。Windows 虚拟显示当前走 Amyuni。
- Backend、数据库、hbbs/hbbr、完整 Token 服务与签名环境不在本仓库，不能宣称已验收。

## ADB 当前约定

- 手机第二页 LADB 提供本机配对与持久 shell；PC 顶栏负责远程配对/连接，状态归右上检测面板。两端入口共用 Tunnel 密钥和私有 daemon，本地终端与远程 helper 使用独立客户端通道，配对/重连单独串行管理。
- 配对检查会话认证、加密、控制权限，不依赖视频订阅或无障碍绑定；开始投屏另查视频权限。Android 初始化/无障碍状态刷新不得覆盖用户控制权限。
- 手机无线调试配对窗口保持开启。配对端口与连接端口不同；LADB 成功配对不等于 Tunnel 已获得信任。
- 内置 adb 用私有 Unix socket 与自己的 key，避免占用 LADB 的默认 daemon。配对码只走 stdin，不保存或记录。
- 配对成功后还要 connect，并以带随机标记的 `id -u` 确认 shell UID 2000，不能只看命令退出。
- 控制请求、配对、视频源切换和取消各有独立生命周期；超时需取消并清理子进程。
- ADB 授权持续到会话断开或手动撤销；关闭 ADB 投屏保留授权。helper protocol 5 不设生产使用时限，保留故障/心跳守卫。
- 两套悬浮侧栏固定走无障碍或 ADB，不按视频模式混用。“开/关共享”只操作普通 MediaProjection；视频选择按最新请求替换，停止只关闭视频任务，不结束控制进程。
- 普通 MP 保持运行，ADB live/截图/节点按实际有效源优先显示；源关闭或失败后接收仍运行的普通画面，不要求“切模式”。重复开启直接替换同连接旧视频任务；旧 taskId/epoch/generation 帧不可覆盖新请求。
- ADB 黑屏使用排除录制的 shell 遮罩，不能以物理关屏替代；不支持的系统明确失败。ADB 投屏持有自己的常亮锁，停止/故障释放。不能全局 kill adb 干扰其他功能。
- helper H264 经 owned JNI buffer 和现有 relay 到 PC；首帧实际显示才确认视频源。ADB 输入独立于视频，有真实控制能力时优先 ADB，否则使用无障碍；每次手势固定后端并检查当前显示几何。
- 普通无障碍与 ADB 可并存；不能为切换视频强行杀无障碍/ADB。受保护内容及系统权限限制不承诺绕过。
- 源码与静态检查不能证明配对、shell、Android 16、16 KiB page 或投屏实机已通过。使用与验收见 [ADB 指南](../docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。

## Windows 当前约定

- `new-build.cmd` / `build.cmd` / `pc-bulid.cmd` 统一到 `scripts/windows-build.ps1`。
- 正式流程：编译 → 定位实际 Cargo/Flutter 产物 → staging 文件校验 → 官方驱动获取 → portable 自解压打包与 payload 校验。
- 产物为 `PC-Bulid/<源码目录名>.exe`，同时保留 hash、payload/build 清单和日志；失败不能打印成功。
- 根 `Cargo.lock` 的 `tunnel.dependencies` 已补 `whoami` 边；旧服务器日志中 `--locked` 退出 101 的修复尚待重建验证。不要删除 lock 或去掉 `--locked` 绕过。
- Windows DLL 与 portable workspace member 共用根 lock。完整构建和自解压实际运行尚待正式环境验证，见 [Windows 指南](../docs/plans/WINDOWS_BUILD_GUIDE.md)。

## 文档约定

只保留主要文档。一般修复不建任务报告、不记流水、不复制记忆；事实变化只改相应指南或本文件。许可证和依赖来源必须保留。
