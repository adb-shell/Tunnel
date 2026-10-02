# TUN-BL-2026-10-03-ADB

- Observed：2026-10-03 Asia/Shanghai；Task T-2026-10-03-001。
- Source：branch `test`，HEAD `bc50fe5b2061970e4e88ff58bbd76c7827de1159`，开始干净；本轮实现为未提交工作树补丁，不冒充 commit 内容。
- Product：Tunnel / 隧道 / com.tunnel.app，版本5.2.1 / 5.2.1+59，未改签名、版本或外部服务。
- LADB：commit `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`，预编译 blob/size/ELF 锁 `android-adb/ladb-prebuilt.lock.json`；本地未下载 binary，服务器获取后记录 SHA256，build-from-source provenance 未证实。
- Helper：仓内受限源码、protocol v2、API34编译配方；继承 scrcpy v4.1 的受限capture来源及许可证。由服务器重建，不能沿用 P0 protocol v1 产物。
- Remote：新增 protobuf AndroidControl/AndroidVideoMetadata/AndroidVideoBarrier，PC/APK配套重建；FRB复用sessionPeerOption，无手写新增生成桥。
- Toolchains：原Android NDK27.2/JDK17/正式Flutter和Windows环境规则保持；实际服务器版本与锁一致性待回传。
- Devices：用户提供OnePlus ACE 6T、iQOO Neo9，Android16；精确ROM/ABI/页大小UNKNOWN。
- Evidence：V0源码/合同/配置静态核查；build/test/analyze/codegen/device/package/sign/git-write NOT_RUN。
- Entry：[实现与验证指南](../plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md)。旧基线和历史结论不改写。
