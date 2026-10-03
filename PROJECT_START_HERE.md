# Tunnel 项目入口

当前实现以源码、manifest、协议和构建脚本为准。文档提供导航，不替代运行验证。

新会话只需先读本文件、[AI 规则](.codex/AI_RULES.md)、[项目记忆](.codex/PROJECT_MEMORY.md)，再按任务定位源码和对应文档。先检查 Git 工作区，保留已有修改。

## 主要文档

| 用途 | 文档 |
|---|---|
| 项目架构 | [架构](docs/AI_ENGINEERING/01_ARCHITECTURE.md)、[模块设计](docs/AI_ENGINEERING/03_MODULE_DESIGN.md) |
| 找代码与功能 | [源码地图](docs/AI_ENGINEERING/02_SOURCE_MAP.md)、[功能地图](docs/AI_ENGINEERING/12_FEATURE_MAP.md) |
| Android | [采集与服务](docs/AI_ENGINEERING/04_ANDROID_PIPELINE.md)、[ADB 指南](docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md) |
| Windows | [运行链路](docs/AI_ENGINEERING/05_WINDOWS_PIPELINE.md)、[构建打包](docs/plans/WINDOWS_BUILD_GUIDE.md)、[服务器环境](PC-Build.md) |
| 网络 / API | [协议](docs/AI_ENGINEERING/06_NETWORK_PROTOCOL.md)、[业务 API](docs/AI_ENGINEERING/07_API_SYSTEM.md) |
| 构建 / 验证 | [构建系统](docs/AI_ENGINEERING/08_BUILD_SYSTEM.md)、[测试清单](TEST_MATRIX.md) |
| 安全 / 外部依赖 | [安全模型](docs/AI_ENGINEERING/10_SECURITY_MODEL.md)、[外部资产](EXTERNAL_ASSET_REGISTRY.md) |
| ZEGO | [架构](docs/ZEGO_VOICE_CALL_ARCHITECTURE.md)、[集成](docs/ZEGO_VOICE_CALL_INTEGRATION.md)、[Token 服务](docs/ZEGO_TOKEN_SERVICE_DEPLOYMENT.md) |

## 工作约定

- 开发在当前独立工作区进行；Android、Windows 完整编译在用户的构建服务器执行。
- 根据当前请求直接完成已授权工作，不重复索要已有授权，不覆盖其他修改。
- 不为每次修改创建任务文档、审计报告、事件流水或多份记忆。历史用 Git 查询。
- 使用方式、接口或构建步骤实际变化时，只修改对应主要文档；长期关键约定只记在项目记忆。
- 运行通过必须有实际证据。当前 ADB 真机配对、投屏及 Windows 自解压产物仍需正式环境验收。
