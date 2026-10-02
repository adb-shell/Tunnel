# Tunnel 身份迁移基线

- Baseline ID：`TUN-BL-2026-10-02-IDENTITY`。
- Task：`T-2026-10-02-004`；Decision：D-016 / ADR-0015。
- Source：`test` / `532637a7b4084ec8ebf7deddbab12982628b1c1c` **加当前未提交工作树**，不是该 commit 已含迁移。
- Evidence：V0 静态；没有构建、测试、codegen、签名、部署或 Git 写入。
- 前基线：`CS-BL-2026-10-02-5cee692`；其中 hash、库存与历史环境记录不代表改名后的文件。

## 当前身份矩阵

| 层 | 当前值 / 来源 |
|---|---|
| 产品 / runtime / 默认标题 | `Tunnel`；`libs/hbb_common/src/config.rs::APP_NAME` |
| 中文 Android 展示名 | `隧道`；Android `strings.xml` |
| Rust package / library / default-run | `tunnel`；根 `Cargo.toml` 与 `Cargo.lock` |
| 产品版本 | Rust `5.2.1`，Flutter `5.2.1+59`；不升级版本号 |
| Runtime ORG | `com.tunnel`；shared config；不等于 applicationId |
| Android namespace / applicationId / manifest | `com.tunnel.app` |
| Android 源码目录 | `flutter/android/app/src/main/kotlin/com/tunnel/app/` |
| Android native | `libtunnel.so`；Kotlin `loadLibrary("tunnel")`，Dart 同库 |
| Windows native / runner | `tunnel.dll` / `tunnel.exe`；Cargo、CMake、Dart、runner、构建脚本一致 |
| portable | `tunnel-portable-packer`，marker `tunnel`，长度由 marker 推导 |
| macOS / iOS native | `libtunnel.dylib` / `libtunnel.a`；Xcode 链接与 Rust 导出对应 |
| macOS product / Apple bundle ID | `Tunnel.app` / `com.tunnel.app`；签名和外部配置仍待核实 |
| Linux binary / native / application ID | `tunnel` / `libtunnel.so` / `com.tunnel.app`；旧 distro 模板显式 staging 为 `rustdesk` binary 别名 |
| Deep link | `tunnel://`；Android/Apple manifest 与 runtime helper |
| FRB | `Tunnel`、`TunnelImpl`、`TunnelPlatform`、`TunnelWire`；生成入口显式 `--class-name Tunnel` |
| Native exports | `tunnel_core_main`、Windows `tunnel_core_main_args` / `get_tunnel_app_name` |
| Flutter platform channels | Android 主桥保留 `mChannel`；macOS `com.tunnel.app/macos` 两端一致 |
| P0 helper | `com.tunnel.adbhelper.Server`；protocol `com.tunnel.adb.protocol`；magic `TADB` / `TABT` |
| P0 identity / flag | `Tunnel/ADB/P0/v1`；Gradle `tunnelAdbMirrorP0` 默认 false |
| Status protocol / events | protobuf `tunnel_status = 39`；`update_tunnel_status`；保持 tag 39 |
| ZEGO broker contract | 客户端与仓内 Go 示例均 `tunnelSessionId`；外部实例未部署 |
| 工程知识与 Skills | `.agents/skills/tunnel-*`、TUNNEL 报告路径、名称与调用提示同步 |

## 身份与指纹边界

- Android 新包具有独立 sandbox，旧包的数据、ADB key、无线调试配对、无障碍授权、录屏授权不会自动转移。需安装后重新按教程授权；应用 UID 由 Android 分配。
- Runtime APP_NAME / ORG 改变配置、日志、IPC、插件等命名空间。远控 ID、UUID、密钥指纹可能随新配置生成或依设备算法保持；本次没有强制重置、伪造或验证这些运行时值。
- APK / EXE 的文件 hash 只有重建后才可记录；证书 SHA-256 与名称无关。未更换 keystore、签名证书、证书字节、设备硬件标识或第三方驱动身份。
- 本基线不声明新安装包已签名，不提供虚构 publisher、域名、邮箱或证书指纹。
- Windows MSI 默认 app/manufacturer 已改，UpgradeCode 仍由现有算法按 app name 派生。运行时注册名与路径采用新产品名；未运行安装器或修改注册表。

## 历史文档规范化声明

用户要求全仓统一项目专有名称，因此历史 Markdown、旧 ADR、Skills 和文件名也采用新名称。**这是 2026-10-02 的文本规范化，不表示旧日期已经使用新品牌。** 历史 Baseline ID、commit、hash、日期、验证结果保持原意；历史原文应从对应 Git 对象恢复，不能将规范化后的文档当作逐字快照。ADR-0002 已由 ADR-0015 替代。

## 编译验证需求（待用户正式环境执行）

1. 同一工作树重建 PC、APK、Rust native、FRB/protobuf、P0 helper 与 portable 数据；不可复用旧 SO/DLL/JAR/bridge header/data.bin/Runner.res。P0 helper 重新生成 manifest 与真实 hash。
2. Android 使用既有 `build.sh` 正式入口；PC 使用 `new-build.cmd`。默认 P0 仍关闭；需测试 P0 时按 `docs/plans/ADB_P0_VALIDATION_RUNBOOK.md` 显式配置 `tunnelAdbMirrorP0`。
3. Android 核验包名、中文名、native 加载、core 启动、录屏、无障碍和本地 ADB；PC 核验文件版本、DLL exports、便携解压启动、连接/重连/侧按钮/语音和状态显示。
4. 先部署与新 `tunnelSessionId` 配套的 broker，再验证语音；版本 API 类型也须服务端识别。旧 PC/APK 混用不作为本次支持的升级路线。
5. `apksigner` / 系统签名工具核实真实证书、完整性、签名主体；构建产物 hash、工具链与设备结果登记后才能形成 release baseline。
6. macOS/iOS 的 provisioning、entitlements、Firebase 等外部注册配置须由对应 owner 为新 bundle 提供真实资产；本次不伪造这些服务端注册。macOS服务模板由APP_NAME/ORG生成，Flutter与legacy可执行文件的大小写分别处理。
7. Linux distro 模板仍含上游安装路径：binary别名已补，旧模板安装 `rustdesk.service`，`src/platform/linux.rs` 按APP_NAME请求 `tunnel` 服务的既有差异仍待完整发行迁移；不能把本轮loader/输入路径对齐当作全发行版验收。当前正式编译交接重点为Android与Windows。

ADB 后续保持 T003 paused；以上证据回传后再续 P0，不把源码完成写成设备验证通过。
