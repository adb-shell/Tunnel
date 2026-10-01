# CloudSend Project Memory

最后更新：2026-07-12  
用途：AI 会话的稳定入口，不替代源码和完整工程文档。

## 1. Entry and Source of Truth

所有 AI 先读：

1. `PROJECT_START_HERE.md`。
2. `.codex/AI_RULES.md`。
3. `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`。
4. 开发任务使用 `TASK_TEMPLATE.md`，并引用 `docs/BASELINE/BASELINE_INDEX.md`。
5. 长期决定查 `docs/ADR/README.md`；验证查 `TEST_MATRIX.md`。

工程事实层级：

1. 当前源码、manifest、build script 和 protocol definition。
2. `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md` 与对应领域文档。
3. `docs/ENGINEERING_*` 与 `docs/TASK_ENTRYPOINTS.md`，作为旧主套件和历史细节。
4. 本目录、`AGENTS.md`、`CLAUDE.md` 只作为规则、摘要和入口，不覆盖源码。

开发任务执行 `DEVELOPMENT_WORKFLOW.md`；仓外服务、database、driver 或 binary 查询 `EXTERNAL_ASSET_REGISTRY.md`。安全 Superpowers 只通过 `cloudsend-superpowers-safe` 的五项只读 allowlist 使用，外部包未安装。冲突时以源码为准；构建/运行行为只有正式环境验证后才能从 `verification-required` 提升为已验证。

## 2. Stable Identity

- 产品/runtime：`CloudSend`。
- Android 显示名：`云计划`。
- 来源：RustDesk 深度二次开发；本地 Git 只能追溯到 2026-04-13 的一次性 `DaXianDesk` 导入，导入前 upstream commit 未确认。
- Rust crate/library：`cloudsend`。
- Flutter package：`flutter_hbb`。
- Android applicationId：`com.cloudsend.app`。
- Android deep link scheme：`cloudsend`。
- 当前源码版本：Rust `5.2.1`，Flutter `5.2.1+59`；不得由 AI 自动修改。
- 根 license：AGPL-3.0；第三方 license/provenance 尚需完整审计。

## 3. Architecture Anchors

- Rust core：`src/`。
- shared config/protocol：`libs/hbb_common/`。
- capture：`libs/scrap/`。
- Flutter：`flutter/lib/`。
- Android runtime：`flutter/android/app/src/main/kotlin/com/cloudsend/app/`。
- Android active JNI：`libs/scrap/src/android/pkg2230.rs`；`ffi.rs` 是未导出的兼容层。
- Windows privacy/display：`src/privacy_mode.rs`、`src/privacy_mode/`、`src/virtual_display_manager.rs`。
- account/API/sync/download：`src/hbbs_http/` 与 Flutter models。
- external infrastructure：hbbs、hbbr、产品 API、ZEGO Token broker/RTC；服务端实现不在本仓库。

完整架构见 `docs/AI_ENGINEERING/01_ARCHITECTURE.md`。

## 4. Non-Negotiable Runtime Facts

- Android 必须分 core service、screen share、frame source、PC waiting 四层状态。
- 只有明确用户操作可以请求新的 MediaProjection permission。
- waiting/reconnect 只可刷新已有 normal video，不可自动切 ignore/screenshot。
- Android 真实 RGBA/Texture frame 必须清 PC waiting。
- controller 当前强制 relay；受控端仍保留 rendezvous/direct/NAT compatibility code。
- Windows 当前 virtual display implementation 是 Amyuni；RustDesk IDD 分支是 dormant。
- Android ADB 是设备本地 LADB 子系统，不是 PC remote ADB protocol。
- ZEGO 媒体不走原 RustDesk audio service；peer protocol 只传 invitation/control state。
- `verify_login()` 的 legacy UI bypass 不等于产品 API、hbbs 或 endpoint authentication bypass。

## 5. Highest Risks

- Public repository/history 中存在真实 credential 类型的字面值；值不得复制到文档或对话。
- 构建内置共享远控密码可由公开源码获知，permanent-password setter 不真正更新；需要每设备高熵凭据迁移。
- Android ImageReader DirectBuffer ownership 可能失效；JNI `static mut` 存在 data-race/UB 风险。
- Android input/custom command 在受控端的 permission enforcement 不完整。
- Android custom-scheme config import 可改写 rendezvous/API/trust key；update/plugin 缺完整 signature/hash/containment gate。
- ZEGO token transport/credential 和 Android auto-accept 存在隐私风险。
- peer secure handshake、secretbox nonce 和本地 password protection 需密码学专项审计。
- 产品 HTTP/sync 路径存在 plaintext/auth boundary 风险。
- Windows privacy/injection/virtual display 是高权限、崩溃恢复敏感区。
- ignored `libadb.so` 和 driver/helper assets 破坏 clean-clone reproducibility。

风险详情见 `docs/AI_ENGINEERING/10_SECURITY_MODEL.md`。

## 6. Mutation Rules

- 未经用户明确确认：不 commit、push、merge、rebase、stage、release、upload、version bump、删除源码或文档。
- 当前环境不是正式编译环境：不执行 Cargo/Flutter/Gradle/Android/Windows/Docker build/test。
- 需要验证时输出《编译验证需求》，列命令、环境、目录、目标，等待正式环境结果。
- 不修改无关代码，不覆盖用户工作树，不清理历史资产。
- 源码事实变化时同步更新 AI engineering docs、decision log 与 task history。
- 所有 secret、token、password、key、生产地址和 PII 必须脱敏。
- 所有开发任务记录 Baseline ID、相关 ADR 和 TEST_MATRIX case IDs。
- Superpowers adapter 仅 brainstorming/planning/debugging/verification/review；commit/push/release hard-denied。

完整规则见 `.codex/AI_RULES.md`。

## 7. Open Asset Gaps

- 2026-04-13 之前的 upstream/DaXianDesk history。
- hbbs/hbbr source、version、config 和部署拓扑。
- 产品 backend/OpenAPI/DB/schema/migration/backup。
- ZEGO broker 独立受控 project/commit、dependency lock、production provenance 和 credential ownership；当前 deployment script 只内嵌了部分 Go server source。
- ADB binary、Windows driver/helper 的 source revision、license 和 hashes。
- 正式 Android/Windows build 与 regression evidence。

这些缺口未补齐前，不得宣称全系统接管或发布就绪。

完整状态、owner、所需材料和 release impact 统一维护在 `EXTERNAL_ASSET_REGISTRY.md`，本节只保留摘要。
