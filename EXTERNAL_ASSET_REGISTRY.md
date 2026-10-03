# Tunnel External Asset Registry

ADB 当前来源：完整官方 LADB 快照已纳入 `third_party/ladb/`，固定 commit `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`。原始 79 个文件及四 ABI 预编译件均已取得；逐文件 SHA-256 与 archive 来源见 `TUNNEL_SOURCE.json`，native/许可证另与 `android-adb/ladb-prebuilt.lock.json` 核验。未执行 native 二进制；运行与源码重建证据仍待补齐。获取与使用见 [ADB 来源说明](android-adb/README.md)。

2026-10-03 T-2026-10-03-003 Windows增量：scripts/windows-assets.lock.json与windows_assets.py形成正式服务器供应入口；打印driver1.4/adapter/checksum的官方API digest固定，usbmmidd旧asset无digest但记录实际hash并支持owner pin；WindowInjection可固定commit源编译或采用owner提供DLL。binary未在本地取得，签名/publisher/OS验证仍外部未完成。详见docs/plans/WINDOWS_BUILD_GUIDE.md。

最后更新：2026-10-03（ADB binary/helper 来源与服务器准备入口补充；其余 inventory 保留各自日期）
状态：repository-side inventory rechecked at `HEAD 5cee6921ec10971bb4654bc010f9328d7f70d02b`；owner/provenance onboarding incomplete
范围：不在 Git tracked source 中，或不能由 clean clone 独立复现的 service、database、driver、binary、signing/build/release infrastructure 和历史 provenance

> 本登记表不保存 credential、token、password、private key、生产 IP/domain、连接串或 PII。发现这类值时只记录资产类型、所在路径和处置状态。

## 1. 状态词汇

| 状态 | 含义 |
|---|---|
| `EXTERNAL` | 服务/基础设施本来就在仓库外，需独立 owner 与 contract |
| `PARTIAL` | 仓库只有 client、contract、脚本或文档，没有完整资产 |
| `LOCAL-ONLY` | 当前机器存在，但被 ignore、未跟踪或无法由 clean clone 获取 |
| `GENERATED` | source 在仓库，binary/artifact 需正式构建生成 |
| `OS-DERIVED` | 运行时依赖目标操作系统提供或从 OS 文件派生，不是仓内可发布的独立 artifact |
| `DORMANT` | 代码或接口仍存在，但不是当前 active product path；启用前必须重新接管 |
| `MISSING` | 需要的 source、contract、binary 或运维材料未提供 |
| `UNVERIFIED` | 存在某些材料，但 version/hash/signature/license/owner 未核验 |
| `BLOCKING` | 缺口阻止正式验证、可复现构建、安全接受或发布 |

Owner 为空时统一写 `OWNER-REQUIRED`，不能由 AI 推断个人或组织。

2026-10-02 只读复核：Git tracked files 中按 `.dll/.exe/.sys/.cat/.so/.aar/.jks/.keystore/.p12/.zip` 扩展名盘点为 0；与旧盘点一致。该结果只覆盖这些扩展名，不是所有二进制格式的普查，也不代表运行时不依赖它们或可安全重新下载。本轮没有访问外部资产、服务器或下载 artifact。

## 2. 登记要求

每个外部资产最终应具备：

- 业务/技术 owner 与 on-call。
- source repository 或 vendor source，固定 commit/version。
- contract/schema/config（脱敏）。
- artifact SHA-256、signature/publisher 和获取方式。
- license、NOTICE 和分发限制。
- build/deploy/runbook、环境清单和 dependency。
- authentication/authorization、secret owner 和 rotation policy，但不记录 secret 值。
- monitoring、backup/restore、RPO/RTO、incident 和 rollback。
- 测试/生产边界与验证证据。

未满足这些字段时，不得描述为“已接管”“可复现”或“发布就绪”。

## 3. External Services and Data

