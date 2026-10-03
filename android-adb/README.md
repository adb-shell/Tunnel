# Android 本地 ADB 来源与准备

完整 [tytydraco/LADB](https://github.com/tytydraco/LADB) 上游快照保存在 `third_party/ladb/`，固定提交 `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`。原始 79 个文件（含四 ABI 的 `libadb.so`、源码、资源、Gradle wrapper 和许可证）均由 `TUNNEL_SOURCE.json` 记录 SHA-256。标准文件名为 `libadb.so`，不是 `libad.so`。

手机第二页按 LADB 流程提供配对、端口发现、手动连接与持久 shell，复用 Tunnel 的 Manager/Runner。原版独立 Activity 不作为另一套后台运行。手机本地与 PC 远程配对共用 `LocalAdbProcessSpec`：私有 `-L localfilesystem:<filesDir>/adb-server.sock` 和 HOME/key，不占用其他 LADB 应用的默认 daemon。操作步骤见 [ADB 指南](../docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。

## 正式构建机准备

`prepare_adb.py` 优先复核已有 staging，再从仓内快照取得文件；只有对应文件缺失且未指定 `--offline` 时才下载固定 commit 的 HTTPS raw 文件。四 ABI 路径、大小和 Git blob SHA-1 见 [ladb-prebuilt.lock.json](ladb-prebuilt.lock.json)。文件长度、blob、ELF PIE 和 ABI 必须匹配；已有不同文件拒绝覆盖。实际 SHA-256 写入随 APK 携带的 `assets/adb-provenance/manifest.json`。

```sh
# 正式构建入口会自动准备所选 ABI
./build.sh 1
# 单独准备（也适用于直接调用 Flutter/Gradle 前）
python3 android-adb/prepare_adb.py --abi arm64-v8a --offline
python3 android-adb/prepare_adb.py --abi arm64-v8a --abi armeabi-v7a --abi x86_64 --offline
```

`--offline` 可以直接使用仓内快照，不要求此前生成过 jniLibs。Gradle `verifyTunnelAdbArtifacts` 在 preBuild 校验来源锁、receipt、ABI 和哈希，缺失/篡改立即失败；保留可执行文件原始字节，禁止再 strip。APK 安装后从 `nativeLibraryDir` 执行，不能从可写应用目录下载并执行任意库。

## 配对与生命周期

配对端口属于 `_adb-tls-pairing._tcp`，连接端口属于 `_adb-tls-connect._tcp`，不能混用。保持系统配对码窗口打开，可分屏输入六位码和端口；自动发现只接受本机服务。配对成功后独立 connect，并以随机 nonce 包围 `/system/bin/id -u`，确认真实 `2000` 响应，不能用历史配对标记充当当前连接成功。

配对码仅写 stdin，不进入命令行、配置或日志。手机输出保存脱敏原生错误；远程仅接收结构化状态。持久终端使用 `adb shell -tt`，保持当前目录/环境，支持 Ctrl+C；本地终端和远程 helper 互斥，停止终端只关闭其客户端并释放 lease，不执行 `kill-server`。切页或打开系统设置不取消正在进行的配对；取消只中断发起方自己的 worker。

## 许可与验证边界

保留上游 [native LICENSE](../third_party/ladb/app/src/main/jniLibs/LICENSE)（Apache-2.0）和 [根 LICENSE](../third_party/ladb/LICENSE)（含非官方 Google Play 分发限制），供应脚本原样核验并随 APK 携带。`.gitattributes` 禁止转换快照行尾，避免破坏原始字节与哈希。

这是一套经哈希核验的上游预编译件，不是从 AOSP 重建的可复现二进制。上游 AOSP revision/toolchain、完整依赖 SBOM、Android 11–16 ROM 及 16 KiB 页设备兼容仍需正式构建与真机验证。本地仅执行快照/离线供应/篡改拒绝测试，没有执行这些 native 二进制。
