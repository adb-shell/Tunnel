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
| `server/src/main/java/com/genymobile/scrcpy/Workarounds.java` | `1852d8c10cfaf583df7d265f01eb73abd72f509845f99f71e15fb879835d0a08` | ShellEnvironment 的 ActivityThread、Samsung 配置及 ONYX 分支 |
| `server/src/main/java/com/genymobile/scrcpy/FakeContext.java` | `a8d740e8f802b851138b731fdd5f75a8722413849595debf5408012b1336063d` | ShellContext 的 shell package/attribution；不引入 provider/audio/clipboard |
| `server/src/main/java/com/genymobile/scrcpy/Server.java` | `290f195e42419f24ee0e518fc2970c87492a76cf10428afce1e1c14e79b32bf9` | 可退出 main Looper 与退出 framework 线程的进程收尾策略 |

原始链接固定为：`https://raw.githubusercontent.com/Genymobile/scrcpy/2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0/<原始文件>`。

## Non-secure mirror 的 AOSP 核对

`DisplayCapture.start()` 主分支调用 hidden static `DisplayManager.createVirtualDisplay(String, int, int, int, Surface)`。已只读核对官方固定 tag [android-14.0.0_r1](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/core/java/android/hardware/display/DisplayManager.java) 与 [android-16.0.0_r1](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/hardware/display/DisplayManager.java)：该方法内部 `VirtualDisplayConfig.Builder` 只设 `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`，没有 `VIRTUAL_DISPLAY_FLAG_SECURE`，权限要求是 `CAPTURE_VIDEO_OUTPUT`。SurfaceControl fallback 显式传 `secure=false`。

这是本适配没有请求 secure display 的源码证据，不是对所有 OEM 实现的运行证明，也不承诺 `FLAG_SECURE`、DRM 或受保护内容可见。相关页面仍列入正式设备验证；原型不提供绕过选项。

## 刻意缩减与差异

1. 不使用 upstream `Server`、`Options`、`Controller`、`Streamer` 的协议或全部依赖；使用 Tunnel 自己的 [AdbWire](../protocol/src/main/java/com/tunnel/adb/protocol/AdbWire.java)。
2. 仅 UID 2000、Android API 30—36、display 0；不降 root 权限后继续，不改变权限、系统设置、显示电源或无障碍配置。
3. AOSP static mirror 只请求 AUTO_MIRROR；SurfaceControl fallback 始终 `secure=false`，不同于 upstream Android 11 的 secure-display 分支；本原型不请求受保护 buffer。
4. 无旋转/裁剪/OpenGL/多屏/自动分辨率重试；检测到 display 属性变化即结束，重新由用户发起诊断。
5. 仅硬件 H264 + Surface；只处理有界 Annex-B，AVCC、partial access unit、编码尺寸漂移直接失败，不发送未经解析的“视频”。
6. 仅构建源码原型，不能把 upstream 已支持的机型当作本适配已验证。供应链、许可证组合、产物 hash 和正式分发仍需外部资产/发布流程验收。