| ID | 资产 | 当前存在性 | 仓内消费/证据锚点 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-SVC-001` | Product account/device API backend | `PARTIAL`：只有 Dart/Rust clients | `flutter/lib/models/user_model.dart`, `ab_model.dart`, `group_model.dart`, `src/hbbs_http/` | `OWNER-REQUIRED` | server repo/version、OpenAPI、auth/ACL、deploy/topology、SLA/logs | backend behavior 与权限无法闭环；full-system verification `BLOCKING` |
| `EXT-SVC-002` | `hbbs` rendezvous server | `EXTERNAL/MISSING` | `src/rendezvous_mediator.rs`, `libs/hbb_common/protos/rendezvous.proto`, config clients | `OWNER-REQUIRED` | source/version、production config、topology、key management、logs/monitoring | ID registration/handshake/compatibility 验证 `BLOCKING` |
| `EXT-SVC-003` | `hbbr` relay server | `EXTERNAL/MISSING` | `src/client.rs::{request_relay, create_relay}`, rendezvous protocol | `OWNER-REQUIRED` | source/version、relay policy、capacity、TLS/key、logs/monitoring、DR | relay-only product path 的 integration evidence `BLOCKING` |
| `EXT-DATA-001` | Product database | `MISSING` | client models/contracts only；仓库无 schema/migration | `OWNER-REQUIRED` | engine/version、schema/migration、tenant ACL、backup/restore、RPO/RTO、retention/audit | account/device/data security 与恢复能力 `BLOCKING` |
| `EXT-SVC-004` | ZEGO token broker | `PARTIAL`：deployment script 内嵌 Go module/server source，但没有独立受控 project、`go.sum`、tests、CI/IaC 或生产部署证据 | `src/client/helper.rs`, `scripts/deploy_zego_token_service.sh`, ZEGO docs | `OWNER-REQUIRED` | 独立 source repo/commit、dependency lock、tests/SBOM、HTTPS/session auth/rate limit、deploy/logs、credential ownership | tracked credential 类型字面值存在，有效性与当前生产使用未测试；脚本的静态 Bearer/4 KiB cap 不证明业务授权。Incident response 与 token abuse control `BLOCKING` |
| `EXT-SVC-005` | ZEGO RTC account/service and SDK distribution | `EXTERNAL/UNVERIFIED`；dependency 被声明，但 tracked `pubspec.lock` 未找到对应 ZEGO entry | `flutter/lib/models/zego_voice_call_model.dart`, Flutter dependency config | `OWNER-REQUIRED` | account owner、exact SDK lock/artifact hash、native binary provenance/license、quota/SLA、data region/retention、incident logs | clean clone dependency drift、voice media/privacy 与 release validation `BLOCKING` |
| `EXT-SVC-006` | Firebase/Google client project | `PARTIAL/UNVERIFIED` | `flutter/ios/Runner/GoogleService-Info.plist` 等 client configuration | `OWNER-REQUIRED` | 是否仍 active、project owner、bundle/SHA restriction、API scope/quota、privacy purpose | 旧 upstream ownership 或滥用风险；采用前必须确认 |
| `EXT-OPS-001` | DNS、TLS certificates、load balancer、production endpoint routing | `EXTERNAL` | endpoint/config consumers；具体值不在本表 | `OWNER-REQUIRED` | inventory、certificate owner/expiry、environment split、change/rollback/runbook | endpoint migration 和 HTTPS availability `BLOCKING` |
| `EXT-OPS-002` | Monitoring、logging、alerting、incident/on-call | `MISSING` | repository 只有 local logs/diagnostic behavior | `OWNER-REQUIRED` | metrics/log pipeline、redaction、retention、alerts、SLO/SLA、incident process | 生产可观测性和安全响应 `BLOCKING` |

## 4. Android Missing / Historical Local-only Assets

| ID | 资产 | 当前存在性 | 仓内消费/证据锚点 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-BIN-ADB-001` | `libadb.so` for arm64-v8a / armeabi-v7a / x86 / x86_64 | `ACQUIRED / HASH_VERIFIED`：四 ABI 固定预编译件已纳入仓内快照，未执行 | `third_party/ladb/TUNNEL_SOURCE.json`, `android-adb/ladb-prebuilt.lock.json`, `prepare_adb.py`, Gradle `verifyTunnelAdbArtifacts` | Android/Release/Security owner required | upstream 内部 AOSP source commit/toolchain、SBOM、signature、16 KiB/ROM 运行证据 | 离线供应及篡改拒绝检查通过，不等于源码重建或真机验收 |
| `EXT-REF-ADB-002` | `ADB-CODE/` research/decompiled materials | `MISSING`：2026-10-02 当前目录不存在，ignore 规则仍在；historical local-only | 旧 ADB 研究背景（历史见 Git） | `OWNER-REQUIRED` | origin、revision、legal/provenance、是否仅研究使用、保留策略 | 旧研究结论目前不可独立重证；不得直接进入 release source |
| `EXT-REF-ADB-003` | 完整 LADB 上游源码快照 | `ACQUIRED / HASH_VERIFIED`：79 个原始文件 | `third_party/ladb/`, `TUNNEL_SOURCE.json`, 原始 LICENSE | Android/Release/Security owner required | native 内部 AOSP 构建链与发行合规验证 | 原版源码保留；产品 Flutter 界面适配并复用 Tunnel 后端，不能等同原版独立 APK |

