# Android / Flutter 源码接管审计

日期：2026-10-02（Asia/Shanghai）
Task：`T-2026-10-02-001`
源码观察点：`5cee6921ec10971bb4654bc010f9328d7f70d02b`
范围：C0 源码静态审查 + C1 文档更新；V0。未改业务源码，未 build/test/analyze/codegen，未操作设备、ADB、账号、token 服务或生产环境。

本报告是固定日期的源码证据快照。`verified` 仅表示所列源码支持；`inferred` 为跨文件/生命周期推断；`external` 表示仓外平台/SDK/服务；`verification-required` 表示须有正式环境证据；`historical` 表示旧文档陈述。没有运行验证不等于失败，也不等于通过。历史 `CS-BL-2026-07-12-77062b4` 不能替代本次 HEAD；本报告不声称两者的 Git diff 已证实。

## 1. 范围、资料和掌握边界

读取项目入口、AI rules、Session Start Protocol、Task Protocol、记忆状态、ADR index、相关 ADR-0004—0008、Android/Flutter Skills、当前 `03_MODULE_DESIGN.md` / `04_ANDROID_PIPELINE.md`、旧 `ENGINEERING_ANDROID_RUNTIME.md`、验证/安全文档和 `TEST_MATRIX.md` 后，按实际调用链重新查源码。

本轮深入覆盖：Android core/share/normal-SKL-ignore 帧源、PC waiting/reconnect、MethodChannel/JNI/FRB、Android input/custom commands、blank/touch-block/Dev selector、local ADB、ZEGO media/control/UI、Flutter engine/window/session owner 和主要 teardown。桌面 file/terminal/account 仅检查启动路由与 FFI owner；其业务、backend、Windows native 深入审计由对应报告承担。

未核验：真实 APK/native binary 与此 HEAD 的一致性、设备 OS/ROM 行为、所有 widget 的视觉与交互、所有 Kotlin 异常路径、外部 ZEGO SDK 内部状态、hbbs/hbbr/broker contract、正式构建/运行表现。因此“接管完成”只能用于此 repository-side 知识和定位成果，不能用于全设备正确性或上线可用性。

## 2. 实际入口和功能地图

| 功能 | 当前路由 / 源码 owner | 状态 |
|---|---|---|
| Mobile startup | `flutter/lib/main.dart::runMobileApp` → `initEnv` → `androidChannelInit` → config path channel → `ServerModel.ensureCoreService` | `verified` |
| Mobile home | `flutter/lib/mobile/pages/home_page.dart::HomePageState.initPages` → Android 非 outgoing-only 的 `ServerPage` / `AdbPage`，通过 `PageView` 承载 | `verified`；底部导航代码已注释 |
| Desktop process/window | `flutter/lib/main.dart::main` → `multi_window`、`--cm`、`--install`、main 分支 | `verified` |
| Desktop session kinds | `flutter/lib/utils/multi_window_manager.dart::WindowType` → RemoteDesktop/FileTransfer/ViewCamera/PortForward/Terminal | `verified`；保留源码不等于当前 mobile 有入口 |
| Android core | `DFm8Y8iMScvB2YDw.onCreate/onStartCommand` + Rust core + JNI GlobalRef | `verified` 源码；持续可用 `verification-required` |
| Screen share | Activity `start_screen_share` → permission Activity → MainService `startCapture` | `verified` |
| PC side commands | `overlay.dart` → `common.dart` callback wiring → `InputModel` → FRB → Rust session/client → authenticated endpoint → JNI → Kotlin | `verified` |
| ADB/LADB | `AdbPage` → `AndroidAdbManager` → `mChannel` → `CloudSendAdbManager` → Runner/DNS/Accessibility automation | `verified`；本地能力 |
| Voice media | Rust invite/control → Flutter `ZegoVoiceCallModel` → ZEGO SDK | `verified` 本地接线；RTC/broker `external` |

重要导航边界：`HomePageState.build()` 访问 `_pages.elementAt(_selectedIndex)`；当非 Android 或 outgoing-only 配置使 `_pages` 为空时存在异常路径。源码支持这一条件组合，是否在实际发布配置可达须正式验证。不能把 Android 主页直接推广为所有 mobile 平台行为。

