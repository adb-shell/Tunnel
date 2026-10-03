# 本机 ADB capture helper

2026-10-03 状态：P0—P6 源码接入中，`NOT_BUILT / NOT_RUN`。远程验收必须覆盖 helper、APK、Rust relay、PC decode/presentation，不能从本地诊断 ready 推断完成。

| 文件 | 职责 |
|---|---|
| `Server.java` | shell UID/API gate、双通道认证、有限操作队列、EOF/心跳/启动超时及有限诊断期限 |
| `ShellEnvironment.java` | shell ActivityThread/context、可退出 main Looper |
| `DisplayCapture.java` | display 0 non-secure mirror、版本固定的反射适配 |
| `VideoEncoder.java` | 硬件 H264 Surface encoder、CSD/IDR、旋转重建和源切换 |
| `ShellAutomation.java` | 独立 UiAutomation、固定输入、截图、节点和 semantic Canvas |
| `BitmapSurface.java` | bitmap → EGL/GLES → encoder Surface；video 线程独占 GL |
| `BlackOverlay.java` | shell 黑色合成层；SKIP_SCREENSHOT 排除远端录制，不建立输入窗口 |
| `DisplayPower.java` | 保留的 display 0 physical power 原语；侧栏黑屏不使用它 |
| `H264AnnexB.java` | 有界 SPS/PPS/IDR 与 access unit 格式检查 |

编译输入：本目录和共享 `../protocol/src/main/java`，Java 8、SDK API 34、D8 min-api 30。无额外 Java 库、NDK 或完整 scrcpy SDK。来源和固定 commit 见 [PROVENANCE](PROVENANCE.md)；产物必须带 [NOTICE](NOTICE) 和 [LICENSE.scrcpy](LICENSE.scrcpy)。

## 启动与权限

APK 持有全进程唯一 mirror lease，核验目标为本机且 shell UID=2000，再验证包内 helper hash、推送自身随机目录、核验远端 hash。helper 无 argv；自身再次拒绝非 UID 2000 或 API 30—36。stdin 只接受固定 Bootstrap，随后 EOF/额外字节撤销。

APK listener 只能绑定 `127.0.0.1`。helper 依次连接 VIDEO、CONTROL，双方 nonce/HMAC 认证完成后才初始化 Android API。helper 为协议 server，APK 为 client，与 TCP 发起方向无关。HMAC 不加密本机媒体；公网媒体只允许走现有认证、加密、relay-only 的远程会话。

`TunnelAdbRuntime` 是当前已授权连接的 scope 边界（PC显式pair/authorize，fresh probe；ADR-0016/0017）：video/input/accessibility/display/snapshot/hierarchy/overlay 独立；远端只可提交本机无线调试的配对/连接端口，不能指定外部 target、文件、shell 或任意授权 scope。默认未授权时仅回 `SESSION_ADB_AUTHORIZATION_REQUIRED`。UiAutomation 使用 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`，从不修改 `enabled_accessibility_services`，不调用 executeShellCommand/adoptShellPermissionIdentity。本应用无障碍服务关闭后，helper 的节点/截图/输入来源仍是自己拥有的 UiAutomation。

## 源和首帧

- live：non-secure display mirror → hardware H264。
- snapshot：UiAutomation screenshot → bounded bitmap → EGL → 同一 H264 wire。
- hierarchy：UiAutomation 独立节点 → 有界 semantic Canvas → EGL → 同一 H264 wire。它是结构图，不是受保护像素捕获；password 文本不输出。
- 单一 `captureMode` 互斥三者。选择 alternate 先取得真实 bitmap，失败保留当前模式；后续 provider 丢失或 EGL/codec 失败，仅停止该视频任务并报告失败，control/helper 保留；endpoint 接收仍运行的普通画面。
- 侧栏开/关共享只操作普通 MediaProjection。授权后单个 helper 持续拥有 UiAutomation、输入和黑罩，Bootstrap initialMode=3 启动；VIDEO_TASK 以 taskId 替换内部 encoder。关闭视频不关闭控制连接，禁止另外启动 UiAutomation 争抢同一服务。VIDEO read 由独立 control 心跳取消，静态画面先请求关键帧；视频超时不撤授权。
- VIDEO_CONFIG flags 标识实际 helper source；源/尺寸/rotation 变化重建 codec、增加 revision、释放按住输入。Runtime 先冻结输入并通知 RECONFIGURE，Rust 给新 network epoch，然后发送 CONFIG/IDR。只有 PC decode ready → Activate → 真实 Presented ACK 后才确认视频源；输入使用独立控制与当前显示几何。收到 config、socket 通或服务活着均不算首帧。
- 不请求 secure display/buffer，不承诺 FLAG_SECURE、DRM 或 OEM 安全层可见。截图和树可用性按实际 API 结果变化。

## 有界资源与恢复

启动 helper 15 秒、APK 含 probe/stage 45 秒；本机 control 每秒 PING，5 秒无有效 control 停止。每个方向 sequence 连续，command ID 严格递增；最多 64 条排队操作、每秒 240 条 control、操作 10 秒、写阻塞 2 秒。Runtime 控制者心跳 15 秒过期；生产 Bootstrap durationSeconds=0，无固定使用期限；断连/撤销/退出ADB/故障清理。有限诊断1—3600秒、P0 20秒，旋转不延期。

VIDEO packet 拷贝 codec buffer，outputBuffer 总在 finally 释放；截图最长边 1280，树最多 1024 节点/depth32/text32KiB/JSON256KiB；跨线程 bitmap 槽最多一个，旧图 recycle。bitmap 与 GL 资源归明确线程所有。control 只有固定数值操作，没有远程任意命令解释器。

停止释放输入、自己的 overlay/A11y pause、mirror/codec/GL/socket，关闭 UiAutomation，恢复自己关闭的物理 display；不清配对、不 kill-server、不重启 APK/core。清理 native binder/codec 卡住时 watchdog 最终只终止 helper PID。此时 physical display 恢复仍需真机验证，不能保证被杀死的 Java finally 能执行；物理电源键是本机恢复路径。

屏幕电源以反射签名和 display0 physical address 识别；失败撤销 capability，不注入 POWER toggle。普通 APK overlay 不能可靠区分物理触摸与 ADB 注入，因此防触默认由宿主返回不支持，不伪报成功。无障碍管理按当前会话独立授权执行，不依赖视频是否开启；disableSelf 后的重新开启走本机设置确认，避免覆盖其他服务配置。

## 服务器/真机验证需求（均未执行）

1. 正式协议测试和 Java/D8/APK/Rust/Flutter 编译；验证产物 provenance/hash 与配置相符。
2. 同意前拒绝、scope 越权、换 owner、旧 epoch/generation/operation 重放、双通道假监听和篡改、超长包、EOF、超时、背压。
3. 两台目标 Android 16 的 live/静态帧/旋转/折叠、CONFIG/IDR 解码和实际呈现 ACK；旧源不抢帧、无双重输入。
4. 无障碍共存/暂停/恢复/disableSelf、本机重新开启；拒绝输入、按住时断线和旋转不能留下 stuck key/touch。
5. snapshot/hierarchy 切换、无节点/截图失败、password/FLAG_SECURE 页面、EGL 失败和源回退。
6. 黑罩开/关、helper EOF/应用退出/被杀时遮罩清理、常亮锁释放、物理屏幕黑而远端画面保持可见；防触保持不可用直至有独立可靠 provider。
7. 健康连续运行超过一小时/持续重连和 100 次启动取消：fd/thread/native/GPU/bitmap 不持续增长、不残留 helper。