**历史快照，不能作为本机验收：** 2026-07-12 文档记录当时存在三个 `libadb.so` 和一份 license 文件且均未 tracked。2026-10-02 当前 worktree 未找到这些文件，因此下列 SHA-256 只保留为历史交接线索，没有重新计算或核验：

| ABI | Historical SHA-256 recorded on 2026-07-12 |
|---|---|
| `arm64-v8a` | `47EA035FA5ED57F6149A2B025BBBD4B21584C355C05D0400416804715E4C12DE` |
| `armeabi-v7a` | `0AFEA102225CD4DDA85D2C01F36A56473724B741CC63B6386163EE286EFF268E` |
| `x86_64` | `62CC0F7707C83C98AA4DE699492C61091EDA9D68B3DB9EA2A185B179DA505BFA` |

这些历史 hash 不能证明当前资产存在、source、publisher、license、完整性或 release approval。材料补齐后需同时核对 source/license/recipe 与实际文件，不能只匹配历史 hash。

### 4.1 Remote ADB source and generated assets

| ID | Asset | State / owner | Required evidence / impact |
|---|---|---|---|
| EXT-SRC-ADB-HELPER-001 | scrcpy v4.1 受限采集适配与 Tunnel helper protocol 3（生产duration=0，APK/helper配套重建；P0有限诊断保留）；来源参考 `2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0` | SOURCE_IMPLEMENTED / V0 / runtime NOT_RUN；正式 Android+Security+Release owner required | [provenance](android-helper/server/PROVENANCE.md)、Apache license/NOTICE 与修改清单；[独立构建配方](android-helper/README.md)；本地源码 hash 决定实际实现，不能用 upstream reference 冒充完整 scrcpy 原版产物或 ROM 通过证明 |
| EXT-BIN-ADB-HELPER-001 | 从上述源码构建的 dex/JAR helper，APK native assets `adb-mirror-p0/` | NOT_BUILT；Android+Release owner required | `build.sh` 默认构建/stage，不依赖 P0 开关；manifest 绑定 artifact/source/resource/tool/build-script hash，Gradle 与运行时分别核验；实际 artifact SHA-256、ROM 矩阵、SBOM 仍待服务器和设备验证 |

T-2026-10-02-002只登记未来依赖；不替代EXT-BIN-ADB-001，也不宣称已安装APK缺少ADB。当前worktree的缺失与用户设备上的产物是不同证据范围。

### 4.2 2026-10-03 固定来源与服务器准备