Android manifest/build 事实：`flutter/android/app/build.gradle` 为 compileSdk 34、targetSdk 33、minSdk 21。MainService 声明 mediaProjection foreground-service type；Accessibility 非 exported，MainActivity 和 BootReceiver exported；scheme 为 `cloudsend`。`accessibility_service_config.xml` 配置 all-event/all-package、window content、gestures、screenshot 等能力。声明、SDK 分支、系统授予和运行可用性是四个不同层次。

## 3. 状态 owner 与资源生命周期

| 状态 / 资源 | 创建与 owner | 停止 / 回收 | 已知边界 |
|---|---|---|---|
| MainService `ctx` / `_isReady` | Kotlin companion；`onCreate` 注册 `MAIN_SERVICE_CTX` | explicit destroy 才 `VHsFQTvK`；unexpected destroy 500ms 后尝试 restart | `_isReady=true` 也可在 `onDestroy` 写入，不是存活证明 |
| Projection / VD / ImageReader / surface | MainService instance；permission result 后创建 | share-only stop、projection callback、VD failure 有各自清理 | token 行为按 SDK 分支；设备实效未验证 |
| `captureStarting` | MainService volatile flag | success/failure/stop/loss 清 false | 允许正常首帧早于 `_isStart=true` 到达 |
| `SKL` / `shouldRun` / pending ignore | Kotlin global + Accessibility companion | 显式切换、open-share bridge、destroy | 多 owner 共享状态，非单一事务 |
| `VIDEO_RAW` / `FrameRaw` | Rust Mutex；暂存外部 pointer | `take` 构造slice后清空保存的pointer/len，再比较/复制；`release` 不释放或持有Java buffer | Mutex 不延长 Java Image buffer 生命周期 |
| Core keep-alive | MainService CPU/Wi-Fi locks、60s ticker、receivers | `onDestroy` 取消 ticker/receiver 并释放 locks | HandlerThread/executors 未见配对 quit/shutdown |
| Touch-block | Accessibility service overlay、100ms watchdog、500ms remote activity window | disable、`onDestroy` 移除 overlay/watchdog | 与 blank overlay 独立 |
| Dev selector | `DevAutoSelectorController.forService` 的 service-scoped instance | pause/close/release 清 Handler callbacks；Accessibility destroy 调 release | screenshot callback 与 service replacement 仍需竞态验证 |
| Flutter session aggregate | `FFI(SessionID?)` 每次创建 session/global-commented models | `FFI.close` 标 closed、leave voice、清 terminal/image/FfiModel/status、关闭 native session | 注释“global”不改变实际每个 FFI 都构造的事实 |
| Reconnect/waiting | `FfiModel` session timers | `clear`、peer info、cancel 分支关闭 reconnect；真实 frame 清 waiting | 延迟一次性 Timer 有 closed guard，未全部保留 handle |
| `ServerModel` 500ms timer | 每个 FFI 的 ServerModel constructor | 未保存 handle，未见 dispose/cancel | `FFI.close` 不关闭该轮询，需修复前评估真实实例数 |
| Session event subscription | `FFI.start` 中 `stream.listen` | 未保存 subscription；callback 检查 closed，native sessionClose | 不足以证明 subscription 已释放；已开始的 async callback 仍须检查 |
| ADB page timers/controllers | `_AdbPageState`：100ms output / 700ms debug timers | `dispose` 取消 timer 并 dispose controllers | stop service 不取消 output timer；in-flight Future 仍可返回 |
| ADB process | `CloudSendAdbManager` singleton runner | explicit stop 关 shell/kill local ADB；generation 防旧 shell reader 回写 | 页面销毁不等于 native process 已停；并发 start/stop/pair 非统一锁 |
| ZEGO engine/media | `ZegoVoiceCallModel` static engine + engine-local active model，per-model payload/timers；Rust controller 另有 process owner mutex | `leave` stop play/publish、logout、清 timers/state；engine 可保留复用 | Dart static 与 Rust `ZEGO_VOICE_CALL_OWNER` 是不同层的锁；多进程边界需实测 |
| Desktop window registry | `CloudSendMultiWindowManager` 按 type 保存 active/inactive window IDs | `_closeWindows` 保存位置并关闭窗口、清 type registry | hide/reuse/close 不等价；失败中止时 registry 需实测 |

## 4. Core、授权与帧的完整链

### 4.1 Core startup / boot / replacement

`runMobileApp` 先调用 `androidChannelInit`，再调用 `syncAndroidServiceAppDirConfigPath()`，然后 await `ensureCoreService()`。后者通过 MethodChannel `ensure_core_service` 启动或绑定 MainService，并执行 Rust `mainStartService`。Activity `init_service` 是 core-only alias，`check_service` 也会 ensure service。

