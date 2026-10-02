# ADB P0 本机原型：构建与验证交接

日期：2026-10-02；任务 T-2026-10-02-003；源码基线 test/532637a + 本任务未提交修改。

状态：**源码已接入，正式编译、测试和真机结果均为 NOT_RUN。P0 整体尚未通过。**

## 1. 本次实际能力

Android ADB 页面新增本机诊断卡片；只有显式使用 `tunnelAdbMirrorP0=true` 的构建才显示。默认构建保持关闭，native start 同时检查 BuildConfig，隐藏 UI 不是唯一限制。

点击后：验证本机 selector / 已连接 transport / nonce / shell UID 2000 → 校验 APK 内 helper manifest 和 SHA-256 → 在本机 shell 随机私有目录 staging → 再核身份 → stdin 传一次性 bootstrap → 两路 loopback mutual authentication → 收取 H264 config、编码帧、IDR 计数 → 关闭本次进程和 socket。

这不是 PC 投屏完成证明。当前 `decoded=false`、`rendered=false`；没有改变 Rust/JNI/video service、PC toolbar、message.proto、普通共享、无视、SKL、无障碍权限或输入 owner。P0 后续仍须完成真实 decode/render 及隔离 relay 的纵向证据，才能进入正式远程 P1—P6。

## 2. 文件地图

| 作用 | Source of truth |
|---|---|
| 本机身份 / selector / 有界子进程 | `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/probe/` |
| artifact 校验 / supervisor | `flutter/android/app/src/main/kotlin/com/tunnel/app/adb/mirror/` |
| 本地 MethodChannel / 互斥 | `oFtTiPzsqzBHGigp.kt` 的 `tunnel_adb_p0_*`、`localAdbAction` |
| Android ADB 页面 | `flutter/lib/mobile/widgets/adb_mirror_probe_card.dart`、`adb_page.dart` |
| 双方共用协议 | `android-helper/protocol/src/main/java/com/tunnel/adb/protocol/AdbWire.java` |
| shell capture / encoder | `android-helper/server/src/main/java/com/tunnel/adbhelper/` |
| 独立构建 / 来源 | [helper README](../../android-helper/README.md)、[provenance](../../android-helper/server/PROVENANCE.md) |

旧 Runner 的自动重连仍可能重启共享 ADB server，持久 shell 的 sendCommand 返回也只表示写入完成，不证明长命令已经结束。P0 对显式页面操作做进程级互斥计数，transport 丢失时结束诊断；本轮没有完成 P1 transport ownership 改造，不能宣称已支持长期并发终端与采集。诊断前应确认没有此前发起的长命令。未知 libadb 内部 daemon 行为也必须由来源及设备证据确认。

## 3. 《编译验证需求》

以下是待正式环境执行的命令清单，不是已执行记录。按 `.codex/AI_RULES.md` §2.2，需先确定环境、动作和停止边界。本任务没有构建/测试/安装/设备/Git/签名/发布授权。

前置材料：受控的完整 JDK、SDK API 34、build-tools D8、项目匹配 Flutter/Gradle、可追溯的每 ABI `libadb.so` 与项目 native 库；以及自有测试手机 Android/ROM/API/ABI。禁止用来历不明的二进制补齐缺口。

### 3.1 离线 helper 构建

执行目录：仓库根。按正式机器实际路径替换参数；无下载、安装或签名。

```text
python android-helper/build_helper.py --android-jar <SDK>/platforms/android-34/android.jar --d8-jar <SDK>/build-tools/<version>/lib/d8.jar --jdk-bin <JDK>/bin --stage
```

目标：共享 Java protocol + Android server 编译、D8 min API 30、`classes.dex` jar、artifact/source/tool hashes。首次 stage 到 `flutter/android/app/src/main/assets/adb-mirror-p0/`；目录存在即拒绝，不覆盖。产物位于 `android-helper/out/` 唯一目录。测试源码不由此脚本执行。

### 3.2 单元测试

执行目录：`flutter/android`，正式 Flutter/SDK 配置准备完成后。

