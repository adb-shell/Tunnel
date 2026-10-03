# Tunnel 工程速查

先读 [项目入口](PROJECT_START_HERE.md)、[AI 规则](.codex/AI_RULES.md) 和 [项目记忆](.codex/PROJECT_MEMORY.md)，按任务读相关源码。不再要求任务模板、独立审计报告或多份记忆同步。

## 身份与代码入口

- 产品：Tunnel / 隧道；Android：`com.tunnel.app`；组织：`com.tunnel`。
- Rust crate/library：`tunnel`；Flutter package：`flutter_hbb`。
- Rust 核心：`src/`；共享协议：`libs/hbb_common/`；Flutter：`flutter/lib/`。
- Android Kotlin：`flutter/android/app/src/main/kotlin/com/tunnel/app/`。
- Android JNI 主路由：`libs/scrap/src/android/pkg2230.rs`；兼容文件：`ffi.rs`。
- `DFm8Y8iMScvB2YDw.kt`：MainService / MediaProjection。
- `nZW99cdXQ0COhB2o.kt`：AccessibilityService。
- `oFtTiPzsqzBHGigp.kt`：FlutterActivity。
- Android ADB：Kotlin `adb/`、`android-adb/`、`android-helper/`。
- Windows：`src/privacy_mode.rs`、`src/virtual_display_manager.rs`。
- 账号/API：`src/hbbs_http/`；旧桌面 UI：`src/ui/`。

## 必须保留的行为

- 服务存活、屏幕共享、视频首帧是不同状态。真实帧到达才清 waiting。
- Android 14+ MediaProjection token 一次性；连接、重连、刷新首帧不得隐式弹授权或自动开无视。
- 停止投屏不等于停止核心服务；PC 断开不能顺带停止普通 Android 共享。
- 主动共享入口为 `start_screen_share` / 侧按钮 `start_capture2`，旧 `init_service` / `start_capture` 不发起新授权。
- Controller 连接保持 relay-only；认证失败不能降级绕过。
- ADB 配对、连接、视频切换分开管理；ADB 状态不能伪造为已授权。
- ZEGO 媒体与 Rust 控制邀请分开；Windows 当前虚拟显示走 Amyuni。
- 修改跨层命令需检查完整收发链和资源生命周期。

## 构建与维护

Android 正式入口：`./build.sh 1` / `./build.sh 2`。Windows 正式入口：`new-build.cmd`，兼容 `build.cmd` / `pc-bulid.cmd`。正式构建在用户服务器执行。

详见 [ADB 指南](docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)、[Windows 指南](docs/plans/WINDOWS_BUILD_GUIDE.md)、[源码地图](docs/AI_ENGINEERING/02_SOURCE_MAP.md)。

优先交付代码。只在使用方式、接口或构建步骤改变时更新对应文档；项目记忆仅保留长期约定。不得为满足流程生成无关报告。