配置同步 helper 是 `void` 且内部未 await Future，因而只证实调用顺序。Activity `SYNC_APP_DIR_CONFIG_PATH` 写 SharedPreferences；若 MainService 已存在再调用 JNI `xt4P9mWE` 补同步。service-before-UI 或 delayed channel 的完成时序仍须 AND-01 / FLT-05 证明。

`BootReceiver.onReceive` 检查用户 boot 配置和必要前置权限后启动 `ACT_INIT_MEDIA_PROJECTION_AND_SERVICE`，不携带投屏 result。MainService 无 result 分支只 keep core。Manifest 还列 QUICKBOOT action，但 receiver 条件仅处理 BOOT_COMPLETED / DEBUG_BOOT_COMPLETED；不能仅看 manifest 宣称 QUICKBOOT 已实现。

unexpected `onDestroy` 保留 JNI GlobalRef、写 core-ready 意图并以 5s cooldown 控制 500ms restart；explicit `destroy()` 才清 JNI context。screen/network/memory callbacks 刷新 keep-alive，不把连接状态变化当作 destroy/restart 依据。旧 Service 尚可被 JNI 引用、HandlerThread/executors 缺少配对停止，属明确需要 device/lifecycle 验证的窗口。

### 4.2 新 MediaProjection 授权

唯一当前正常代码调用入口是 Activity `start_screen_share` 和显式远端 share command。`start_screen_share` 已 `_isStart` 时直接返回；否则显式 `restoreMediaProjection(..., allowPermissionPrompt=true)` 或 permission Activity。`XerQvgpGBzr8FDFr.onCreate` 每次创建 capture intent，result 成功才将 intent 交给 MainService。

MainService 保存 intent 仅限 SDK < 34；`reuseVirtualDisplay = SDK < 34`。`restoreMediaProjection` 默认 `allowPermissionPrompt=false`，但函数开头总执行 `armOpenShareIgnoreBridge`，所以默认 false 仅禁止授权弹窗，不能把函数视为无副作用的普通 refresh。当前搜索到的两处调用均来自显式 start/share；不要在 reconnect 中直接复用此 helper。

`createOrSetVirtualDisplay` 的 SecurityException/普通异常只调用 `handleProjectionStoppedKeepService`，不请求授权。`startCapture` 在完成 VD 建立后才置 `_isStart=true`；`captureStarting` 暂时接收早到帧，失败回收清 flag。Android 14/15 平台约束是源码设计意图和 SDK 分支，实际 ROM 仍属 external / verification-required。

### 4.3 Normal / SKL / ignore / black recovery

```text
Normal: MediaProjection → VirtualDisplay → ImageReader.acquireLatestImage().use
        → plane ByteBuffer → pkg2230.kt::yy4mmhjJ → FrameRaw.update(pointer)
        → scrap::common::android::Capturer::frame → Rust video_service → peer VideoFrame
        → controller decode → EventToUI_Rgba / EventToUI_Texture → FFI.onEvent2UIRgba

SKL:    Accessibility hierarchy → EqljohYazB0qrhnj.a012933444444
        → imageBuffer → MainService.createSurfaceuseVP9 → b6L3vlmP → VIDEO_RAW

Ignore: Accessibility.takeScreenshot → HardwareBuffer/Bitmap
        → EqljohYazB0qrhnj.a012933444445 → imageBuffer
        → MainService.createSurfaceuseVP8 → T1s73AGm → VIDEO_RAW
```

Normal callback 要求 `_isStart || captureStarting` 且 `!SKL && !shouldRun`。`createSurfaceuseVP9/useVP8` 名称不能直接当作 codec 证明：这里是不同 raw frame producer；MainService `useVP9=false`，最终 encoder 仍属于 Rust video service。Screenshot continuous gate 是 `shouldRun`；one-shot gate 是 `isOneShotScreenshotFrame`，发送后消费并恢复 `PIXEL_SIZEBack8` gate。

blank 的 `BIS`、`gohome`、本机 overlay、brightness 及 JNI raw-pixel recovery 共同影响画面。touch-block 使用另一透明 overlay，远端活动触发临时 passthrough，不能把黑屏 display overlay 和 input blocker 合并。SKL 停止和 blank 关闭可显式请求 one-shot clean screenshot；这也不等于 waiting 自动 fallback。

