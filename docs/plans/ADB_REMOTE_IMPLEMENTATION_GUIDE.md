# ADB 远程投屏：源码交接、构建与使用

2026-10-03 T005：授权持续至当前连接结束/撤销/退出ADB，无10分钟倒计时；生产helper protocol3 durationSeconds=0，取消一小时停止，故障守卫保留。PC/APK/helper同批重建，旧protocol2资产不可混用；验收必须包含健康运行超过一小时。详见 [ADR-0017](../ADR/0017-adb-session-lifetime.md) / [任务](ADB_SESSION_LIFETIME_TASK.md)。

2026-10-03 · T-2026-10-03-001 / 004 · 基线 `TUN-BL-2026-10-03-ADB`。

这是本次实现的使用与验收入口。当前为源码交付，未在本地编译、执行测试或连接手机。不能把下表的源码覆盖当作 P0—P6 真机 PASS。构建在用户的 Linux Android / Windows 服务器进行；目标手机是 OnePlus ACE 6T 和 iQOO Neo9，均 Android 16，精确 ROM、ABI 和页大小需随报告记录。

## 1. 阶段与实现位置

| 阶段 | 本次源码 | 运行证据 |
|---|---|---|
| P0 本机原型 | 保留 native shell UID/本机 transport 校验、固定 helper、协议认证及 native 来源供应；T004 移除手机诊断界面 | 用户报告旧版缺 libadb.so；修订版 NOT_RUN |
| P1 生命周期与协议 | 短命令、有界进程、取消、mirror lease；typed AndroidControl、单 owner、conn scopes/期限/撤销；远程 pair/authorize | NOT_RUN |
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
| `adb/mirror/TunnelAdbRuntime.kt` | PC 显式配对/授权后的 conn scopes、mode、输入所有权、状态及回退 |
| `adb/RemoteAdbPairing.kt` | 有限后台配对/连接 worker，进度、取消、断线隔离及 redacted 结果 |
| `AccessibilityLifecycle.kt` | enabled/bound/paused/inputAvailable，不覆盖其他服务列表 |
| `DFm8Y8iMScvB2YDw.kt`、`oFtTiPzsqzBHGigp.kt`、`pkg2230.kt` | core 生命周期、JNI dispatch、手机本地 MethodChannel |
| `libs/scrap/src/android/encoded.rs`、`pkg2230.rs` | 独立有界 H264 入口，复制 owned 数据，与旧 RGBA 分离 |
| `src/server/android_control.rs`、`connection.rs` | 认证/加密/权限/owner、控制与视频出口、input gate |
| `src/client.rs`、`src/client/io_loop.rs` | 候选 decoder、barrier、呈现通知、丢弃陈旧 source |
| `flutter/lib/models/android_mode_model.dart` | 每个会话窗口的状态、请求、presented ACK 和 heartbeat |
| `desktop/widgets/android_adb_menu.dart`、`android_adb_pairing_dialog.dart` | PC 顶栏动作、可拖动配对/连接授权弹窗 |
| `models/android_adb_pairing_model.dart`、`common/widgets/overlay.dart::TunnelStatusMonitor` | 独立配对事务、会话授权状态、右上角 ADB 检测；手机 ADB 页面/两张卡已移除 |

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

## 4. PC 配对与远程使用

1. PC/APK 使用同一批源码构建安装，建立已有普通远控连接。在手机系统开发者选项打开无线调试及“使用配对码配对设备”；**保持系统配对窗口打开**，关闭会使码/端口失效。手机 Tunnel 没有专用 ADB 页面。
2. PC 顶栏开发者图标 →“远程 ADB 配对 / 连接授权”。拖动弹窗标题移动位置，输入系统窗口的配对端口与六位码，点击“配对并授权本连接”。请求经原 Tunnel 加密会话发给 APK，APK 立即在后台执行；PC 不直接连接手机 ADB，二者不必同局域网。
3. APK 配对后自动发现独立连接端口并连接，再进行本机 nonce / `uid 2000` 校验。**配对端口和连接端口不能混用**。若显示“已配对，尚未连接”，在可选连接端口填入无线调试主页端口，点“连接已配对设备并授权”；已有系统信任 key 的设备也使用此入口，不必每次重新配对。
4. 弹窗显示准备/配对/连接/核验及明确终态。码提交即清空，不保存在偏好/日志；可取消，关闭弹窗会取消未完成任务。APK 操作预算45秒，PC 90秒无结果主动请求取消；断线/撤权阻止迟到 grant，不 kill 全局 ADB。
5. fresh 校验成功后，当前有输入/视频权限的加密已认证 conn 获得投屏、输入、无障碍及侧按钮 scopes，在当前会话持续有效，无固定到期时间。此为 PC 主动授权政策，见 ADR-0016/0017，已经替代旧 phone-local consent；历史配对和普通连接不自动 grant。点击“开始 ADB 投屏”，首帧解码与显示确认 COMMITTED 后才接管 ADB 输入及允许暂停/关闭自身无障碍。
6. 右上角 Tunnel 检测面板显示 ADB 连接、投屏模式/真实来源、输入、会话授权状态、配对状态/错误；菜单只放动作。断线或“撤销本连接 ADB 授权”撤销 scopes；退出 ADB 同时撤销 scopes、释放 helper 并回退已有普通共享，MP 失效仍须手机显式开共享。恢复连接后再通过 PC 显式连接授权，不复用旧 conn 权限。