- 官方 [LADB 仓库](https://github.com/tytydraco/LADB) 固定为 `60f48029cf9d8e0bc848ca41a7bd76694d4ab796`，完整快照已取得。四个 `libadb.so` 的 SHA-256 见仓内 `TUNNEL_SOURCE.json`，Git blob、size、ELF 属性见 [source lock](android-adb/ladb-prebuilt.lock.json)，不得与历史 SHA-256 混用。
- Linux 正式服务器 `./build.sh 1` / `./build.sh 2` 在 Flutter 构建前调用 `android-adb/prepare_adb.py`，优先从仓内快照取得所需 ABI，缺失时按固定 commit 在线获取，复核 Git blob、size、ELF class/machine/PIE，计算真实 SHA-256 到 `assets/adb-provenance/manifest.json`；已有不同文件拒绝覆盖。`TUNNEL_ADB_OFFLINE=1` 可从仓内快照离线准备或复核组件。Gradle 校验所有待打包的 `libadb.so` 与 receipt，并保留原始 executable bytes。完整命令见 [ADB provenance](android-adb/README.md)。
- 原始 [native Apache license](https://github.com/tytydraco/LADB/blob/60f48029cf9d8e0bc848ca41a7bd76694d4ab796/app/src/main/jniLibs/LICENSE) 与 [LADB repository LICENSE](https://github.com/tytydraco/LADB/blob/60f48029cf9d8e0bc848ca41a7bd76694d4ab796/LICENSE) 分别固定、复核并随 APK 保存。后者含非官方 Google Play 分发限制；不能把一个 LICENSE 概括为整个依赖图授权，也不构成法律发布批准。
- helper 使用现有 JDK、Android API 34 `android.jar` 和 D8 离线编译，`--stage` 对已核验且仅含两文件的旧资产作 recoverable replace；不下载工具、不执行 helper。Gradle `verifyTunnelAdbHelper` 拒绝 stale source/script/resource、protocol 或 JAR hash。临时目录/锁崩溃残留需构建负责人检查，不自动递归删除。
- 已固定的是 **upstream prebuilt 的获取内容**，不是 upstream 从 AOSP 到 executable 的完整可复现源码链。缺失的 publisher/signature、AOSP revision/toolchain、完整 SBOM、Android 16 与 16 KiB page-size 实测仍需补齐。快照完整性、离线供应、篡改拒绝的 Python 测试已通过；正式服务器构建、APK 安装、配对、uid 2000、采集和远程切换仍为 `NOT_RUN`。

## 5. Windows Drivers, DLLs and Helpers

| ID | 资产 | 当前存在性 | 仓内消费/证据锚点 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-WIN-001` | Amyuni/`usbmmidd_v2` virtual-display package | `SOURCE-RECORDED / BINARY-NOT-ACQUIRED`；官方asset180784412/199309bytes，无上游digest，服务器receipt与可选owner SHA256 | `src/virtual_display_manager.rs`, `scripts/windows_assets.py`/lock | `OWNER-REQUIRED` | vendor/source、INF/CAT/SYS/installer hashes、publisher/signature、license、OS support | active virtual display；正式运行/发行仍待验证 |
| `EXT-WIN-002` | `deviceinstaller64.exe` | `EXTERNAL`，随 `usbmmidd_v2` package 使用 | `src/virtual_display_manager.rs` | `OWNER-REQUIRED` | binary version/hash/signature/source/license | 高权限 driver install/uninstall `BLOCKING` |
| `EXT-WIN-003` | `WindowInjection.dll` / RustDeskTempTopMostWindow source | `SOURCE-PINNED / BINARY-NOT-BUILT`：固定commit53b548a5398624f7149a382000397993542ad796，服务器MSBuild/receipt；owner现有DLL只记录hash | `src/privacy_mode/win_topmost_window.rs`, `scripts/windows_assets.py`/lock | `OWNER-REQUIRED` | complete reproducible build、hash/Authenticode、license、ABI matrix | privacy injection运行/发行 `BLOCKING` |
| `EXT-WIN-004` | Printer driver package | `API-DIGEST-PINNED / NOT-DOWNLOADED`，v4-1.4与checksum表；服务器取得后核对 | `libs/remote_printer/`, `scripts/windows-assets.lock.json` | `OWNER-REQUIRED` | driver source、INF/CAT hashes、signature/publisher、license、OS matrix | printer install/release `BLOCKING` |
| `EXT-WIN-005` | `printer_driver_adapter.dll` | `API-DIGEST-PINNED / NOT-DOWNLOADED`，zip SHA256与checksum表；服务器PE x64校验 | `src/server/printer_service.rs`, `scripts/windows-assets.lock.json` | `OWNER-REQUIRED` | source/ABI/version/signature/license | runtime DLL与printer运行/发行 `BLOCKING` |
| `EXT-WIN-006` | `dylib_virtual_display.dll` | `GENERATED`：source tracked in `libs/virtual_display/`，artifact not tracked | Windows packaging and virtual display FFI | Release owner required | formal toolchain result、artifact hash、Authenticode、ABI test | source provenance较好；仍需正式 build/sign evidence |
| `EXT-WIN-007` | `RuntimeBroker_tunnel.exe` helper | `OS-DERIVED/UNVERIFIED`：运行时复制 Windows `RuntimeBroker.exe` 后改名；仓内不存在独立 helper source/binary 是预期状态 | `src/privacy_mode/win_topmost_window.rs`, Windows runtime copy path | Windows/Security owner required | supported Windows build matrix、源文件 Microsoft signature 校验、复制/注入/清理 contract、EDR compatibility | 行为随 OS build 漂移；privacy helper 路径必须按目标 OS 验证 |
| `EXT-WIN-008` | Windows `XpsPrint.dll` OS prerequisite | `OS-DERIVED`：`PrintXPSRawData` implementation 已 tracked，仓外依赖仅为 Windows OS API/DLL | `src/platform/windows.cc`, `src/platform/windows.rs` | Windows owner required | supported OS/API matrix、resource/error contract | 不是缺失的 Tunnel function；仍需 Win10/11 print compatibility 验证 |
| `EXT-WIN-009` | Dormant RustDesk IDD driver package | `DORMANT/MISSING`：user-mode dylib source tracked，driver INF/SYS/CAT 不在仓库；current active backend 为 Amyuni | `libs/virtual_display/dylib/src/win10/`, `src/virtual_display_manager.rs` | Architecture/Release owner required | 保留/淘汰决定；若启用则需 source/version/hash/signature/license/rollback | 不得误标为 active 或混入当前 release；启用前为 `BLOCKING` |

## 6. Signing, Build and Release Infrastructure

| ID | 资产 | 当前存在性 | 仓内消费/证据锚点 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-SIGN-001` | Android release keystore/signing environment | `EXTERNAL` | `build.sh`, Gradle signing configuration | Release/Security owner required | key owner/custody、alias policy、CI access、rotation、backup、certificate expiry | signed Android release `BLOCKING` |
| `EXT-SIGN-002` | Windows Authenticode identity、timestamp 与 remote signing service | `MISSING/EXTERNAL` | `res/job.py` 保留 remote signing client 线索；canonical Windows build 尚无完整签名 gate | Release/Security owner required | signing service repo/API、certificate chain、HSM/key custody、timestamp、ACL/audit、artifact hash binding、rotation/revoke | trustworthy Windows release `BLOCKING` |
| `EXT-BUILD-001` | Linux Android formal build host/toolchain | `EXTERNAL` | `build.sh`, `/opt/rustdesk-toolchain`-style layout | Release owner required | host image/version、SDK/NDK/JDK/Rust/Flutter/vcpkg locks、access、rebuild/runbook | Android V2 build evidence `BLOCKING` |
| `EXT-BUILD-002` | Windows formal build host and caches | `EXTERNAL` | `new-build.cmd`, `C:\DevEnv` / `C:\DevTool` layout | Release owner required | host image、VS/Rust/Flutter/LLVM/vcpkg locks、third-party cache provenance、access | Windows V2 build evidence `BLOCKING` |
| `EXT-BUILD-003` | Internal artifact registry / immutable binary store | `MISSING` | required by `libadb.so`, driver/DLL and release provenance | Release/Security owner required | storage、ACL、retention、checksum/signature manifest、promotion policy | clean-clone reproducibility and supply chain `BLOCKING` |
| `EXT-CI-001` | GitHub environments、runners、secrets、branch/release protections | `EXTERNAL/UNVERIFIED` | `.github/workflows/` 当前以 manual dispatch 为主；存在 mutable action refs 与自动 commit/push 型维护 workflow，仓内未见 `CODEOWNERS` | Repository/Release owner required | permission inventory、immutable Actions SHA、required checks、environment approval、runner trust、secret boundary | 现有 workflows 不能直接视作可信 release system |
| `EXT-REL-001` | Distribution channels and release storage | `EXTERNAL/UNVERIFIED` | legacy GitHub/WinGet/F-Droid/store workflows and packaging docs | Product/Release owner required | supported channels、accounts、publisher identity、approval、rollout/rollback、source offer | no authorized release path is currently documented |
| `EXT-COMP-001` | AGPL Corresponding Source / third-party notice delivery | `MISSING` | root AGPL license and binary distribution obligations | Legal/Product/Release owner required | upstream baseline、source archive/tag、offer mechanism、NOTICE/SBOM、legal review | commercial distribution compliance `BLOCKING` |

## 7. Upstream and Dependency Provenance

| ID | 资产 | 当前存在性 | 仓内证据 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-HIST-001` | RustDesk/DaXianDesk upstream baseline and historical repository | `MISSING` | 当前本地为非 shallow 的单 root commit；旧 `77062b4` 与 59-commit 演进记录不能在本地重证 | `OWNER-REQUIRED` | 原始 repo/bundle、exact fork point、patch lineage、authors、旧 baseline 对照、license/CVE mapping | differential maintenance、CVE triage、attribution `BLOCKING`；旧 public/remote 状态本轮未验证 |
| `EXT-DEP-001` | Cargo Git repositories and revisions | `EXTERNAL/PARTIAL` | Cargo manifests + lockfile; many manifests do not pin `rev` | Domain/Release owner required | approved source list、manifest pin、license/security review、mirror policy | lock update and supply-chain drift risk |
| `EXT-DEP-002` | Flutter/Gradle/Maven/vcpkg/Python/tool downloads | `EXTERNAL/PARTIAL` | pub/Gradle/vcpkg/build scripts | Domain/Release owner required | complete lock/verification、hash/signature、mirror/cache policy、license/SBOM | build reproducibility and dependency takeover risk |

普通、已锁定且可由标准 package manager 复现的依赖继续由 manifests/lockfiles 管理；本表登记的是未固定、高权限、binary-only 或 release-critical 外部资产。

## 8. Release Blockers Summary

正式发布前至少关闭：

1. `EXT-SVC-004` credential/token-broker incident response。
2. `EXT-BIN-ADB-001` Android ADB binary provenance。
3. `EXT-WIN-001`—`005` driver/DLL/helper provenance 与 signature。
4. `EXT-SIGN-001`、`002` signing custody 和 verification。
5. `EXT-BUILD-001`—`003` formal build environment 与 immutable artifact store。
6. `EXT-CI-001` / `EXT-REL-001` hardened approval、channel 和 rollback。
7. Full-system validation 所需的 backend、hbbs/hbbr、database、ZEGO contracts 和 owners。
8. `EXT-HIST-001` / `EXT-COMP-001` upstream、SBOM、NOTICE 和 source-offer governance。

关闭状态必须有证据，不能只把 `OWNER-REQUIRED` 改成姓名或把本机 binary 复制进仓库。

## 9. Asset Intake Template

```text
Registry ID：
Asset name/class：
Purpose and consumers：
Owner / on-call：
Source repository / vendor：
Version / commit：
Artifact hash / signature / publisher：
License / redistribution：
Acquisition and reproducible build：
Environment / dependency：
Authentication and secret owner（no values）：
Monitoring / backup / rollback：
Test evidence：
Release impact：
Status and review date：
```

## 10. Maintenance Rules

### 2026-10-02 T004 身份迁移补充

- `EXT-SVC-004`：仓内 broker 的请求字段、示例、systemd 服务与安装路径采用新品牌，request 为 `tunnelSessionId`。既有已部署实例/运维脚本未修改，owner 需配套部署或另做受控双字段兼容；不得声明仓库改名自动升级服务。
- 产品 version API 所属服务需识别 `tunnel-client` / `tunnel-server`。`src/common.rs::is_public` 和 sync 的品牌 host 分类同步为 `tunnel.`；这只是既有分类规则的文字迁移，不是新域名注册/解析或生产地址切换。实际端点未改，后续应单独验证该旧式字符串分类行为。
- `EXT-SIGN-001` / `002`：未轮换实际签名资产。App/bundle 改名不等于证书指纹变化；hash 和 fingerprint 以重建签名产物实测登记。Apple provisioning、Firebase 等外部注册必须对应新 bundle，旧资产不构成兼容证据。
- `EXT-BIN-ADB-HELPER-001`：helper Java package、HMAC 域、magic 已迁移；binary 仍 NOT_BUILT，配套 manifest/hash 待正式配方生成。不可复用旧 helper、旧 bridge/native 或 portable data.bin。
- `EXT-WIN-*`：第三方驱动、证书、硬件 GUID、ABI 名称保留真实值，没有通过改品牌伪造 vendor/source。安装元数据与本产品启动路径使用 Tunnel。
- 当前身份见 PROJECT_START_HERE.md；正式构建步骤见 docs/AI_ENGINEERING/08_BUILD_SYSTEM.md。

### 通用维护要求

- 新增仓外依赖、binary、driver、service 或 database 前先登记，再设计集成。
- version、source、hash、signature、owner、license 或 contract 改变时更新同一 ID，不复制新表。
- 不把 production value、credential 或 private URL 写入本文件。
- 不下载、安装、执行或替换登记资产，除非通过对应 `EXECUTE` / `DESTRUCTIVE` / `EXTERNAL` 确认门。
- 未取得资产本体时，只能验证仓内 contract 和调用边界。
- 每次 release readiness review 都复核所有 `BLOCKING` 条目和 review date。
