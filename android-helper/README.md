# Android local ADB mirroring P0 helper

状态：源码原型；本次未编译、未运行协议测试、未安装 APK、未执行设备探针。

本目录只用于手机本地的有限时长采集验证，不接入 PC/relay 生产视频，不提供远程输入或任意 shell。入口是 `com.tunnel.adbhelper.Server`，源码见 [server](server/src/main/java/com/tunnel/adbhelper/Server.java)、[server README](server/README.md) 和 [provenance](server/PROVENANCE.md)。完整产品方案见 [ADB_REMOTE_MIRRORING_PLAN.md](../docs/plans/ADB_REMOTE_MIRRORING_PLAN.md)。

## 源码与协议

- `protocol/src/main/java/`：Android-independent Java 8 共享 wire；APK 和 helper 编译同一份源码。
- `protocol/src/test/java/`：JUnit 4 源码，覆盖握手、篡改、截断、边界、序号、epoch、配置和首关键帧；本构建脚本不编译或运行测试，不下载 JUnit。
- `server/src/main/java/`：shell helper，只依赖 Android SDK 与共享协议。
- `out/`：每次构建创建新的唯一子目录，失败输出也保留，不覆盖已有产物。

`AdbWire.authenticateClient` 的 client 指 APK，`authenticateServer` 的 server 指 helper，与哪个进程先建立 TCP 连接无关。仅使用 `127.0.0.1`；两条独立 socket 分别承载 VIDEO 和 CONTROL。握手绑定版本、channel、epoch 和双方随机 nonce；方向分离的 HMAC 密钥保护每条记录。HMAC 提供认证和完整性，不提供媒体加密；它不是公网协议。

P0 消息只有 CAPABILITIES、VIDEO_CONFIG、VIDEO_FRAME、REQUEST_KEYFRAME、STOP、PING、PONG，没有 input/file/shell 操作。每条 channel 每个方向的 sequence 从 1 连续递增；一个 Session 固定 epoch。VIDEO_CONFIG 包含独立的 H264 codec configuration，之后首帧必须标 KEY_FRAME。收到 config 不能当作成功解码首帧。`ptsUs` 是微秒；未来接 Rust 视频时再明确转换为其毫秒时基。

每包长度在分配前校验；最大 AU 8 MiB、config 64 KiB、最长边 4096、总像素 8 Mi。Packet 复制入参并只提供 `payloadCopy()`，不保存调用方可变数组。读写错误永久关闭 Session。调用方仍须设置 socket 读期限、限制出入队列和通过关闭 socket 取消卡住的写入；framing 本身不会创建超时线程。

握手及数据记录使用 magic `TADB`（`0x54414442`）。启动配置经 stdin 传固定 70 字节 big-endian Bootstrap：`TABT`（`0x54414254`）、version u16、32-byte secret、epoch i64、videoPort/controlPort/maxSize/fps/bitrate/durationSeconds 六个 i32。secret 不放 argv、环境变量、URL 或日志。`Bootstrap.close()` 清理自己的副本，调用方也应清理 `secretCopy()` 返回的副本。Bootstrap 时长上限 30 秒，建议初始配置最长边 1280、15/30fps、4Mbps、10秒；能力探针结果不代表产品运行验收通过。

## 离线构建

正式执行需使用已取得的构建授权。本仓库不下载 SDK/JDK/D8，不触发构建、不替 owner 选择外部二进制。显式提供可信 JDK bin（建议 JDK 17）、Android SDK API 34 `android.jar` 及同目录 `source.properties`、既有 build-tools `lib/d8.jar`。

在仓库根目录，按本机构建机真实路径替换示例参数：

```powershell
python android-helper/build_helper.py --android-jar '<SDK>/platforms/android-34/android.jar' --d8-jar '<SDK>/build-tools/<approved-version>/lib/d8.jar' --jdk-bin '<JDK>/bin'
```

脚本通过 Java 8 source/target 和 Android API 34 bootclasspath 编译共享协议与 server，再由 D8 `--min-api 30` 生成单个 `classes.dex`，最后封装 `helper.jar`；`META-INF/` 同时保留 `LICENSE.scrcpy`、`NOTICE`、`PROVENANCE.md`。命令使用参数数组，不拼接 shell，不运行 helper、APK 或设备。生成 `manifest.json` 包含 artifact SHA-256、protocol、entryPoint、固定 upstream reference commit `2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0`、本地 Java 源码逐文件 hash、源码树 hash、随包许可证/来源文件 hash、构建脚本及显式工具输入 hash。该 upstream 字段是来源参考，不表示本地原型与完整 scrcpy 二进制相同，也不证明供应链已批准。

编译期间源码被改动会拒绝生成可 stage 的成功结果。记录工具 hash 不能替代工具来源审查、JDK runtime 完整性或可复现构建验证。

## 显式首次 stage

需要把已构建 helper 纳入后续 APK 的本地原型资源时，在同一命令追加 `--stage`：

```powershell
python android-helper/build_helper.py --android-jar '<SDK>/platforms/android-34/android.jar' --d8-jar '<SDK>/build-tools/<approved-version>/lib/d8.jar' --jdk-bin '<JDK>/bin' --stage
```

仅首次创建 `flutter/android/app/src/main/assets/adb-mirror-p0/`，复制 `helper.jar` 后最后写入 `manifest.json`。这是 Android native assets，与 `context.assets.open("adb-mirror-p0/...")` 一致，不需要 Flutter pubspec 条目。缺少父 assets 目录时只在已确认的仓库范围内创建。目标目录只要存在即拒绝，包含空目录或先前失败的部分 stage；不删除、不覆盖、不自动修复。失败残留须由 owner 检查并在明确批准的替换任务中处理。APK 必须校验两件套和 hash，不能将缺 manifest 的 jar 视作可运行资产。

`--stage` 不是 APK 构建、设备授权、发布、签名或 Git 提交。协议 JUnit、helper build、APK build 和真机采集各自回填验证结果；全部尚为 `NOT_RUN` 时，不得启用生产远程 ADB 按钮。
