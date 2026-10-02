# TUN-BL-2026-10-03-WINDOWS-BUILD

- 日期：2026-10-03；Task T-2026-10-03-003。
- Source：test / 3c22a25f0151f0dea408c0c03c6e818cdf31fe98，开始干净；本轮为未提交工作树修改，保留该HEAD的Android helper修复。
- 工具链/版本：继承TUN-BL-2026-10-03-ADB与既有Windows正式环境；5.2.1、默认Rust1.75、既有Flutter与vcpkg路线，不作升级或签名。
- Entry：new-build.cmd → scripts/windows-build.ps1；build.cmd/pc-bulid.cmd兼容转发；PC-Bulid输出。
- Assets：scripts/windows-assets.lock.json锁打印driver1.4/adapter/checksum的官方API SHA256；usbmmidd asset180784412/199309bytes且无上游digest；WindowInjection固定来源commit53b548a5398624f7149a382000397993542ad796。未在本地获取binary。
- Build：Cargo JSON定位DLL/packer，CMake接收实际DLL，新staging＋固定资产供应，独立DPI manifest、嵌入payload SHA256验证。
- Evidence：V0源码/AST/JSON/PowerShell语法/diff；合同测试、服务器编译/下载/解压/驱动 V1—V3 NOT_RUN。
- Entry：[使用和验证](../plans/WINDOWS_BUILD_GUIDE.md)。本基线不证明产物、publisher/signature、驱动兼容或发布就绪；旧基线保留。
