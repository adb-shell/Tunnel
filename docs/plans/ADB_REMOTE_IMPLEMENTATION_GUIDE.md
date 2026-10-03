# ADB 远程配对与投屏

手机主页第二页提供 LADB 本机配对与持久 shell，从主页向右滑动进入，底部标签栏隐藏；PC 顶栏负责远程配对/连接，状态显示在右上检测面板。两种入口共用 Tunnel 的 ADB 密钥和私有 daemon。以下是当前源码流程，修订版配对、shell 与投屏仍待正式构建和真机验收，不能据此承诺 Android 11–16 全设备可用。

## 配对前先确认远控通道已加密

`SECURE_CHANNEL_REQUIRED` 来自 PC 发送前的检查，此时端口和配对码没有到达手机。当前界面在未加密时禁止提交，并分别提示服务器未返回签名公钥、签名错误、设备身份或握手错误；不要因此反复修改无线调试配对码。

上游 hbbs 的 `-k <32字节公钥字符串>` 只设置准入口令，不加载签名私钥；此模式会使 `get_pk()` 返回空值。部署时应使用服务器**原有密钥目录**中的 `id_ed25519` 与 `id_ed25519.pub`，将原 hbbs 启动参数的 `-k <公钥字符串>` 改为 `-k _`，保留原有 `-r`、端口、volume 和工作目录设置。hbbr 可同样使用 `-k _`，前提是它读取同一套密钥；分离部署需核对实际 Key 一致。不要在空目录启动导致生成另一套密钥，不要把私钥粘贴到命令行或客户端。

客户端 Key 必须等于该目录实际的 `id_ed25519.pub`；修正部署后重新连接 PC 与手机，使其重新协商加密。若实际公钥不同，需要更新客户端 Key 或构建环境 `RS_PUB_KEY`，不能沿用旧口令冒充公钥。已有正常加密连接不需要调整服务器。