### 4.4 PC waiting 与 endpoint 平台 fallback 的区别

`FfiModel.showConnectedWaitingForImage` 约 200ms/1200ms/10s 请求 `sessionRefreshVideo`；MainService `forceVideoFrameRefresh` 要求 normal share 完整活跃并排除 SKL/ignore，只触发 Rust refresh，不重绑 VD。授权 add_connection 在 200/900/1800ms 再发正常 refresh。

交叉复核补充：PC refresh 到 `Connection::refresh_video_display` 还设置 `OPTION_REFRESH`，video loop据此`SWITCH`；Kotlin refresh 到 `src/flutter_ffi.rs::server_side::Java_pkg2230_ClsFx9V0S_qR9Ofa6G` 仅调用`video_service::refresh`，Android实现为`Display::refresh_size`。后者不直接武装`FrameRaw.force_next`；只能称refresh请求，不保证产生新首帧。

源码中存在 Android 平台事件自动 fallback：screen-off 在 800/1800ms 延迟检查“之前有 share、当前仍 screen-off、Accessibility open”后启动 ignore；projection-stop 可在 `shouldRun || screenOffActive` 条件下启动。此处是现有 endpoint 行为，不能从 ADR-0006 推断其不存在。PC waiting/reconnect 仍不应自动切换这些帧源。

`FFI.onEvent2UIRgba` 同时被有效 RGBA 与受支持 Texture 分支调用，清 waiting timer/dialog/overlay，再清 first-image flag。它证明收到渲染事件/数据，不能单独证明屏幕上已经成功显示像素。

`_startAndroidAutoReconnect` 使用一个 2500ms timer、300ms guarded first try、60s prompt timer；每次强制 relay。只有 `hasRetry` 且 `_isRecoverableAndroidConnectionError` 判定可重试的 Android error 才进入；offline/failed/resolve/handshake 等关键字会排除。peer info 到达即停 retry timer，并非等待首帧才停。密码取 session → process peer cache → build-in default remote password，不取本机 permanent password。

## 5. 输入、侧按钮与 Dev 全链

`overlay.dart::DraggableMobileActions` 回调经 `common.dart` 绑定 `InputModel.onScreen*`；`InputModel.sendMouse` 组装 JSON。`src/flutter_ffi.rs::session_send_mouse` 合成 `mask = type | buttons << 3`，`ui_session_interface.rs::send_mouse` → `client.rs::send_mouse` → protobuf `MouseEvent.url`。`Connection::on_message` 在 `self.authorized` 分支接收后调用 active `pkg2230::call_main_service_pointer_input`。

| 功能 | Dart type / mask | JNI → Kotlin sink |
|---|---|---|
| blank | `wheelblank` / 37 | `start_overlay` → Accessibility `onstart_overlay`，修改 overlay/brightness/pixel gates |
| browser | `wheelbrowser` / 38 | MainService pointer input → Accessibility `onMouseInput` → browser |
| SKL | `wheelanalysis` / 39 | JNI `start_capture` → Accessibility `onstart_capture` |
| ignore | `wheelback` / 40 | `stop_overlay` → `startIgnoreFallback` / `stopIgnoreCapture` |
| share | `wheelstart` / 41 | `start_capture2` → explicit restore / stop-share-and-ignore |
| legacy stop mapping | `wheelstop` / 42 | FRB mapping 存在；未找到 Dart sender 或 active JNI mask 42 专用 sink，不能宣称当前可用 |
| touch block | `wheeltouch` / 43 | `touch_block` → Accessibility `setTouchBlockEnabled` |
| Dev selector | `wheeldevselector` / 44 | `dev_selector` → `handleDevSelectorCommand` → `DevAutoSelectorController.handleCommand` |

同名异义必须记忆：Activity MethodChannel `start_capture` 是 non-authorizing normal refresh；JNI set-by-name `start_capture` 是 SKL toggle。协议 payload prefix 只是解析标识，不是鉴权。

`InputModel.sendMouse` 明确豁免六类 Android custom commands 的本地 keyboard permission；endpoint Android Mouse/Pointer/Key 分支亦未执行 desktop 分支的 `peer_keyboard_enabled()`。它们有 session authorized 与 view-camera 排除，但缺少该 capability gate。因此不能说“远程未经认证”，也不能说“关闭输入开关已形成完整 Android endpoint enforcement”。

