# CloudSend 调试与验证体系 / Debug System

接管基线：2026-07-12  
状态：`verified` + `verification-required`

## 1. 调试原则

CloudSend 的故障通常跨 Flutter、Rust、JNI、Kotlin、驱动和外部服务。调试必须先回答三个问题：

1. 失败发生在哪个状态层或 trust boundary？
2. 能否用同一个 correlation ID 串起控制端、受控端和外部服务？
3. 当前证据是源码事实、日志事实、复现事实还是推测？

禁止用“服务在线”“连接成功”“画面黑”概括多个状态。特别是 Android 必须分开 core service、screen share、frame source、PC waiting。

## 2. 证据等级

| 等级 | 定义 | 可用于 |
|---|---|---|
| E1 | 源码静态证据 | 建立路径、风险假设 |
| E2 | 有时间/版本/设备的日志 | 定位阶段与时序 |
| E3 | 可重复的正式环境复现 | 确认 bug |
| E4 | 自动化回归或攻击复现 | 关闭风险 |
| E5 | 生产观测与回滚验证 | 发布完成 |

本次接管多数风险为 E1，少数历史问题只有无原始日志的观察记录。不得把 E1 写成“已经发生”，也不得用一次无法追溯的 E2 宣布修复。

## 3. 日志安全

不得记录或上传：

- password、permanent/default password。
- API access/refresh token、Authorization header。
- ZEGO secret、client credential 或 room token。
- signing key、keystore、private key。
- 完整剪贴板、文件正文、终端命令输出和联系人数据。
- 未脱敏的生产地址、设备 UUID、peer ID、IP 或用户名。

统一采用 session correlation ID；peer/device/address 只保留不可逆短 hash 或受控脱敏值。诊断包生成前必须执行 secret/PII redaction。

## 4. 通用会话分段

按以下 checkpoint 记录结果和耗时：

```text
process start
  -> configuration loaded
  -> rendezvous registered/resolved
  -> relay connected
  -> secure handshake
  -> peer authentication
  -> capability/permission
  -> service started
  -> first payload/frame
  -> steady state
  -> reconnect/close
```

每个 checkpoint 至少记录：component、version、platform、monotonic timestamp、result class、sanitized error、retry count。

## 5. Android 调试树

### 5.1 无 ID/离线

检查顺序：

1. MainService `_isReady` 和进程是否存在。
2. JNI `MAIN_SERVICE_CTX` 是否初始化。
3. Rust rendezvous `mainGetConnectStatus()` 原始状态。
4. 网络、relay/rendezvous 连接和 2.5 秒 reconnect timer。
5. 不要因为 `_connectStatus` 波动自动重启 core service。

### 5.2 已连接、无首帧

检查四层：

- Core：MainService/JNI/relay。
- Share：`_isStart`、`mediaProjection`、VirtualDisplay、`captureStarting`。
- Source：normal、SKL、ignore/one-shot 中哪一条实际产帧。
- PC：`waitForFirstImage`、timer、最后真实 RGBA/Texture 时间。

允许发送 normal `sessionRefreshVideo(...)`；禁止通过调试脚本自动切 ignore、截图、重绑 VirtualDisplay 或弹 MediaProjection 权限。

### 5.3 Android 14/15

需要记录：token 创建次数、permission result、VirtualDisplay create/release、projection stop reason、lock/unlock、是否清除 saved intent。任何 hidden reconnect path 出现新授权框都判定失败。

### 5.4 Native frame memory

专项关注：

- `ImageReader.acquireLatestImage().use` 生命周期。
- DirectBuffer 地址保存与 Rust `FrameRaw::take()` 的时间差。
- `static mut PIXEL_SIZE*` 的跨线程读写。
- screenshot/Accessibility shared buffer 并发。

正式设备建议开启 CheckJNI 和可用的 native memory/thread sanitizer；如果平台不支持 sanitizer，至少用高帧率、静态帧、旋转、锁屏、后台、内存压力组合复现。

### 5.5 输入权限

在 keyboard/input permission 开与关两种状态下分别注入 Mouse、Touch、Key 和 command mask 37/39/40/41/43/44，记录受控端是否拒绝。UI 按钮隐藏不计为通过。

## 6. Flutter 调试树

- 启动分流：确认 mobile/desktop/`--cm`/multi-window type。
- EventToUI：记录 event type 与 session ID，不记录敏感 payload。
- First frame：RGBA 与 Texture 两种事件都必须清 waiting。
- Lifecycle：跟踪 `FFI`、`ServerModel`、timer、stream subscription 与 controller dispose。
- Multi-window：确认 window ID 复用、关闭和跨窗口 event routing。
- HTTP：用唯一 request ID，避免以 URL 作为并发结果 key。

当前需验证的 lifecycle debt：`ServerModel` 500ms periodic timer 无保存/取消，以及 StatelessWidget 中 `TextEditingController.dispose()` 不会被框架调用。

## 7. Windows 调试树

- Capture：枚举显示器、capturer backend、codec、首帧和 display change。
- Input：权限、secure desktop/UAC、键盘布局、低级 hook。
- Privacy：mode owner、RuntimeBroker helper、injection DLL、恢复路径。
- Virtual display：当前 Amyuni implementation、process-local count、plug/unplug sequence。
- Driver：version、签名、安装状态和 reboot requirement。

隐私模式或虚拟显示失败必须优先恢复真实桌面、输入和显示拓扑。不要用循环快速 plug/unplug 作为自动恢复；源码已有可能导致 server crash 的警告。

