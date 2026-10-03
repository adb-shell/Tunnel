# Tunnel / 隧道

基于 [RustDesk](https://github.com/rustdesk/rustdesk) 二次开发的远程控制项目，包含 Rust 核心、Flutter 界面、Android 服务与 Windows 客户端。

- 产品名：Tunnel；Android 显示名：隧道。
- Android 包名：`com.tunnel.app`；Flutter 包：`flutter_hbb`。
- Rust 动态库：Android `libtunnel.so`，Windows `tunnel.dll`。

从 [项目入口](PROJECT_START_HERE.md) 开始。开发优先解决代码和实际故障，文档只维护必要事实。

| 内容 | 入口 |
|---|---|
| 架构与源码 | [架构](docs/AI_ENGINEERING/01_ARCHITECTURE.md)、[源码地图](docs/AI_ENGINEERING/02_SOURCE_MAP.md)、[功能地图](docs/AI_ENGINEERING/12_FEATURE_MAP.md) |
| Android / ADB | [ADB 使用与实现](docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md) |
| Windows 构建 | [构建与打包指南](docs/plans/WINDOWS_BUILD_GUIDE.md) |
| 工具链与依赖 | [构建系统](docs/AI_ENGINEERING/08_BUILD_SYSTEM.md)、[外部资产](EXTERNAL_ASSET_REGISTRY.md) |
| 验证 | [测试清单](TEST_MATRIX.md) |

Android 与 Windows 产品构建在用户的正式服务器环境执行。本地静态检查不代表设备测试或打包成功。

本项目保留 RustDesk 及第三方组件的版权与许可证。许可见 [LICENCE](LICENCE)，依赖组件按各自许可证使用。
