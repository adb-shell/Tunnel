# ADB 远程投屏：源码交接、构建与使用

2026-10-03 · T-2026-10-03-001 · 基线 `TUN-BL-2026-10-03-ADB`。

这是本次实现的使用与验收入口。当前为源码交付，未在本地编译、执行测试或连接手机。不能把下表的源码覆盖当作 P0—P6 真机 PASS。构建在用户的 Linux Android / Windows 服务器进行；目标手机是 OnePlus ACE 6T 和 iQOO Neo9，均 Android 16，精确 ROM、ABI 和页大小需随报告记录。

## 1. 阶段与实现位置

| 阶段 | 本次源码 | 运行证据 |
|---|---|---|
| P0 本机原型 | 保留独立诊断卡、shell UID/本机 transport 校验、固定 helper、协议认证；新增 native 来源供应 | 用户报告旧版缺 libadb.so；修订版 NOT_RUN |
| P1 生命周期与协议 | 短命令、有界进程、取消、mirror lease；typed AndroidControl、单 owner、本机 scopes/期限/撤销 | NOT_RUN |
| P2 视频主链 | helper MediaCodec H264 → APK → owned JNI 队列 → 原 relay 会话 → PC decoder | NOT_RUN |
| P3 切换与输入 | epoch/revision、候选解码、同序 barrier、真实 Flutter 帧后 ACK、输入冻结与回退 | NOT_RUN |
| P4 无障碍共存 | 保留系统 ServiceInfo；区分配置/绑定/暂停；UiAutomation DONT_SUPPRESS；pause/resume/disable/settings | 两种 ROM 必测，NOT_RUN |
| P5 侧按钮 | ADB live、截图/节点 Canvas 经 H264、物理显示开关、有限输入；无障碍模式保留原链 | 分项能力/ROM 验收，NOT_RUN |
| P6 集成与交付 | 构建准入、异常清理/watchdog、PC/手机 UI、回归矩阵、本文与知识同步 | 源码交付；设备/长稳/发布验收未完成 |

## 2. 源码地图

| 入口 | 职责 |
|---|---|
| `android-adb/prepare_adb.py`、`ladb-prebuilt.lock.json` | 固定 LADB commit/blob、ABI、许可证及供应 receipt |
| `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/` | 本机配对、mDNS、手填连接、shell UID 探测、transport lease |
| `android-helper/server/` | shell helper：capture、MediaCodec、UiAutomation、输入、显示恢复、截图/节点帧 |
| `android-helper/protocol/` | 本地 IPC 认证、长度上限、epoch/sequence、固定操作 |
| `adb/mirror/TunnelAdbSession.kt` | helper staging/hash、子进程、双 socket、watchdog、清理 |
| `adb/mirror/TunnelAdbRuntime.kt` | 手机 consent/scopes、mode、输入所有权、状态及回退 |
| `AccessibilityLifecycle.kt` | enabled/bound/paused/inputAvailable，不覆盖其他服务列表 |
| `DFm8Y8iMScvB2YDw.kt`、`oFtTiPzsqzBHGigp.kt`、`pkg2230.kt` | core 生命周期、JNI dispatch、手机本地 MethodChannel |
| `libs/scrap/src/android/encoded.rs`、`pkg2230.rs` | 独立有界 H264 入口，复制 owned 数据，与旧 RGBA 分离 |
| `src/server/android_control.rs`、`connection.rs` | 认证/加密/权限/owner、控制与视频出口、input gate |
| `src/client.rs`、`src/client/io_loop.rs` | 候选 decoder、barrier、呈现通知、丢弃陈旧 source |
| `flutter/lib/models/android_mode_model.dart` | 每个会话窗口的状态、请求、presented ACK 和 heartbeat |
| `desktop/widgets/android_adb_menu.dart` | PC 顶栏 ADB 与无障碍动作 |
| `mobile/widgets/adb_remote_consent_card.dart` | 仅手机本地同意、scope 选择、10 分钟授权及立即撤销 |