Dev selector 是实际有路由的自动化功能：解析 start/pause/stop/close/progress，limit 范围 1—9999、delay 200—60000ms；限制目标窗口，按 blank / API level 分支使用 accessibility nodes、screenshot 或 coordinate fallback，`dispatchGesture` 点击/滚动。UI 口令和目标 package 条件均不替代 session capability。维护它须同时核查隐私、blank recovery、service destruction 和 pause/revoke。

## 6. ADB 与 ZEGO 边界

### ADB

`CloudSendAdbManager` 是 application-context singleton facade；Activity handler 对较长操作使用 worker thread 并在 UI thread 返回结果。Runner 从 `nativeLibraryDir/libadb.so` 启动进程，NSD 查找 connect port，pair/connect 尝试 loopback/local Wi-Fi 候选并维护 preferred serial。`startLocalShell` 单独使用 `sh -l`，不应混称 ADB shell。ADB pairing code 通过 stdin；报告不保存任何 port/device/code 实值。

`openShell` 非 local-shell 自动请求 `WRITE_SECURE_SETTINGS` grant。shell reader 以 generation 防旧实例回调，restart attempts 最大 3；输出显示缓存为 synchronized 16KiB ring。`runAdb/pair` 的 wait-before-read 和 `readText` 临时存储仍与该 ring buffer 不同；需要高输出和 stop/start 并发测试。

AdbPage 有 100ms status+output 双轮询，而 status 已携带 output；stop 不取消 timer，dispose 会取消。`_refreshTerminalOutput` 和 `_applyState` 有 mounted 检查，但 `_appendLocalLine` 内直接 setState，某些 await 后的错误/成功路径仍调用它。`supported = SDK >= 30` 从 native 返回，页面未依据该字段退出；port/code 只做有限非空检查。没有找到 PC remote ADB 的 protobuf/session 对接，不得把本地 shell 自动暴露给远程会话。

### ZEGO

邀请授权、broker token、麦克风 consent 是三层：Rust `VoiceCallRequest` 和 pending/active call state → Android MainService pending events/foreground notification → `server_page.dart::androidChannelInit` → `ServerModel.updateVoiceCallState` → accept → `zego_voice_call_ready` → `ZegoVoiceCallModel.joinFromJson`。

ServerModel 将 voice state Future 串行排队，按 native CM clients 清 stale flags；client remove 会取消 auto-accept timer，相关 call 会 leave。3s per-client auto-accept timer 是实际逻辑，dialog countdown 不是唯一 owner；对话框仅接受，back/cancel 调 submit。麦克风权限缺失时才请求 OS permission，拒绝则 `accept=false`；已有 permission 时仍会自动接受。这是现状，不是“明确用户 consent 已满足”的证明。

`ZegoVoiceCallModel.join` 等待 in-flight leave，忽略 duplicate/recently-closed payload，login room 后 publish/play；duration/play-retry/publish-watchdog timers 由模型清理。`mediaReady` 仅 `joined && publisherAudioFirstFrameSent && playerAudioFirstFrameReceived`，不直接检查 publisher/player state string。`leave` 不等于 destroyEngine；engine 可复用，Dart static owner 只在当前 Dart engine 内成立。controller 的 `src/client/io_loop.rs::try_acquire_zego_voice_call_owner` 另有 Rust process Mutex，不能因 Dart scope 而推断完全没有跨窗口 owner；同进程多 engine 与不同 native process 仍要分别验证。PC toolbar 与 `Data::NewVoiceCall` 没有 Android platform-string 前置限制，直接针对当前连接尝试邀请。

新增敏感日志发现：`androidChannelInit` 在分发前打印整个 arguments，voice-ready payload 的 JSON schema 含 `token`。这是源码证实的日志路径；具体 APK 是否保留/采集日志未验证。现有 broker transport/credential 风险在安全审计记录，不在本报告复制值。

## 7. 关键符号证据索引

ZEGO异步边界补充：voice state Future串行化不等于media操作串行化。`androidChannelInit` 对ready/closed分别启动unawaited join/leave；`ZegoVoiceCallModel.join`仅在入口等待已有leave，后续engine/config/login等await后没有generation/cancel检查。若leave发生于join中途，旧continuation存在继续置joined/publish的路径；这是`inferred / verification-required`，需测试“room login未完成即挂断”，不能写成已发生。

以下锚点均按本次 HEAD 实查；Kotlin 简写前缀 `K/` = `flutter/android/app/src/main/kotlin/com/cloudsend/app/`，只用于本表导航。

