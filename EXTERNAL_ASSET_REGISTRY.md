# CloudSend External Asset Registry

最后更新：2026-10-02，Task `T-2026-10-02-001`
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
| `EXT-BIN-ADB-001` | `libadb.so` for arm64-v8a / armeabi-v7a / x86_64 | `MISSING`：2026-10-02 当前 `jniLibs/` 不存在；旧记录为 ignored/local-only | `CloudSendAdbRunner.kt`, Android packaging | `OWNER-REQUIRED` | upstream/source commit、reproducible build recipe、per-ABI hash、signature、version、license mapping、artifact registry | clean clone 不能复现 ADB packaging；Android release `BLOCKING` |
| `EXT-REF-ADB-002` | `ADB-CODE/` research/decompiled materials | `MISSING`：2026-10-02 当前目录不存在，ignore 规则仍在；historical local-only | `docs/ADB_LADB_INTEGRATION_MEMORY.md` 的研究背景 | `OWNER-REQUIRED` | origin、revision、legal/provenance、是否仅研究使用、保留策略 | 旧研究结论目前不可独立重证；不得直接进入 release source |
| `EXT-REF-ADB-003` | `LADB/` reference source | `MISSING`：2026-10-02 当前目录不存在，ignore 规则仍在；historical local-only | ADB integration memory 与实现设计背景 | `OWNER-REQUIRED` | upstream URL/commit、license obligations、旧文档所述分发限制的原始证据、修改清单 | license/distribution 需资产到位后复核；不得等同 bundled binary source |

**历史快照，不能作为本机验收：** 2026-07-12 文档记录当时存在三个 `libadb.so` 和一份 license 文件且均未 tracked。2026-10-02 当前 worktree 未找到这些文件，因此下列 SHA-256 只保留为历史交接线索，没有重新计算或核验：

| ABI | Historical SHA-256 recorded on 2026-07-12 |
|---|---|
| `arm64-v8a` | `47EA035FA5ED57F6149A2B025BBBD4B21584C355C05D0400416804715E4C12DE` |
| `armeabi-v7a` | `0AFEA102225CD4DDA85D2C01F36A56473724B741CC63B6386163EE286EFF268E` |
| `x86_64` | `62CC0F7707C83C98AA4DE699492C61091EDA9D68B3DB9EA2A185B179DA505BFA` |

这些历史 hash 不能证明当前资产存在、source、publisher、license、完整性或 release approval。材料补齐后需同时核对 source/license/recipe 与实际文件，不能只匹配历史 hash。

## 5. Windows Drivers, DLLs and Helpers

| ID | 资产 | 当前存在性 | 仓内消费/证据锚点 | Owner | 主要缺口 | 影响 |
|---|---|---|---|---|---|---|
| `EXT-WIN-001` | Amyuni/`usbmmidd_v2` virtual-display package | `EXTERNAL/UNVERIFIED`，正式构建从外部 cache 复制 | `src/virtual_display_manager.rs`, `new-build.cmd` | `OWNER-REQUIRED` | vendor/upstream source/version、INF/CAT/SYS/installer hashes、publisher/signature、license、OS support | current active virtual display；Windows build/release `BLOCKING` |
| `EXT-WIN-002` | `deviceinstaller64.exe` | `EXTERNAL`，随 `usbmmidd_v2` package 使用 | `src/virtual_display_manager.rs` | `OWNER-REQUIRED` | binary version/hash/signature/source/license | 高权限 driver install/uninstall `BLOCKING` |
| `EXT-WIN-003` | `WindowInjection.dll` / RustDeskTempTopMostWindow source | `EXTERNAL/UNVERIFIED`；当前 binary 缺失，workflow 能从固定 upstream commit 构建，但 action/upload provenance 仍可漂移 | `src/privacy_mode/win_topmost_window.rs`, `new-build.cmd`, legacy workflow | `OWNER-REQUIRED` | approved source fork/commit、immutable CI actions、reproducible build、hash/Authenticode、license、ABI matrix | privacy injection 与本地高权限代码执行 `BLOCKING` |
| `EXT-WIN-004` | Printer driver package | `EXTERNAL/UNVERIFIED` | `libs/remote_printer/`, `new-build.cmd` | `OWNER-REQUIRED` | driver source/version、INF/CAT/SYS hashes、signature/publisher、license、OS matrix | remote printer install/release `BLOCKING` |
| `EXT-WIN-005` | `printer_driver_adapter.dll` | `EXTERNAL/UNVERIFIED` | `src/server/printer_service.rs`, `new-build.cmd` | `OWNER-REQUIRED` | source/ABI/version/hash/signature/license | runtime DLL loading and printer service `BLOCKING` |
| `EXT-WIN-006` | `dylib_virtual_display.dll` | `GENERATED`：source tracked in `libs/virtual_display/`，artifact not tracked | Windows packaging and virtual display FFI | Release owner required | formal toolchain result、artifact hash、Authenticode、ABI test | source provenance较好；仍需正式 build/sign evidence |
| `EXT-WIN-007` | `RuntimeBroker_cloudsend.exe` helper | `OS-DERIVED/UNVERIFIED`：运行时复制 Windows `RuntimeBroker.exe` 后改名；仓内不存在独立 helper source/binary 是预期状态 | `src/privacy_mode/win_topmost_window.rs`, Windows runtime copy path | Windows/Security owner required | supported Windows build matrix、源文件 Microsoft signature 校验、复制/注入/清理 contract、EDR compatibility | 行为随 OS build 漂移；privacy helper 路径必须按目标 OS 验证 |
| `EXT-WIN-008` | Windows `XpsPrint.dll` OS prerequisite | `OS-DERIVED`：`PrintXPSRawData` implementation 已 tracked，仓外依赖仅为 Windows OS API/DLL | `src/platform/windows.cc`, `src/platform/windows.rs` | Windows owner required | supported OS/API matrix、resource/error contract | 不是缺失的 CloudSend function；仍需 Win10/11 print compatibility 验证 |
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

- 新增仓外依赖、binary、driver、service 或 database 前先登记，再设计集成。
- version、source、hash、signature、owner、license 或 contract 改变时更新同一 ID，不复制新表。
- 不把 production value、credential 或 private URL 写入本文件。
- 不下载、安装、执行或替换登记资产，除非通过对应 `EXECUTE` / `DESTRUCTIVE` / `EXTERNAL` 确认门。
- 未取得资产本体时，只能验证仓内 contract 和调用边界。
- 每次 release readiness review 都复核所有 `BLOCKING` 条目和 review date。