```text
./gradlew :app:testDebugUnitTest -PtunnelAdbMirrorP0=false --tests 'com.tunnel.adb.protocol.AdbWireTest' --tests 'com.tunnel.app.adb.probe.*'
```

Windows 对应使用 `gradlew.bat`。Gradle 现有任务可能解析依赖和生成 protobuf，这些副作用需要包含在执行授权中。覆盖目标：认证失败、错误 epoch/sequence/方向、包长/截断、config-before-keyframe、非法本机地址、nonce/UID、进程超时/输出上限。测试声明已接入 `app/build.gradle`；JUnit 4.13.2 不等于本轮已下载或测试已通过。

### 3.3 APK 诊断构建

执行目录：`flutter`，先取得项目 native 产物及来源。仅构建 debug APK，不能替代项目正式 Linux release 全链。

```text
ORG_GRADLE_PROJECT_tunnelAdbMirrorP0=true flutter build apk --debug
```

上述为 POSIX 单命令环境变量示例。Windows 正式环境可先设置同名进程环境变量，再执行 `flutter build apk --debug`，执行后恢复此前值。另做一次无该属性的构建确认诊断入口隐藏、native start 拒绝。现有签名/完整构建脚本不自动运行。

### 3.4 真机执行边界

仅明确指定的自有设备；安装/升级测试 APK、无线配对、本机 helper staging 与短暂屏幕采集需明确批准。默认不保存、上传或持久化画面，不连接生产 relay。记录不含配对码、地址、设备标识、secret 或原始 stdout。

1. 先在 Android 系统开启无线调试，并通过既有 ADB 页面配对、连接。配对端口和连接端口不同。
2. 复制当前连接端口，回到 APK 的 ADB 页。输入与已建立 ADB transport 完全一致的本机 selector，例如 `localhost:<port>` 或 `127.0.0.1:<port>`；不得用 USB/外部设备 selector。不会自动猜测、connect 或替换目标。
3. 点击“开始 10 秒验证”。观察 shell、helper、capability、config、编码帧和关键帧分别变为真实结果。正常结束码为 `ENCODED_SAMPLE_RECEIVED`；它仅证明本次编码样本收到。
4. 点击停止、离开 ADB 页、切到后台或销毁 Activity，验证本次进程/socket 退出；普通共享/core/其他无障碍继续工作。页面轮询不是唯一 watchdog。
5. 再启动一次，验证资源已释放。关闭无线调试/断 transport/旋转应安全失败，不能静默改成无视或重开 MediaProjection。
6. 当前原型固定主 display 0；Android API 30—36 代码范围不等于 ROM 支持矩阵。旋转/尺寸变化终止；不提供输入/节点/无障碍设置。受保护内容黑屏不能当作权限不足后继续提权。

时间边界：APK 在认证后采样约 10 秒；单次读 5 秒 timeout、包含准备过程的总 watchdog 45 秒；helper 启动 15 秒上限、采集 20 秒硬上限、5 秒控制 lease、2 秒阻塞写回收。APK 通常先关闭 stdin/socket，20 秒用于失联兜底。退出后的有限 cleanup 最多另用 2 秒；若系统进程启动或 framework cleanup syscall 挂住，必须按错误和残留证据处理，不能当作严格实时保证。

## 4. 结果记录模板

```text
Source commit + reviewed diff identity:
Build environment / tool versions / hashes:
Helper jar hash / manifest / APK hash:
libadb.so provenance + ABI hash:
Phone model / Android API / ROM / ABI (no device identifiers):
Default-off gate:
Identity / handshake / encoded config+IDR / counters:
Cancel / page leave / background / transport loss / rotation:
Cleanup / second start / ordinary sharing + accessibility coexistence:
Unit tests:
Actual decode / PC render / isolated relay: NOT_RUN unless independently proven
Cases: ADBM-01..07/12/30 partial evidence, AND-06 regression
Verdict and remaining blockers:
```

没有证据的项目保留 NOT_RUN。返回具体结果后，先修复原型问题并补齐 decoder/relay 和关键 provider 实验，再接 PC 顶栏、模式事务与侧按钮；不得用当前源码或计数器替代全部九项需求验收。
