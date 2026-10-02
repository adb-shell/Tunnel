# 本机 ADB capture helper（P0）

状态：`source prototype / NOT_BUILT / NOT_RUN`。入口 `com.tunnel.adbhelper.Server`；本模块仅在手机本机显式诊断时运行，不接 PC、relay、远程命令或正式视频链。

## 模块和构建边界

| 文件 | 职责 |
|---|---|
| `Server.java` | shell UID/API gate、bootstrap、双方认证、control、EOF、deadline、进程清理 |
| `ShellEnvironment.java` | shell 专用 ActivityThread/context、可退出 main Looper |
| `DisplayCapture.java` | 固定上游签名的 display 0 mirror/SurfaceControl fallback；检测尺寸/方向变化 |
| `VideoEncoder.java` | 硬件 H264 Surface encoder、CSD/IDR、显式关键帧、owned packet、释放 |
| `H264AnnexB.java` | 有界 Annex-B SPS/PPS 与 IDR 检查；不假装支持其他 bitstream |

构建输入为本目录 `src/main/java` 和 `../protocol/src/main/java`。Java source/target 8、Android SDK 34 `android.jar`、D8 `--min-api 30`；无第三方 Java 库、NDK 或 Gradle plugin 依赖。构建脚本与产物封装由 `android-helper` 根目录负责；本轮没有执行编译、测试、D8、APK 安装或设备命令。

官方来源、原始 hash 与修改说明见 [PROVENANCE](PROVENANCE.md)；随产物保留 [NOTICE](NOTICE) 和 [LICENSE.scrcpy](LICENSE.scrcpy)。未全量引入 scrcpy SDK。

## APK 调用合同

1. 只允许本机已验证 ADB 的独立无 PTY 子会话启动此入口，无 argv；helper 自身再次检查 UID 必须为 2000、API 必须在 30—36。
2. APK 先创建两个仅绑定 `127.0.0.1` 的 listener，通过 stdin 写入共享 `AdbWire.writeBootstrap()` 结构。secret 不放 argv、文件或日志。整个会话保留 stdin；EOF 表示父进程撤销，额外 stdin 字节导致退出。
3. helper 按 **VIDEO → CONTROL** 顺序连接；每条连接 helper 调用 `authenticateServer`，APK accept 后调用 `authenticateClient`。协议角色不是 TCP client/server 方向。
4. 两条通道认证均完成前，不初始化 capture/encoder，不调用 display API。认证失败、未知协议或包篡改会关闭会话。
5. 首个 CONTROL `CAPABILITIES(0,0)` 仅表示完成认证；encoder/surface 初始化成功后再推 `H264 / VIDEO|KEYFRAME`。这些能力位不证明已经产生可解码画面。
6. APK 每 1—2 秒 PING，helper PONG；只有 `REQUEST_KEYFRAME`、`STOP`、`PING` 三类控制输入。无任意命令、文件、输入注入、设置修改接口。
7. VIDEO 首先给 `VIDEO_CONFIG`（合并 Annex-B SPS/PPS，revision 从 1 起）；随后必须为 IDR。sequence 在每条通道/方向独立连续；PTS 单调微秒。收到帧不等于 PC 解码或呈现成功。
8. Bootstrap 规定最长边、fps、码率、1—30 秒 duration；当前 APK 接收采样窗口为 10 秒，发送 helper duration 为 20 秒，使正常收尾先由 APK 撤销 stdin 完成。duration 是 helper 独立硬上限。编解码能力失败时只给诊断错误，不偷偷切 MP、Accessibility、软件编码或再次申请权限。

协议是 loopback 上的双方身份与每包 HMAC，**不提供视频加密**；不允许扩大为公网/局域网 listener。未来远程通道需要独立的 endpoint scope 与可信传输，不能复用“本机认证成功”为远控授权。

## 生命周期和资源所有权

- helper 启动 deadline 为 15 秒；单 socket connect 3 秒、握手/control read 5 秒。APK 另外有包含身份核实、产物准备和采样的 45 秒总 deadline。
- capture 初始化后计 duration；duration 到期无真实发送帧则 `NO_FRAME_BEFORE_TIMEOUT`，不是成功。
- video/control 写阻塞 2 秒由独立 watchdog 关闭 socket；control 5 秒无有效消息即退出。
- 所有 packet 拥有 byte[]，没有跨线程存储已释放 MediaCodec ByteBuffer。每次 codec output 在 finally 中释放，主视频不经过字符串、Dart 或 stdout。
- codec/capture/surface 仅 video thread 创建和释放；control thread 仅写原子 keyframe flag。请求合并，关键帧间隔至少 500ms。
- stop 先撤销操作并关闭连接；video thread 随后释放 mirror、codec、surface。OEM native 调用卡住超过清理期限时只结束当前 helper PID；不调用 `kill-server`、不清配对、不停止 APK/core。
- stdout 不承载媒体或日志。stderr 只由本代码输出 `TUNNEL_ADB_P0:<固定错误码>`；APK 仍必须有界 drain 并丢弃 vendor/framework 的原始输出，不能向 UI/持久日志直接转发。

## 首轮验证和明确不支持项

以下需要后续明确授权后在正式工具链/真机执行；目前全部未运行：

| 验证 | 通过证据 |
|---|---|
| UID/API/破损bootstrap/假listener/错误secret/调换channel | 无capture，有限时间退出 |
| 两通道认证、initial/ready capabilities | 未初始化时不误报capture ready |
| 默认10秒静态及运动画面 | 实际CONFIG、IDR和frames；先接收、后单独验证解码/显示 |
| keyframe/stop/PING/EOF/客户端退出/不读视频 | 请求可取消、deadline成立，无残留helper或mirror |
| 旋转、折叠、display尺寸变化 | 明确 `DISPLAY_CHANGED_RESTART_REQUIRED`，不继续发送错误metadata |
| CSD分离/合并、非Annex-B/partial AU、异常buffer | 正确归类或拒绝，有界分配，不产生伪帧 |
| 100次启动/取消、native codec阻塞 | fd/thread/native资源不持续增长，强制退出仅作用于本helper |
| 受保护页面 | 尊重系统采集限制，不承诺安全内容可见 |

P0 不实现：远程投屏接入、PC呈现、输入、无视、节点、无障碍启停、显示电源、防触、音频、热旋转和多viewer。具备这些后续能力必须分别增加实现与验收，不能从此诊断 ready 推断。