| 锚点 | 证明的事实 |
|---|---|
| `flutter/lib/main.dart::main` / `runMobileApp` / `runMultiWindow` | platform、CM、install、window 分流；Android启动次序 |
| `flutter/lib/mobile/pages/home_page.dart::HomePageState.initPages` | mobile 当前 page 可达性与空列表分支 |
| `flutter/lib/utils/multi_window_manager.dart::CloudSendMultiWindowManager._newSession` / `_closeWindows` | tab/复用/新窗口/关闭 owner |
| `flutter/lib/models/native_model.dart::PlatformFFI._startListenEvent` / `syncAndroidServiceAppDirConfigPath` | global event stream、MethodChannel、未 await config helper |
| `flutter/lib/models/model.dart::FFI` / `FFI.start` / `FFI.close` | 每实例模型创建、session stream 与 teardown |
| `flutter/lib/models/model.dart::FfiModel._startAndroidAutoReconnect` | 2500ms、300ms、60s 与 forceRelay |
| `flutter/lib/models/model.dart::FfiModel.showConnectedWaitingForImage` | waiting 仅 normal refresh |
| `flutter/lib/models/model.dart::FFI.onEvent2UIRgba` | RGBA/Texture 公用清 waiting |
| `flutter/lib/models/model.dart::CloudSendStatusModel.updateFromEvent` / `_restartStaleTimer` | missing-key、8s stale / 8.5s timer |
| `flutter/lib/models/server_model.dart::ServerModel` / `ensureCoreService` / `startService` / `stopService` | 500ms timer 与 core/share 分离 |
| `flutter/lib/mobile/pages/server_page.dart::androidChannelInit` | JVM event consumer 与敏感 arguments 日志路径 |
| `flutter/lib/models/input_model.dart::InputModel.sendMouse` / `onScreen*` | UI command permission exception、payload |
| `flutter/lib/common/widgets/overlay.dart::DraggableMobileActions` / `DraggableMobileActionsDev` | 正常/Dev UI 和 controller lifecycle |
| `src/flutter_ffi.rs::session_send_mouse` | type/button → mask |
| `src/ui_session_interface.rs::send_mouse` / `src/client.rs::send_mouse` | session → wire MouseEvent |
| `libs/hbb_common/protos/message.proto::MouseEvent` / `VoiceCallRequest` | 自定义 url 字段和 voice metadata schema |
| `src/client/io_loop.rs::try_acquire_zego_voice_call_owner` / `release_zego_voice_call_owner` | controller Rust process 级通话 owner |
| `src/server/connection.rs::Connection::on_message` | authorized 后 Android input 未检查 keyboard capability |
| `src/server/connection.rs::cloudsend_status_message` / `Connection::send_logon_response` | immediate status 尝试与有效 JSON gate |
| `src/ui_cm_interface.rs::remove_connection` | PC 移除不发送 Android stop capture |
| `libs/scrap/src/android/mod.rs::pkg2230` | active module 唯一导出 |
| `libs/scrap/src/android/pkg2230.rs::FrameRaw::update/take` | 外部 raw pointer 保存后延迟复制 |
| `libs/scrap/src/android/pkg2230.rs::Java_pkg2230_ClsFx9V0S_yy4mmhjJ` | normal DirectBuffer producer |
| `libs/scrap/src/android/pkg2230.rs::call_main_service_pointer_input` | mask 37/39/40/41/43/44 特殊命令 |
| `libs/scrap/src/android/pkg2230.rs::Java_pkg2230_ClsFx9V0S_VHsFQTvK` | explicit destroy 清 JNI context，compat ffi 无同名功能 |
| `libs/scrap/src/common/android.rs::Capturer::frame` / `PixelBuffer::new` | raw → owned rgba → pixel buffer / stride |
| `flutter/android/app/src/main/kotlin/pkg2230.kt::ClsFx9V0S` | active Kotlin native symbol declarations |
| `K/oFtTiPzsqzBHGigp.kt::initFlutterChannel` / `onDestroy` | channel dispatch、绑定解除；不等于 service destroy |
| `K/XerQvgpGBzr8FDFr.kt::onCreate` / `onActivityResult` | 新 capture intent、result delivery |
| `K/BootReceiver.kt::onReceive` | boot 条件与 core-only intent |
| `K/DFm8Y8iMScvB2YDw.kt::onCreate` / `onStartCommand` / `onDestroy` | core init、no-result 分支、restart/teardown |
| `K/DFm8Y8iMScvB2YDw.kt::startCapture` / `createSurface` / `createOrSetVirtualDisplay` | 首帧窗口、Image.use、VD failure |
| `K/DFm8Y8iMScvB2YDw.kt::restoreMediaProjection` / `armOpenShareIgnoreBridge` | prompt gate 不等于无副作用 |
| `K/DFm8Y8iMScvB2YDw.kt::startScreenOffIgnoreFallbackIfNeeded` / `handleProjectionStoppedKeepService` | endpoint 平台 fallback |
| `K/DFm8Y8iMScvB2YDw.kt::forceVideoFrameRefresh` / `handleAuthorizedConnectionForVideoRefresh` | normal-only refresh / 200-900-1800ms |
| `K/DFm8Y8iMScvB2YDw.kt::updateScreenInfo` / `calculateIntegerScaleFactor` | scale divide-by-zero 和 raw/scaled 比较 |
| `K/nZW99cdXQ0COhB2o.kt::onstart_capture` / `onMouseInput` / `onKeyEvent` | SKL、gesture、keyboard sink |
| `K/nZW99cdXQ0COhB2o.kt::setTouchBlockEnabled` / `onDestroy` | overlay/watchdog owner 与释放 |
| `K/EqljohYazB0qrhnj.kt::a012933444444/a012933444445` / `imageBuffer` | shared screenshot/hierarchy buffer |
| `K/DevAutoSelectorController.kt::handleCommand` / `selectNext` / `release` | 实际 Dev automation 和 cleanup |
| `flutter/lib/mobile/pages/adb_page.dart::_ensurePolling` / `_appendLocalLine` / `dispose` | poll 重入和 async-dispose 风险 |
| `K/adb/CloudSendAdbManager.kt::initialize` / `start` / `setWirelessDebugging` | singleton facade 与 supported 字段 |
| `K/adb/CloudSendAdbRunner.kt::openShell` / `runAdb` / `append` | privilege command、wait/read、16KiB cache |
| `K/adb/CloudSendAdbDnsDiscover.kt::discoverConnectPort` | NSD port discovery / retry |
| `flutter/lib/models/server_model.dart::_startVoiceCallAutoAcceptTimer` / `showAutoAcceptVoiceCallDialog` / `onClientRemove` | consent 现状和 per-client cleanup |
| `flutter/lib/models/zego_voice_call_model.dart::join` / `leave` / `mediaReady` | SDK media lifecycle 与真实 readiness predicate |