依据：[hbbs 的 get_server_sk/get_pk](https://github.com/rustdesk/rustdesk-server/blob/master/src/rendezvous_server.rs)、[hbbr 的 get_server_sk](https://github.com/rustdesk/rustdesk-server/blob/master/src/relay_server.rs)。服务器部署在本仓库之外，本次未执行服务器重启或验证其密钥文件。

## 源码地图

| 入口 | 职责 |
|---|---|
| `android-adb/prepare_adb.py`、`ladb-prebuilt.lock.json` | 固定 LADB commit/blob、ABI、许可证及供应 receipt |
| `third_party/ladb/`、`TUNNEL_SOURCE.json` | 完整上游仓库快照、四 ABI 预编译件、原始许可及逐文件 SHA-256 |
| `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/` | 本机配对、mDNS、手填连接、shell UID 探测、transport lease |
| `android-helper/server/` | shell helper：capture、MediaCodec、UiAutomation、输入、显示恢复、截图/节点帧 |
| `android-helper/protocol/` | 本地 IPC 认证、长度上限、epoch/sequence、固定操作 |
| `adb/mirror/TunnelAdbSession.kt` | helper staging/hash、子进程、双 socket、watchdog、清理 |
| `adb/mirror/TunnelAdbRuntime.kt` | PC 显式配对/授权后的 conn scopes、mode、独立控制权限、视频任务、画面优先级及资源恢复 |
| `adb/RemoteAdbPairing.kt` | 有限后台配对/连接 worker，进度、取消、断线隔离及 redacted 结果 |
| `AccessibilityLifecycle.kt` | enabled/bound/paused/inputAvailable，不覆盖其他服务列表 |
| `DFm8Y8iMScvB2YDw.kt`、`oFtTiPzsqzBHGigp.kt`、`pkg2230.kt` | core 生命周期、JNI dispatch、手机本地 MethodChannel |
| `libs/scrap/src/android/encoded.rs`、`pkg2230.rs` | 独立有界 H264 入口，复制 owned 数据，与旧 RGBA 分离 |
| `src/server/android_control.rs`、`connection.rs` | 认证/加密/权限/owner、控制与视频出口、input gate |
| `src/client.rs`、`src/client/io_loop.rs` | 候选 decoder、barrier、呈现通知、丢弃陈旧 source |
| `flutter/lib/models/android_mode_model.dart` | 每个会话窗口的状态、请求、presented ACK 和 heartbeat |
| `desktop/widgets/android_adb_menu.dart`、`android_adb_pairing_dialog.dart` | PC 顶栏动作、可拖动配对/连接授权弹窗 |
| `models/android_adb_pairing_model.dart`、`common/widgets/overlay.dart::TunnelStatusMonitor` | 独立配对事务、会话授权状态、右上角 ADB 检测 |
| `mobile/pages/adb_page.dart`、`models/local_adb_model.dart`、`adb/LocalAdbTerminal.kt` | 手机第二页、本机配对/连接、持久 shell、终端交接 |

不新增 FRB 方法：使用已有 `sessionPeerOption(name: "android-control")`，Rust 截取此保留命令且不保存为偏好。会话事件 `android_control/status` 返回 JSON。protobuf 新字段需 PC/APK 配套重建；旧端无能力时不能按成功处理。

## 服务器构建

本地工作区只改源码。本节是用户在正式构建机的执行入口，不表示本次已经执行或已生成 APK/EXE。

Android 使用现有入口：

```sh
./build.sh 1
# 或原多 ABI 入口
./build.sh 2
```

脚本准备所选 ABI 的 `libadb.so` 并构建/打包受控 helper；Gradle preBuild 检查 native receipt、ELF ABI 和 helper 来源/哈希，缺件立即失败。直接用 Flutter/Gradle 时，先按 `android-adb/README.md`、`android-helper/README.md` 完成准备。不要再依赖未纳入 Git 的个人 jniLibs 目录，也不要放入重命名的旧 SO。

`libadb.so` 来自 [LADB 固定提交](https://github.com/tytydraco/LADB/tree/60f48029cf9d8e0bc848ca41a7bd76694d4ab796)，不是 `libad.so`。供应脚本优先从仓内 `third_party/ladb/` 取得并核验预编译件，缺少快照时才在线获取；`--offline` 支持直接从仓内快照准备。不宣称从 AOSP 可复现构建或已经验证 Android 16 的 16 KiB 页兼容。

Windows 使用：

```bat
new-build.cmd
rem 用户习惯的兼容入口也可使用：
pc-bulid.cmd
```

输出 `PC-Bulid/<源码目录名>.exe` 与 `.exe.payload.json`。新版 portable 读取 Cargo 实际 executable 路径，支持 `CARGO_TARGET_DIR`，传播 Cargo 非零退出；以新 staging 打包，保留未压缩 Release。打包检查 `tunnel.dll`、`dylib_virtual_display.dll`、`WindowInjection.dll`、`usbmmidd_v2`、`drivers/RustDeskPrinterDriver`、`printer_driver_adapter.dll`；仍须从受控驱动目录供应真实文件。文件存在不等于驱动安装/签名验收通过。

## 先在手机验证 LADB

1. 进入主页第二页 LADB，打开系统开发者选项 → 无线调试。按 [LADB 官方说明](https://github.com/tytydraco/LADB) 使用分屏/浮窗，让 Tunnel 与“使用配对码配对设备”窗口同时保持打开。
2. 输入配对窗口的端口和六位码，点“配对并连接”。配对端口留空可自动发现；连接端口来自无线调试主页面，通常先留空自动查找。已配对但未连接时，填连接端口后点“手动连接”。
3. 显示 shell 就绪后点“验证 id”，应实际输出 `uid=2000(shell)`。终端支持连续命令、`cd`、持续输出及“中断命令”；命令不加 `adb shell` 前缀。历史/书签/输出只存内存。原生失败详情只显示在手机输出中。
4. 打开“允许远程控制 / ADB 配对”。本机终端与远程控制各用独立客户端，不需要为了投屏停止终端。本机已配对后，PC 可直接选择“已配对，连接”，无需重复配对；PC 会再次核验 shell 并单独授予当前远控会话权限。

该页是按上游 LADB 配对、终端流程适配的 Flutter 界面，复用 Tunnel 后端；上游完整源码保留在 `third_party/ladb/`，没有另行启动原版独立 APK。切页/切到设置不取消正在进行的配对，也不杀 daemon；本地终端持续到手动停止、shell 退出或应用进程结束。

## PC 配对与远程使用

1. PC/APK 使用同一批源码构建安装，建立加密且已授权的远控连接，手机允许远程控制，PC 退出“仅查看”。在手机系统开发者选项打开无线调试及“使用配对码配对设备”；**保持系统配对窗口打开**，关闭会使码/端口失效。配对本身不要求已订阅视频或无障碍服务已绑定。
2. PC 顶栏 ADB 图标 →“远程 ADB 配对 / 连接授权”。选择“首次配对”，输入系统窗口的配对端口与六位码；下方连接端口始终可见，已知时可同时填写，留空自动发现。点击“配对并连接”。拖动标题移动窗口；打开弹窗时远控键盘让给本地输入框。请求经原 Tunnel 加密会话发给 APK，APK 在后台执行；PC 不直接连接手机 ADB，二者不必同局域网。三项字段分别传递，六位码保留开头的零。
3. APK 配对后自动发现独立连接端口并连接，再进行本机 nonce / `uid 2000` 校验。**配对端口和连接端口不能混用**。若已配对但未连接，弹窗自动切到“已配对，连接”，填无线调试主页的连接端口，点“连接并授权”；已由 Tunnel 配对的设备也用此入口。LADB 与 Tunnel 有各自密钥，LADB 的配对成功不能代替 Tunnel 配对。
4. 弹窗先显示发送/等待手机确认，收到手机回包后才显示准备/配对/连接/核验。12 秒无接收确认显示 NO_RESPONSE 并请求取消；已确认但 90 秒未结束也请求取消；取消等待手机确认，10 秒仍无确认明确显示未确认。APK 操作预算45秒。码提交即清空、不入偏好/日志；关闭弹窗取消未完成任务。断线/撤权阻止迟到 grant，不 kill 全局 ADB。
5. fresh 校验成功后，当前允许控制的加密已认证 conn 获得会话 scopes，在当前会话持续有效，无固定到期时间。当前采用 PC 主动请求后的会话授权；历史配对和普通连接不自动 grant。点击“开启 ADB 投屏”时另查视频订阅，首帧解码与显示确认 COMMITTED 后才确认 ADB 视频源。ADB 控制 helper 就绪后即优先处理坐标/键盘输入，与当前视频来源无关；无真实 ADB 控制能力时才使用无障碍。侧栏动作和无障碍管理按当前授权执行，不要求 ADB 视频已开启。
6. 右上角检测面板只显示“ADB”和“投屏模式”两行，错误细节保留在配对窗口/悬停提示，菜单只放动作。“关闭 ADB 投屏”只停止 helper 内的视频任务并接受已有普通共享，保留控制 helper、遮罩和会话 ADB 授权；没有普通录屏时等待手机显式开启，也可以再次开启 ADB。断线或“撤销本连接 ADB 授权”才撤销 scopes；恢复连接后再通过 PC 显式连接授权，不复用旧 conn 权限。

本地持久终端、远程控制和有限 shell 操作使用独立、有数量上限的 ADB 客户端；只有 UiAutomation/helper 本身保持单实例，避免多个进程争抢系统无障碍自动化通道。配对/连接任务单独去重。视频任务不会 kill daemon、清配对或撤销会话 scopes；只有断开远控或明确撤销才结束当前远控授权。

## 模式与按钮语义

“无障碍功能”和“ADB功能”是两套独立可拖动悬浮栏，固定使用各自执行通道。ADB 栏只在当前远程会话已获 ADB 授权时出现，不依赖普通视频首帧。重复操作有界去重；某个按钮执行中不锁住其他无关动作。

| 动作 | 无障碍功能栏 | ADB 功能栏 |
|---|---|---|
| 开/关无视 | 原截图 fallback | helper UiAutomation screenshot → H264 / 关闭当前 override 后返回 base mode |
| 开/关穿透 | 原无障碍节点绘制 | 独立 UiAutomation 节点 Canvas → H264 / 关闭当前 override 后返回 base mode |
| 开/关黑屏 | 原 overlay 行为 | shell 黑色合成层，排除截图/录制；不改变物理屏幕电源，不吞掉 ADB 注入 |
| 开/关共享 | 原明确授权/停止逻辑 | 只控制普通 MediaProjection；现有授权可恢复，无授权时通过固定 shell 启动入口请求系统录屏确认，不停止 ADB 视频 |
| 暂停/恢复无障碍 | 原本机控制 | 暂停工作而保留授权 / 恢复运行；ADB仍持有输入所有权 |
| 关闭无障碍权限 | 本机开关 | 仅关闭本应用服务 `disableSelf()`，不覆盖其他服务列表 |
| 重新开启无障碍 | 手机设置 | 打开手机设置并要求本机确认；不写全局服务列表 |

明确限制：截图/节点不突破 FLAG_SECURE、DRM 或锁屏保护；穿透画面是节点表示，不是原像素。黑屏依赖 AOSP Android 12+ 的排除录制合成层接口，按实际反射/API 结果探测；Android 11 或删改接口的 ROM 明确返回不支持，目标两台 Android 16 仍须验证物理屏幕黑、PC 画面正常。普通 APK 触摸覆盖层会吞掉 ADB 注入，因此 ADB 防触摸尚无可保证的 provider，应明确拒绝，不能假成功。旧无障碍节点选择器没有冒充 ADB 节点编辑器。单个 ADB 视频 owner；同设备多个PC窗口时禁止启动ADB，后来出现的旁观窗口冻结坐标输入且不ACK，owner丢失时重连需 PC 显式重新授权。输入覆盖单指拖动、pan、Android键码和常用ASCII/功能键；任意Unicode/多指缩放/滚轮专项仍需补齐与验收。任意远程 shell、安装应用、跨用户和系统音频不在本次范围。

普通 MediaProjection 持续运行、保留自己的 token/reader，不因 ADB 开关销毁。普通无障碍截图/节点继续使用自己的源管理。Rust 根据有效源选择 ADB 视频或仍运行的普通画面；旧 taskId/epoch/generation 不能抢占新请求。ADB 视频关闭、超时或编码失败后接受仍运行的普通画面，不要求用户“切模式”；没有可用画面时等待，期间仍可操作 ADB 或重新开启视频。

截图和节点保留各自开关，最后开启的覆盖源优先；关闭后回到仍开启的另一覆盖源，否则恢复 ADB live。所有源选择都是可替换的视频任务。连续重复开启以最新请求为准，旧回执不能取消新任务或改变新输入几何。

单个常驻 helper 持有 UiAutomation、黑罩和输入，内部 encoder 按协议 5 的 VIDEO_TASK 独立启停。普通 codec/provider 失败只结束视频任务；本地 IPC 断裂或 OEM Binder/codec 卡死会有界回收 helper，并保留授权重新建立控制通道，不能全局 kill ADB。普通帧、ADB 帧及当前显示尺寸的呈现确认与输入权限分离：每个手势固定后端，旋转/几何变动先取消旧手势，防止半次点击分发给两套输入。

ADB 投屏的常亮锁不改系统熄屏设置，视频结束释放；它不自动解锁手机。无障碍防触层与 ADB 注入混用时，显式远程手势期间暂时穿透该层，结束后恢复吸收。此机制不能保证在同一瞬间区分物理触摸与注入触摸；独立 ADB 防触仍不支持。系统录屏首次授权仍需用户确认。

PC/APK 必须配套重建，不能搭配旧 helper。录像在 ADB 源接管前停止；开启自动接收录像时拒绝 ADB 视频请求，避免来源混录。

## 本轮故障定位与验证重点

- 取消中断必须先暂时清除再回收进程/等待输出线程，最后恢复中断；否则执行器误判泄漏并永久拒绝后续命令。NSD 收集多个本机记录，不能首记录到达即终止。独立私有 daemon socket 和既有 HOME 配套，pair/connect/probe/helper/P0 共用同一配置；有界 get-state 与 nonce uid 探测是 shell 就绪依据，连接后允许短暂就绪等待。
- MainService 重建后旧 CM add_connection 不一定重发；Rust 在当前加密/认证/控制权限检查之后通过私有 JNI 刷新配对资格，native 仍复查且断线撤销。配对不再依赖视频订阅；投屏/输入继续检查视频权限。Android 模型初始化不再强制写 `enable-keyboard=N`，无障碍后台重绑也不会覆盖用户控制权限。拒绝原因分别显示 `ADB_CONTROL_PERMISSION_REQUIRED`、`ADB_VIEW_ONLY_SESSION`、`ADB_SESSION_NOT_ENCRYPTED` 等；旧复合码仅用于旧端兼容。helper 错误不回传 shell 文本或异常消息。

- 状态与取消：独立 pairing kind/operationId/revision，不改 video epoch；取消/撤权/断线阻止 late grant；ADB_BUSY 不沿用旧 verified 作为本次核验；PC 显示明确错误和会话授权状态。
- Android11—16：最低 API30，NSD 本机服务筛选、IPv6 本地服务映射 loopback、resolver busy 有限重试及手填 connectPort。正式版本/ROM/ABI 尚未运行；固定 LADB native 的16KiB ELF/真机兼容仍未证实，不能声明全原生系统100%支持。验收见根目录 TEST_MATRIX.md。

- vivo/iQOO：旧 JNI 连接回调创建 ServiceInfo 并写硬编码 flags；已改为保留系统对象和声明能力。状态刷新区分“开关已开，等待系统绑定”，不在返回页时取消授权。此为可证实源码风险修复，不能据此断言已证明全部 OEM 根因。
- 缺 ADB：tracked 源码此前没有 libadb.so 供应闭环，单独改显示名无法生成 binary。现在由锁定来源供应与 Gradle 校验防止缺库 APK 出厂。
- Windows portable：旧 Python 调用未可靠传播 Cargo 失败并假设固定产物目录；现在按 JSON artifact 路径复制、核对载荷清单，保留排查产物。

《编译验证需求》：分别在 Android/Windows 正式构建机运行上述入口；执行目录为各服务器完整源码根目录。先验 APK native/helper 资产与 PE 自解压清单，再按 [测试清单](../../TEST_MATRIX.md) 验证无障碍、普通共享、配对、首帧、权限及 Windows 打包。新增重点：两台 Android16 授权返回与重绑、三项配对输入、双侧栏固定通道、未开视频调用 ADB 动作、普通共享与 ADB 开关独立、截图/穿透覆盖栈恢复、普通画面时 ADB 触摸、LADB shell 与视频并行、连续重复开启与失败后直接重开、黑罩不污染远端画面、常亮超过系统熄屏时间、断网/撤销/权限丢失、20次切换及30分钟长稳。

每项回传：源码 commit+dirty、工具链、ROM build/ABI/页大小、APK/EXE SHA256、用例步骤/结果及脱敏错误码。不要提交配对码、ADB key、账号密码或画面/节点内容。上述修订的完整运行结果仍未验证。