内部 Manager/Runner/native suite/probe/helper/有限 shell 继续保留，手机 UI 的页面、终端控件和诊断卡已移除。投屏期间独占 transport，配对/连接操作须先退出投屏；撤销与状态查询仍可用。不开放远程任意 shell。

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

明确限制：截图/节点不突破 FLAG_SECURE、DRM 或锁屏保护；穿透画面是节点表示，不是原像素。物理熄屏可能被 ROM 拒绝。普通 APK 触摸覆盖层会吞掉 ADB 注入，因此 ADB 防触摸尚无可保证的 provider，应明确拒绝，不能假成功。旧无障碍节点选择器没有冒充 ADB 节点编辑器。单个 ADB owner；同设备多个PC窗口时禁止启动ADB，后来出现的旁观窗口冻结输入且不ACK，owner丢失时重连需 PC 显式重新授权。输入覆盖单指拖动、pan、Android键码和常用ASCII/功能键；任意Unicode/多指缩放/滚轮专项仍需补齐与验收。任意远程 shell、安装应用、跨用户和系统音频不在本次范围。

“关共享”与顶栏“退出ADB”不同：前者不关闭helper，也不kill ADB；base stopped且无override时status.capturePaused=true，保留最后图像但冻结普通输入，可用侧按钮开共享/开无视/开穿透恢复有效源。后者才释放ADB模式，按新epoch回退已有MediaProjection。截图/节点关闭回到base mode。录像会在切ADB前停止；受控Android的自动录像已开启时拒绝ADB启动，不能把两路来源混录。

## 6. 本轮故障定位与验证重点

- T004 首次无响应/失败：`LocalAdbProcessSpec` 原 `-H 127.0.0.1` 被 AOSP local socket 判断视作 remote server，尚无 daemon 时不能自动启动；改为 `-H localhost`。原 pair 成功即返回，且 ADB_MDNS_AUTO_CONNECT=0，不会创建 transport；现在补独立连接端口发现/连接/fresh核验。stderr/stdout 均解析成功与拒绝码，只传稳定错误标识。
- T004 状态/安全：独立 pairing kind/operationId/revision，不改 video epoch；取消/撤权/断线阻止 late grant；ADB_BUSY 不沿用旧 verified 作为本次核验；PC 显示明确错误和会话授权状态。
- Android11—16：最低 API30，NSD 本机服务筛选、IPv6 本地服务映射 loopback、resolver busy 有限重试及手填 connectPort。正式版本/ROM/ABI 尚未运行；固定 LADB native 的16KiB ELF/真机兼容仍未证实，不能声明全原生系统100%支持。完整案例见 TEST_MATRIX 的 ADBP-01—12。

- vivo/iQOO：旧 JNI 连接回调创建 ServiceInfo 并写硬编码 flags；已改为保留系统对象和声明能力。状态刷新区分“开关已开，等待系统绑定”，不在返回页时取消授权。此为可证实源码风险修复，不能据此断言已证明全部 OEM 根因。
- 缺 ADB：tracked 源码此前没有 libadb.so 供应闭环，单独改显示名无法生成 binary。现在由锁定来源供应与 Gradle 校验防止缺库 APK 出厂。
- Windows portable：旧 Python 调用未可靠传播 Cargo 失败并假设固定产物目录；现在按 JSON artifact 路径复制、核对载荷清单，保留排查产物。

《编译验证需求》：分别在 Android/Windows 正式构建机运行上述入口；执行目录为各服务器完整源码根目录。先验 APK native/helper 资产与 PE 自解压清单，再按 `TEST_MATRIX.md` 的 ADBM-01—30、AND 无障碍/普通共享、FLT 窗口/首帧、NET 权限/旧端、Windows 驱动回归执行。新增重点：两台 Android16 授权返回与重绑、配对端口失效、扫描取消、候选失败不切源、旋转/截图/节点来回切换、断网/撤销/权限丢失、helper卡死、物理屏幕恢复、20次切换及30分钟长稳。

每项回传：源码 commit+dirty、工具链、ROM build/ABI/页大小、APK/EXE SHA256、用例步骤/结果及脱敏错误码。不要提交配对码、ADB key、账号密码或画面/节点内容。V1—V5 和所有 ADBM 运行结果当前均为 NOT_RUN。