不新增 FRB 方法：使用已有 `sessionPeerOption(name: "android-control")`，Rust 截取此保留命令且不保存为偏好。会话事件 `android_control/status` 返回 JSON。protobuf 新字段需 PC/APK 配套重建；旧端无能力时不能按成功处理。

## 3. 服务器构建

本地工作区只改源码。本节是用户在正式构建机的执行入口，不表示本次已经执行或已生成 APK/EXE。

Android 使用现有入口：

```sh
./build.sh 1
# 或原多 ABI 入口
./build.sh 2
```

脚本准备所选 ABI 的 `libadb.so` 并构建/打包受控 helper；Gradle preBuild 检查 native receipt、ELF ABI 和 helper 来源/哈希，缺件立即失败。直接用 Flutter/Gradle 时，先按 `android-adb/README.md`、`android-helper/README.md` 完成准备。不要再依赖未纳入 Git 的个人 jniLibs 目录，也不要放入重命名的旧 SO。

`libadb.so` 来自 [LADB 固定提交](https://github.com/tytydraco/LADB/tree/60f48029cf9d8e0bc848ca41a7bd76694d4ab796)，不是 `libad.so`。供应脚本按固定 blob 取上游预编译件；不宣称从 AOSP 可复现构建或已经验证 Android 16 的 16 KiB 页兼容。

Windows 使用：

```bat
new-build.cmd
rem 用户习惯的兼容入口也可使用：
pc-bulid.cmd
```

输出 `PC-Bulid/<源码目录名>.exe` 与 `.exe.payload.json`。新版 portable 读取 Cargo 实际 executable 路径，支持 `CARGO_TARGET_DIR`，传播 Cargo 非零退出；以新 staging 打包，保留未压缩 Release。打包检查 `tunnel.dll`、`dylib_virtual_display.dll`、`WindowInjection.dll`、`usbmmidd_v2`、`drivers/RustDeskPrinterDriver`、`printer_driver_adapter.dll`；仍须从受控驱动目录供应真实文件。文件存在不等于驱动安装/签名验收通过。

## 4. 手机配对与远程使用

1. 安装本次服务器构建的 `com.tunnel.app` APK。在系统开发者选项开启无线调试并打开配对码对话框；分屏保留设置与 Tunnel。不能先离开配对码页再期待原端口一直有效。
2. Tunnel 的 ADB 页面输入当前六位码；配对端口可以自动发现或手填。自动扫描只接受本机 loopback/当前手机地址，不能扫描其他手机后误连。
3. 配对成功后点击扫描连接，或输入无线调试主页的连接端口。**配对端口与连接端口不同**。界面只有通过真实本机 `uid 2000` 探测后才显示 ADB shell 可用。
4. PC/APK 用同一批源码构建，建立普通远控连接。PC 顶栏 USB/ADB 图标选择“请求 ADB 投屏”。手机 ADB 页面出现对应当前连接的申请。
5. 手机勾选所需权限并同意，再在 PC 点击投屏。初始只默认视频/输入；使用无视、穿透、物理显示或无障碍管理前需授予相应 scope。授权只存在进程内、只属于该连接，10 分钟到期；断线/撤销后不能自动恢复旧权限。
6. PC 首帧解码和显示确认后进入 COMMITTED；此时才允许 ADB 输入及暂停/关闭本应用无障碍。关闭 ADB、断线、helper 失效会清理自己的资源；已有普通共享活跃时回退，MP 已失效时须本机显式重新开共享。

ADB 页面保留取消、手动配对/连接和本机终端。投屏期间独占 transport，其他本地命令返回 busy；关闭页面不关闭远程 helper，不 kill 全局 ADB。

## 5. 模式与按钮语义

| 动作 | 无障碍模式 | ADB 已提交模式 |
|---|---|---|
| 开/关无视 | 原截图 fallback | helper UiAutomation screenshot → H264 / 关闭当前 override 后返回 base mode |
| 开/关穿透 | 原无障碍节点绘制 | 独立 UiAutomation 节点 Canvas → H264 / 关闭当前 override 后返回 base mode |
| 开/关黑屏 | 原 overlay 行为 | helper 物理 display off/on；动态探测，失败明确返回 |
| 开/关共享 | 原明确授权/停止逻辑 | baseMode live/stopped，暂停live但保留helper/ADB；唯一截图/节点override仍按其自身开关管理。恢复有新首帧事务 |
| 暂停/恢复无障碍 | 原本机控制 | 暂停工作而保留授权 / 恢复运行；ADB仍持有输入所有权 |
| 关闭无障碍权限 | 本机开关 | 仅 `disableSelf()`，须替代视频与输入已接管 |
| 重新开启无障碍 | 手机设置 | 打开手机设置并要求本机确认；不写全局服务列表 |

明确限制：截图/节点不突破 FLAG_SECURE、DRM 或锁屏保护；穿透画面是节点表示，不是原像素。物理熄屏可能被 ROM 拒绝。普通 APK 触摸覆盖层会吞掉 ADB 注入，因此 ADB 防触摸尚无可保证的 provider，应明确拒绝，不能假成功。旧无障碍节点选择器没有冒充 ADB 节点编辑器。单个 ADB owner；同设备多个PC窗口时禁止启动ADB，后来出现的旁观窗口冻结输入且不ACK，owner丢失时必要的重连需重新本机同意。输入覆盖单指拖动、pan、Android键码和常用ASCII/功能键；任意Unicode/多指缩放/滚轮专项仍需补齐与验收。任意远程 shell、安装应用、跨用户和系统音频不在本次范围。

“关共享”与顶栏“退出ADB”不同：前者不关闭helper，也不kill ADB；base stopped且无override时status.capturePaused=true，保留最后图像但冻结普通输入，可用侧按钮开共享/开无视/开穿透恢复有效源。后者才释放ADB模式，按新epoch回退已有MediaProjection。截图/节点关闭回到base mode。录像会在切ADB前停止；受控Android的自动录像已开启时拒绝ADB启动，不能把两路来源混录。

## 6. 本轮故障定位与验证重点

- vivo/iQOO：旧 JNI 连接回调创建 ServiceInfo 并写硬编码 flags；已改为保留系统对象和声明能力。状态刷新区分“开关已开，等待系统绑定”，不在返回页时取消授权。此为可证实源码风险修复，不能据此断言已证明全部 OEM 根因。
- 缺 ADB：tracked 源码此前没有 libadb.so 供应闭环，单独改显示名无法生成 binary。现在由锁定来源供应与 Gradle 校验防止缺库 APK 出厂。
- Windows portable：旧 Python 调用未可靠传播 Cargo 失败并假设固定产物目录；现在按 JSON artifact 路径复制、核对载荷清单，保留排查产物。

《编译验证需求》：分别在 Android/Windows 正式构建机运行上述入口；执行目录为各服务器完整源码根目录。先验 APK native/helper 资产与 PE 自解压清单，再按 `TEST_MATRIX.md` 的 ADBM-01—30、AND 无障碍/普通共享、FLT 窗口/首帧、NET 权限/旧端、Windows 驱动回归执行。新增重点：两台 Android16 授权返回与重绑、配对端口失效、扫描取消、候选失败不切源、旋转/截图/节点来回切换、断网/撤销/权限丢失、helper卡死、物理屏幕恢复、20次切换及30分钟长稳。

每项回传：源码 commit+dirty、工具链、ROM build/ABI/页大小、APK/EXE SHA256、用例步骤/结果及脱敏错误码。不要提交配对码、ADB key、账号密码或画面/节点内容。V1—V5 和所有 ADBM 运行结果当前均为 NOT_RUN。
