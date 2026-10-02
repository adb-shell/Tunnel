# Android 本地 ADB 二进制来源

2026-10-03 T004：专用 Android ADB 页面已移除，PC 可拖动弹窗发送端口/码或复用已配对密钥；APK RemoteAdbPairing 后台 pair→独立connect→fresh shell probe，状态进入右上 Tunnel 检测。内部 runner/probe/helper/有限 shell 保留。`-H localhost` 修复初次 daemon 启动，ADB_MDNS_AUTO_CONNECT=0 配合应用显式本机 NSD/connect，不把配对成功冒充连接成功。当前授权政策与使用见 [指南](../docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md) / ADR-0016；下文分屏/页面退出的原手机 UI 操作描述属于旧阶段。

状态：仅来源核查和供应脚本已完成；本次没有下载/执行二进制，没有运行服务器构建、协议测试或设备验证。标准名称始终为 `libadb.so`，不是 `libad.so`。

官方来源为 [tytydraco/LADB](https://github.com/tytydraco/LADB)，固定提交 `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`。四个 ABI 的路径、大小和 Git blob SHA-1 来自该提交的官方 Git tree，固定在 [ladb-prebuilt.lock.json](ladb-prebuilt.lock.json)。这里复现的是上游预编译文件的获取，不声称从 AOSP/上游构建源码重建出了相同文件。上游 ADB 源码提交、编译器配置、完整传递依赖 SBOM、16 KiB 页设备验证尚未得到证明。

`prepare_adb.py` 在服务器显式构建阶段下载固定 commit 的 HTTPS raw 文件，验证 Git blob（包含 `blob <size>\0` 前缀）、文件长度、ELF PIE 类型及 ABI。服务器计算实际 SHA-256，写入随 APK 携带的 `assets/adb-provenance/manifest.json`。该 SHA-256 是实际取得字节的记录；本地尚未取得字节，因此本文件不编造一个预先验证的 SHA-256。已有文件不符时拒绝覆盖，要求人工确认旧文件来源。

正式服务器使用方式：

```sh
python3 android-adb/prepare_adb.py --abi arm64-v8a
python3 android-adb/prepare_adb.py --abi arm64-v8a --abi armeabi-v7a --abi x86_64
# 已准备相同产物的离线服务器：
python3 android-adb/prepare_adb.py --abi arm64-v8a --offline
```

`build.sh` 为其所选 ABI 调用供应脚本；直接运行 Flutter/Gradle 时必须先准备对应 ABI。Gradle `verifyTunnelAdbArtifacts` 在 preBuild 校验来源锁、receipt、ABI 和哈希，缺少/篡改立即失败；保留可执行文件原始字节，禁止对 `libadb.so` 再 strip。APK 安装后从 `nativeLibraryDir` 执行该文件，不能从可写应用目录下载并执行任意库。

来源许可证分别为 [jniLibs/LICENSE](https://github.com/tytydraco/LADB/blob/60f48029cf9d8e0bc848ca41a7bd76694d4ab796/app/src/main/jniLibs/LICENSE)（Apache-2.0 文本）与 [根 LICENSE](https://github.com/tytydraco/LADB/blob/60f48029cf9d8e0bc848ca41a7bd76694d4ab796/LICENSE)（包括 Google Play 分发限制）。脚本按固定 blob 原样取得两份文本并随 APK 携带；不能据此省略完整发行合规审查。

配对端口属于 `_adb-tls-pairing._tcp`；连接端口属于 `_adb-tls-connect._tcp`，二者不能混用。Android 11–16 用户在系统无线调试页开启配对码对话框，分屏保留设置窗口，输入 6 位配对码；可以自动发现**本机**配对端口或手填本机地址。配对成功后另行发现/输入连接端口。历史配对标记不表示当前授权：每次使用前要求指定本机 transport 并以随机 nonce 包围 `/system/bin/id -u` 的真实 `2000` 响应。

[Android 16 本地网络保护](https://developer.android.com/privacy-and-security/local-network-permission)目前是 opt-in，会影响 NSD/局域网访问；这里不伪造授权，不关闭系统保护。mDNS失败提供手工 loopback/当前手机 Wi-Fi 地址入口；实际 ROM、配对对话框生命周期、Android16 16KiB 页运行仍需服务器APK真机验证。

生命周期：配对/连接/终端采用有界独立子进程；配对码只写短 stdin，不放命令行/日志。页面退出只取消该页本地操作；模式切换不执行 `kill-server`、全局断开或无线调试循环。镜像运行持 `TunnelAdbManager.acquireMirrorLease()`，本地配对/终端被共享租约拒绝，helper退出后仅由该 owner 释放。
