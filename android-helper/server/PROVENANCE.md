# Shell helper 源码来源

日期：2026-10-02。状态：源码适配完成；编译、运行和设备验证 `NOT_RUN`。

官方上游：[`Genymobile/scrcpy`](https://github.com/Genymobile/scrcpy)。仅从官方 GitHub API 和 raw.githubusercontent.com 读取文本源码；未获取或运行预编译二进制，未安装依赖。

- Tag：`v4.1`。
- Annotated tag object：`49c9501fb26f456bbf4a341dd68879f670c67452`。
- 完整 commit：`2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0`。
- Tag 与 commit 由官方 `git/ref/tags/v4.1`、对应 `git/tags/<object>` 两步核实。
- 上游许可证：Apache-2.0，完整文本保存在 [LICENSE.scrcpy](LICENSE.scrcpy)，归属信息见 [NOTICE](NOTICE)。所检查的 tag 根目录有 LICENSE，没有 NOTICE 文件。
- 本模块不是上游源码的完整 vendor 副本；以下是已阅读并适配的来源，以及原始 UTF-8 文件 SHA-256。当前改编文件的 hash 将由正式 helper 构建清单另外记录，不能与上游 hash 混用。

| 原始文件（位于该 commit） | SHA-256 | 本地使用 |
|---|---|---|
| `LICENSE` | `01c12035bf35af37241298dc7ad538eb2a07e5c940437bc6876feeaa9d1951d0` | 原文随附 |
| `server/src/main/java/com/genymobile/scrcpy/video/ScreenCapture.java` | `8b34e8fec43ccf3e333df4658eb07c5dfd2bcb741b56297caf5afd66bf7cb9ee` | DisplayCapture 的 mirror/fallback 生命周期 |
| `server/src/main/java/com/genymobile/scrcpy/video/SurfaceEncoder.java` | `f37c238b748608b1cfcedf9ea411793e208b085f22350e4f70ee8b835820abee` | VideoEncoder 的 Surface/MediaCodec 格式与释放顺序 |
| `server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayManager.java` | `4d83c7bdf476efa5d99f314689a5d3355efc9cae3b9efaa138fe41d753e744a8` | DisplayManagerGlobal 信息查询、hidden mirror 方法签名 |
| `server/src/main/java/com/genymobile/scrcpy/wrappers/SurfaceControl.java` | `9598fea63e5acda1931e51005397866633263c6142522da596b18a0918c0aa1e` | SurfaceControl 创建、事务、surface/rect/layer 与销毁反射 |
| `server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java` | `c3ae939007213824d933d069df9196897a43124dc81f4475e10b471576bf65c5` | 2026-10-03 新增 DisplayPower 的 Android 14+ physical token 查询与系统库载入签名 |
| `server/src/main/java/com/genymobile/scrcpy/Workarounds.java` | `1852d8c10cfaf583df7d265f01eb73abd72f509845f99f71e15fb879835d0a08` | ShellEnvironment 的 ActivityThread、Samsung 配置及 ONYX 分支 |
| `server/src/main/java/com/genymobile/scrcpy/FakeContext.java` | `a8d740e8f802b851138b731fdd5f75a8722413849595debf5408012b1336063d` | ShellContext 的 shell package/attribution；不引入 provider/audio/clipboard |
| `server/src/main/java/com/genymobile/scrcpy/Server.java` | `290f195e42419f24ee0e518fc2970c87492a76cf10428afce1e1c14e79b32bf9` | 可退出 main Looper 与退出 framework 线程的进程收尾策略 |

原始链接固定为：`https://raw.githubusercontent.com/Genymobile/scrcpy/2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0/<原始文件>`。

## Non-secure mirror 的 AOSP 核对

`DisplayCapture.start()` 主分支调用 hidden static `DisplayManager.createVirtualDisplay(String, int, int, int, Surface)`。已只读核对官方固定 tag [android-14.0.0_r1](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/core/java/android/hardware/display/DisplayManager.java) 与 [android-16.0.0_r1](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/hardware/display/DisplayManager.java)：该方法内部 `VirtualDisplayConfig.Builder` 只设 `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`，没有 `VIRTUAL_DISPLAY_FLAG_SECURE`，权限要求是 `CAPTURE_VIDEO_OUTPUT`。SurfaceControl fallback 显式传 `secure=false`。

这是本适配没有请求 secure display 的源码证据，不是对所有 OEM 实现的运行证明，也不承诺 `FLAG_SECURE`、DRM 或受保护内容可见。相关页面仍列入正式设备验证；原型不提供绕过选项。

## 刻意缩减与差异

1. 不使用 upstream `Server`、`Options`、`Controller`、`Streamer` 的协议或全部依赖；使用 Tunnel 自己的 [AdbWire](../protocol/src/main/java/com/tunnel/adb/protocol/AdbWire.java)。
2. 仅 UID 2000、Android API 30—36、display 0；不降 root 权限后继续，不改变权限、系统设置或无障碍配置。2026-10-03 新增受限 display0 physical power，恢复自己改动，不使用 POWER toggle。
3. AOSP static mirror 只请求 AUTO_MIRROR；SurfaceControl fallback 始终 `secure=false`，不同于 upstream Android 11 的 secure-display 分支；本原型不请求受保护 buffer。
4. 2026-10-03 新增旋转后 codec 重建、独立 UiAutomation screenshot/node renderer 和 bitmap EGL encoder；无裁剪、多屏、自动分辨率重试。网络 epoch 和 PC 首帧事务由宿主处理，不复用上游协议。
5. 仅硬件 H264 + Surface；只处理有界 Annex-B，AVCC、partial access unit、编码尺寸漂移直接失败，不发送未经解析的“视频”。
6. 仅构建源码原型，不能把 upstream 已支持的机型当作本适配已验证。供应链、许可证组合、产物 hash 和正式分发仍需外部资产/发布流程验收。

## 2026-10-03 UiAutomation 与辅助源

已只读核对官方 [AOSP android-16.0.0_r1 UiAutomation.java](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/app/UiAutomation.java)：隐藏构造 `(Looper,IUiAutomationConnection)`、`connect(int)` 和 `disconnect()` 通过反射适配；显式使用公开 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`。有限输入、semantic node renderer、bitmap/EGL 和 endpoint consent 由 Tunnel 自行实现，不复制 upstream Controller。未调用 executeShellCommand、权限代用或 secure settings 写入。上述证据是签名/权限边界核对，正式构建与 Android 16 两机验证仍 `NOT_RUN`。

## 2026-10-03 T005 会话生命周期

Tunnel自有AdbWire升级VERSION3并同步HMAC域：Bootstrap durationSeconds=0表示生产会话持续至EOF/撤销/退出/故障，无一小时截止；1—3600保留有限诊断。Server.captureStarted只取消生产使用期限，启动/心跳/操作/写阻塞watchdog仍保留。P0仍20秒helper/10秒采样。上述修改不是upstream scrcpy原版行为；实际产物由本地source hash/manifest确定，二进制与设备验收NOT_RUN。

## 协议 4 与黑屏遮罩

Tunnel 自有 Bootstrap 增加 initialMode，记录从 70 扩为 74 字节；新增 OVERLAY_BLACK 固定操作和 CAP_OVERLAY，APK/helper/Gradle/供应清单同步为协议 4。黑屏侧按钮不再调用物理 power 操作。BlackOverlay 使用独立 shell SurfaceControl 色层及 setSkipScreenshot；依据 [AOSP Android 12 SurfaceControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/view/SurfaceControl.java) 和 [Android 16 固定 tag](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/view/SurfaceControl.java) 的截图、镜像与录制排除语义自行实现，未复制上游文件。隐藏 API 运行时反射探测；不支持则返回失败，不使用 secure layer 或物理关屏替代。源码核对不代表 OEM 真机验证。
