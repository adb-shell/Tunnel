# Android ADB mirroring helper

状态：源码原型；本次未编译、未运行协议测试、未安装 APK、未执行设备探针。

2026-10-03 本目录扩展为受控远程投屏的手机本地 helper；原 P0 10 秒诊断入口保留，正常远程入口由 APK `TunnelAdbRuntime` 管理本机同意、scope 和控制者租约。helper 不直接开放公网，不提供任意 shell。入口是 `com.tunnel.adbhelper.Server`，源码见 [server](server/src/main/java/com/tunnel/adbhelper/Server.java)、[server README](server/README.md) 和 [provenance](server/PROVENANCE.md)。完整产品方案见 [ADB_REMOTE_MIRRORING_PLAN.md](../docs/plans/ADB_REMOTE_MIRRORING_PLAN.md)。

## 源码与协议

- `protocol/src/main/java/`：Android-independent Java 8 共享 wire；APK 和 helper 编译同一份源码。
- `protocol/src/test/java/`：JUnit 4 源码，覆盖握手、篡改、截断、边界、序号、epoch、配置和首关键帧；本构建脚本不编译或运行测试，不下载 JUnit。
- `server/src/main/java/`：shell helper，只依赖 Android SDK 与共享协议。
- `out/`：每次构建创建新的唯一子目录，失败输出也保留，不覆盖已有产物。

`AdbWire.authenticateClient` 的 client 指 APK，`authenticateServer` 的 server 指 helper，与哪个进程先建立 TCP 连接无关。仅使用 `127.0.0.1`；两条独立 socket 分别承载 VIDEO 和 CONTROL。握手绑定版本、channel、epoch 和双方随机 nonce；方向分离的 HMAC 密钥保护每条记录。HMAC 提供认证和完整性，不提供媒体加密；它不是公网协议。

Wire VERSION=2：CAPABILITIES、VIDEO_CONFIG、VIDEO_FRAME、REQUEST_KEYFRAME、STOP、PING、PONG，以及 OPERATION/RESULT。`AdbCommands` 只允许固定数值结构的触摸、按键、导航、截图、节点、display power、释放输入和选择采集模式；没有 file/shell/Intent/component/settings 接口。CAPTURE_MODE 的 a=0/1/2/3 分别 live/snapshot/hierarchy/paused，b=0/3 指 provider 丢失后返回 live/paused；其余字段必须 0。每条 channel 每个方向 sequence 从 1 连续递增；一个 helper Session 固定 epoch。VIDEO_CONFIG flags 0=live、2=snapshot、4=hierarchy，其 payload 仍是 Annex-B SPS/PPS，首帧必须是 IDR。Runtime 将 helper epoch 映射到 Rust 分配的网络 epoch；旋转/换源/恢复先私有 RECONFIGURE 握手，再发送新 CONFIG/IDR。`ptsUs` 始终是微秒，解码/呈现成功另行确认。

每包长度在分配前校验；最大 AU 8 MiB、config 64 KiB、最长边 4096、总像素 8 Mi。Packet 复制入参并只提供 `payloadCopy()`，不保存调用方可变数组。读写错误永久关闭 Session。调用方仍须设置 socket 读期限、限制出入队列和通过关闭 socket 取消卡住的写入；framing 本身不会创建超时线程。

握手及数据记录使用 magic `TADB`（`0x54414442`）。启动配置经 stdin 传固定 70 字节 big-endian Bootstrap：`TABT`（`0x54414254`）、version u16、32-byte secret、epoch i64、videoPort/controlPort/maxSize/fps/bitrate/durationSeconds 六个 i32。secret 不放 argv、环境变量、URL 或日志。`Bootstrap.close()` 清理自己的副本，调用方也应清理 `secretCopy()` 返回的副本。Bootstrap 硬上限 3600 秒，不因旋转重置；P0 仍 20 秒 helper 上限/10 秒采样。远程同意最长 3600 秒、控制者心跳 15 秒过期，正常 EOF/撤销更早终止。能力探针结果不代表产品运行验收通过。