## 8. 发现与文档纠偏

| ID | 发现 / 纠偏 | 证据等级 | 下一验证 |
|---|---|---|---|
| AF-01 | Normal plane pointer 在 Image.use 结束后仍由 FrameRaw 保存；Rust 后续才复制，缺 ownership proof | `verified` 路径，UAF 后果 `inferred` | AND-02、RST-03/04；CheckJNI/sanitizer |
| AF-02 | `static mut PIXEL_SIZE*` 多线程读写、shared imageBuffer 非统一同步 | `verified` 实现，实际 race 触发待验证 | RST-04 / AND-02 |
| AF-03 | Android authenticated input/custom commands 缺 keyboard capability gate；UI 特意豁免六类命令 | `verified` | AND-05、NET-04；修改需产品策略确认 |
| AF-04 | 3s voice 自动接受、cancel=accept；MethodChannel arguments 可能包含 token 被打印 | `verified` 路径；产物日志影响待验证 | AND-07、API-08、FLT-06 |
| AF-05 | `w / 350` 在 1—349 可得 0 再除；SCREEN_INFO scaled-width 与 raw-width 比较可反复 rebind | 前者 `verified` 条件，后者 `inferred` | AND-03 |
| AF-06 | 非显式 destroy 保留旧 JNI；MainService worker teardown 缺对应关闭 | `verified` 实现 / lifecycle 后果 `inferred` | AND-01、RST-04 |
| AF-07 | FFI 每次构造 ServerModel 无 handle 的 500ms timer；StatelessWidget 自定义 dispose 无框架调用 | `verified` | FLT-02/04 |
| AF-08 | ADB 有 16KiB 显示缓存，但 process wait-before-read、重入轮询、async setState 和 shell owner 仍需验证 | `verified` 结构 / 后果 `inferred` | AND-06、FLT-04 |
| AF-09 | config-path helper 未 await；主页非 Android/outgoing-only 可空列表 | `verified` 代码条件，release 可达性待验证 | AND-01、FLT-02/05 |
| AF-10 | `wheelstop` 只有 mapping，不足以证明 active stop；MethodChannel/JNI 同名 start_capture 不同义 | `verified` 当前检索和链路 | FLT-05、NET-05 |
| AF-11 | 禁止 waiting fallback 不等于 endpoint screen-off 无 fallback；status stale 清理约 8.5s | `verified` | AND-01/02/04 |
| AF-12 | ZEGO mediaReady 不含 state-normal 条件；Dart engine owner 与 Rust process owner 是两层 | `verified` 结构；跨 engine/process recovery 待验证 | AND-07、FLT-02/06 |
| AF-13 | join中途leave后旧async continuation可能继续joined/publish；voice-state queue并未取消media Future | `inferred / verification-required` | AND-07、FLT-04；login未完成即挂断 |

