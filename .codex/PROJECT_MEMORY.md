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

- 手机不再提供 ADB 页面；PC 顶栏弹窗负责配对/连接，状态归右上检测面板。
- 手机无线调试配对窗口保持开启。配对端口与连接端口不同；LADB 成功配对不等于 Tunnel 已获得信任。
- 内置 adb 用私有 Unix socket 与自己的 key，避免占用 LADB 的默认 daemon。配对码只走 stdin，不保存或记录。
- 配对成功后还要 connect，并以带随机标记的 `id -u` 确认 shell UID 2000，不能只看命令退出。
- 控制请求、配对、视频源切换和取消各有独立生命周期；超时需取消并清理子进程。
- 授权持续到会话断开、手动撤销或退出 ADB 模式，不设 10 分钟自动断开；生产 helper protocol 3 不设一小时停止。
- “关共享”暂停视频并保留 helper；退出 ADB 模式停止该会话 helper。不能全局 kill adb 干扰其他功能。
- helper H264 经 owned JNI buffer 和现有 relay 到 PC，首帧实际显示后再切输入。
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