## 8. 网络/API 调试树

- 区分 rendezvous、relay、secure handshake、login 和 service permission。
- CloudSend controller 预期始终 relay；出现 direct candidate 应记录为策略偏差。
- 抓包只能在批准的测试环境进行，并在采集前替换生产 credential。
- API 记录 method、endpoint label、status class、latency、timeout/retry；不记录 URL query/token/body。
- heartbeat/config/disconnect 必须验证 server authentication。
- ZEGO 分开检查 control invitation、token broker、room join、publish、play 和 first audio frame。

## 9. 最小回归矩阵

本节保留调试速查；商业级 case IDs、优先级、正式环境、oracle、负向/兼容/回滚和 evidence owner 以根目录 `TEST_MATRIX.md` 为准。

| 域 | 最低矩阵 |
|---|---|
| Android | Android 10、13、14、15；至少 Huawei/ColorOS 类定制 ROM 与一台接近原生设备 |
| Android display | 静态画面、旋转、分屏、锁屏、后台、低内存、小尺寸/折叠状态 |
| Windows | 支持的 Windows 10/11 build；单屏/多屏；Amyuni 有/无；UAC/锁屏 |
| Network | 正常 relay、延迟、丢包、瞬断、60s+ 断线、错误密码、key mismatch |
| Service | remote desktop、input、clipboard、file、terminal、printer、voice 各自权限开/关 |
| API | 2xx、4xx、5xx、timeout、malformed JSON、oversize、token expiry/revoke |

## 10. Bug 记录模板

```text
标题：<组件>/<阶段>/<现象>
基线：commit + version + build provenance
环境：OS/ROM/device/toolchain/server environment
前置状态：auth/permission/frame source/network
最小复现：
预期：
实际：
证据：sanitized logs/screenshots/crash dump
首次失败 checkpoint：
风险等级：
临时规避：
回归矩阵：
```

## 11. 《编译验证需求》

本轮未执行任何下列命令。请在正式环境由项目负责人批准后执行并回传完整、已脱敏日志。

### 11.1 Rust workspace

命令：

```bash
cargo build --release --features flutter
cargo test
```

环境要求：与项目锁定依赖兼容的 Rust 1.75、C/C++ toolchain、vcpkg/system dependencies、protobuf/FRB 相关工具；按目标平台准备驱动和系统 SDK。

执行目录：CloudSend 仓库根目录。

验证目标：workspace/feature 可编译；unit tests 通过；重点确认 `cli` feature/API drift、Android JNI symbol、generated bridge 与当前 FFI 一致。若要验证 `cli`，需另加显式 `--features cli` 的受控构建，不应混入发布构建。

### 11.2 Flutter static/test/build

命令：

```bash
cd flutter
flutter pub get
flutter analyze
flutter test
```

环境要求：项目锁定 Flutter/Dart 版本与 plugin dependencies。当前仓库只有极少 Dart test，且 test dependency 状态需先确认。

执行目录：`CloudSend/flutter`。

验证目标：Dart analyzer、bridge API、widget/model lifecycle 和现有测试基线；不得把“无测试被发现”当作通过。

### 11.3 Android release build 与真机

命令：

```bash
./build.sh 1
./build.sh 2
```

环境要求：项目规定 Linux Android 构建机、`/opt/rustdesk-toolchain` 等价受控环境、Rust/Flutter/JDK、Android SDK/NDK 27.2、vcpkg、签名环境、ZEGO SDK、受控 `libadb.so` artifacts；Android 10/13/14/15 真机。

执行目录：CloudSend 仓库根目录。

验证目标：三 ABI packaging、签名、JNI linkage；MediaProjection one-shot；share loss 不杀 core；DirectBuffer ownership；旋转/折叠/小于 350px 边界；input permission enforcement；ADB 旧系统/高输出/并发；ZEGO 明确接受/拒绝和麦克风行为。

### 11.4 Windows release build 与系统验证

命令：

```bat
new-build.cmd
```

环境要求：`PC-Build.md` 定义的 Windows Server/Windows 10-11 正式构建环境，`C:\DevEnv`、`C:\DevTool`、VS 2022 Build Tools、Rust 1.75 MSVC、Flutter 3.24.x、LLVM、vcpkg、Amyuni/driver/helper artifacts 和签名条件。

执行目录：CloudSend 仓库根目录。

验证目标：`cloudsend.exe`/`cloudsend.dll`/portable pack 一致；capture/input/file/terminal；Amyuni 单/多显示器；privacy mode 进入、异常恢复和退出；driver/DLL signature；无 RustDesk naming break。

### 11.5 安全与协议验证

命令：由安全负责人基于隔离测试环境制定；不得直接对生产服务扫描。建议包含 sanitizer、static analysis、dependency/SBOM、protocol interop、TLS/mitm-resistance 和 secret scanning。

环境要求：脱敏测试 credential、隔离 hbbs/hbbr/API/ZEGO broker、可重置设备与日志保存策略。

执行目录：按各工具说明；源码扫描从仓库根目录开始。

验证目标：secure handshake fail-closed、nonce uniqueness、受控端权限强制、API TLS/auth、credential 不出现在产物/日志、第三方 binary provenance 可追溯。

### 11.6 验证结果回填

正式结果必须回填：

- commit/hash 与 dirty state。
- toolchain versions。
- command 和 exit code。
- artifact hashes。
- 测试矩阵与通过/失败/未执行。
- 已脱敏 failure logs。
- reviewer 和日期。

在正式结果回填前，本文所有相关项保持 `verification-required`。