旧 Android runtime 文档保留为历史线索；其 `mediaReady` 描述包含 publisher/player normal，与源码 predicate 不同。现有 `04_ANDROID_PIPELINE.md` 已根据本报告校正；未删除、移动或重写旧历史。所有安全发现仍需独立产品/安全决策，不在接管任务中顺手改业务。

## 9. 未来修改定位与正式验证需求

| 用户后续需求 | 首读位置 | 必须一起核对 |
|---|---|---|
| 开共享/授权反复/断线后丢共享 | MainService capture/restore/stop + Activity handlers | ADR-0004、permission Activity、core/share/frame/waiting 四层；AND-01/02/04 |
| 首帧等待/静态画面 | FfiModel waiting + MainService normal refresh + FrameRaw | RGBA/Texture 两路、data ownership、force_next；AND-02、FLT-03、RST-04 |
| 黑屏/无视/穿透/防触 | InputModel + pkg2230 masks + Accessibility + image helper | UI→wire→JNI 全链、brightness/pixel recovery、授权；AND-02/05 |
| Dev 自动化 | DraggableMobileActionsDev + DevAutoSelectorController | 目标窗口、stop/revoke、privacy、blank overlay；AND-05 |
| local ADB 稳定性 | AdbPage + CloudSendAdbManager/Runner/DnsDiscover | native process owner、polling、settings automation、provenance；AND-06/08 |
| 语音接听/无音频/忙状态 | ServerModel + ZegoVoiceCallModel + Rust invitation state | token scope/TLS、explicit consent、microphone、pending cache、日志脱敏；AND-07、NET-07、API-08 |
| 多窗口/关闭后泄漏 | main.dart + MultiWindowManager + FFI/ServerModel | engine/session/window IDs、stream subscription、timer/controller teardown；FLT-02/04/05 |

《编译验证需求》：本报告不执行以下命令。

- Android：在 E-A 正式 Linux 构建环境、native assets/hash/toolchain 已确认后，仓库根目录 `./build.sh 1`、`./build.sh 2`；目标 AND-08 的 ABI/JNI/build evidence，随后以独立设备授权执行 AND-01—07。
- Flutter：在 E-F 项目锁定 Flutter/Dart 环境，`flutter/` 目录运行经批准的 `flutter analyze`；测试 discovery 须先确认，不能把 `flutter/test/cm_test.dart` 手动 harness 当自动化 suite。目标 FLT-01—07；本轮均 NOT_RUN。
- Windows controller：E-W 正式环境仓库根目录 `new-build.cmd`；结合 E-A/E-N 实测 AND-04、FLT-02/03、E2E-01，禁止以仅编译成功替代跨设备回归。
- 内存/权限/语音：E-A/E-N/E-P 隔离测试环境执行 AND-05/07、RST-03/04、NET-04/07、API-08；明确 secret redaction 和设备恢复方案，保留 build hash、OS/ROM、steps、oracle、结果与 reviewer。

本次验收只包含文档路径/符号/源码关系的 V0 复核。所有 runtime/security/interop oracle 保持 `verification-required`，没有新增 PASS 声明。

V0 复核记录：本报告提取的 34 个唯一完整源码/配置路径（含 `K/` 展开）均存在；逐项复核关键 symbol 和新增 findings；`git diff --check` 对领域文档无 whitespace error，只有仓库 LF→CRLF 提示。新增报告为 untracked 文件，另做路径/文本检查，不能将 tracked diff check 冒充对新增文件的 Git 校验。没有依赖安装、编译、测试、设备执行或 Git 写操作。