## 离线构建

正式执行需使用已取得的构建授权。本仓库不下载 SDK/JDK/D8，不触发构建、不替 owner 选择外部二进制。显式提供可信 JDK bin（建议 JDK 17）、Android SDK API 34 `android.jar` 及同目录 `source.properties`、既有 build-tools `lib/d8.jar`。

在仓库根目录，按本机构建机真实路径替换示例参数：

```powershell
python android-helper/build_helper.py --android-jar '<SDK>/platforms/android-34/android.jar' --d8-jar '<SDK>/build-tools/<approved-version>/lib/d8.jar' --jdk-bin '<JDK>/bin'
```

脚本通过 Java 8 source/target 和 Android API 34 bootclasspath 编译共享协议与 server，再由 D8 `--min-api 30` 生成单个 `classes.dex`，最后封装 `helper.jar`；`META-INF/` 同时保留 `LICENSE.scrcpy`、`NOTICE`、`PROVENANCE.md`。命令使用参数数组，不拼接 shell，不运行 helper、APK 或设备。生成 `manifest.json` 包含 artifact SHA-256、protocol、entryPoint、固定 upstream reference commit `2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0`、本地 Java 源码逐文件 hash、源码树 hash、随包许可证/来源文件 hash、构建脚本及显式工具输入 hash。该 upstream 字段是来源参考，不表示本地原型与完整 scrcpy 二进制相同，也不证明供应链已批准。

2026-10-03 编译修复：`javac` bootclasspath 同时包含 `android.jar` 和当前 D8 所属 build-tools 目录下的 `core-lambda-stubs.jar`（与 `lib/` 同级），使用宿主 `os.pathsep` 分隔。仅有 `android.jar` 会在 Lambda 编译时报 `Unable to find method metafactory`。脚本在创建产物前验证 stub 文件和 `java/lang/invoke/LambdaMetafactory.class`，并将其 SHA-256 纳入 `toolInputs.coreLambdaStubsJar` 及构建期间输入变动检查；stub 不打包进 helper。服务器需有完整的已批准 SDK build-tools，本脚本不自动下载。此修复仅经静态检查，须重新执行原 `build.sh` 入口验证。

编译期间源码被改动会拒绝生成可 stage 的成功结果。记录工具 hash 不能替代工具来源审查、JDK runtime 完整性或可复现构建验证。

## 显式首次 stage

需要把已构建 helper 纳入后续 APK 的本地原型资源时，在同一命令追加 `--stage`：

```powershell
python android-helper/build_helper.py --android-jar '<SDK>/platforms/android-34/android.jar' --d8-jar '<SDK>/build-tools/<approved-version>/lib/d8.jar' --jdk-bin '<JDK>/bin' --stage
```

创建或安全替换 `flutter/android/app/src/main/assets/adb-mirror-p0/`。这是 Android native assets，与 `context.assets.open("adb-mirror-p0/...")` 一致，不需要 Flutter pubspec 条目。已有目录必须仅含真实文件 `helper.jar` 与 `manifest.json`，且 entryPoint、schema、implementation、size/hash 自洽；否则保留并拒绝。脚本使用排他锁、临时目录和旧目录备份 rename，成功后只删除已验证的旧两文件；替换失败恢复旧目录，不递归清理未知文件。崩溃遗留锁/备份由构建负责人检查。`build.sh` 普通构建也准备 helper，Gradle `preBuild` 核对 protocol 2、当前源码/脚本/来源文件 hash，拒绝陈旧或不完整资产，与默认关闭的 P0 诊断开关独立。APK 运行时仍必须再次核验两件套和 hash。

`--stage` 不是 APK 构建、设备授权、发布、签名或 Git 提交。协议 JUnit、helper build、APK build 和真机采集各自回填验证结果；当前源码入口可供服务器构建验证，全部尚为 `NOT_RUN` 时不得称为已通过生产验收。
