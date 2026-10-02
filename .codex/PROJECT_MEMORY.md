# Tunnel Project Memory

最后更新：2026-10-03

当前ADB增量T-2026-10-03-001：用户已要求先交付P0—P6源码，再在其他服务器编译、以OnePlus ACE6T/iQOO Neo9 Android16测试。实现/路径/限制和验证见 `docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md`；仅V0，不是设备PASS。T003暂停已由继续指令解除并承接，旧P0-only描述仅为历史阶段。
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

开发任务执行 `DEVELOPMENT_WORKFLOW.md`；仓外服务、database、driver 或 binary 查询 `EXTERNAL_ASSET_REGISTRY.md`。安全 Superpowers 只通过 `tunnel-superpowers-safe` 的五项只读 allowlist 使用；外部包安装状态不是本次观察内容。冲突时以源码为准；构建/运行行为只有正式环境验证后才能从 `verification-required` 提升为已验证。

当前接管索引：`docs/AI_ENGINEERING/audits/2026-10-02/README.md`；功能定位：`docs/AI_ENGINEERING/12_FEATURE_MAP.md`；当前baseline：`TUN-BL-2026-10-02-IDENTITY`，前基线库存/hash不可继承为迁移后的值。最新task/event状态从 `PROJECT_STATE.md` / `CURRENT_WORK.md`读取，不依赖旧聊天记忆。

## 2. Stable Identity

- 产品/runtime：`Tunnel`。
- Android 显示名：`隧道`。
- 来源：RustDesk 深度二次开发；当前本地Git非shallow，仅`5cee692`单root（2026-10-01），旧`77062b4`及导入历史不可重放；旧演进文档保持historical。
- Rust crate/library：`tunnel`。
- Flutter package：`flutter_hbb`。
- Android applicationId：`com.tunnel.app`。
- Android deep link scheme：`tunnel`。
- Runtime ORG：`com.tunnel`。新包名/命名空间独立；原数据、配对、授权不自动迁移；签名证书未变。
- 身份决定：ADR-0015 / D-016；全仓历史品牌文字已规范化但不等于历史逐字快照。PC/APK/native/helper/外部broker需配套验证；T003暂停且P0未通过。
- 当前源码版本：Rust `5.2.1`，Flutter `5.2.1+59`；不得由 AI 自动修改。
- 根 license：AGPL-3.0；第三方 license/provenance 尚需完整审计。

## 3. Architecture Anchors

- Rust core：`src/`。
- shared config/protocol：`libs/hbb_common/`。
- capture：`libs/scrap/`。
- Flutter：`flutter/lib/`。
- Android runtime：`flutter/android/app/src/main/kotlin/com/tunnel/app/`。
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
- Android ADB包含本地LADB与受控remote协议；PC只经既有加密会话发送typed操作，手机本机consent/scopes/TTL，任意shell仍local-only。
- ZEGO 媒体不走原 RustDesk audio service；peer protocol 只传 invitation/control state。
- `verify_login()` 的 legacy UI bypass 不等于产品 API、hbbs 或 endpoint authentication bypass。
- terminal已实现进程内persistent registry与service-ID reattach；不等于跨重启耐久化，peer-owner隔离仍待验证。
- PC waiting不得切ignore与Android screen-off/projection-stop有条件fallback同时成立；MethodChannel/JNI的同名`start_capture`语义不同。

## 5. Highest Risks

- 当前tracked源码/部署资料存在credential类型字面值；有效性、远端公开性和历史传播未在本轮核验；值不得复制。
- 构建内置共享远控密码可从客户端源码/产物提取，permanent-password setter 不真正更新；需要每设备高熵凭据迁移。
- Android ImageReader DirectBuffer ownership 可能失效；JNI `static mut` 存在 data-race/UB 风险。
- Android input/custom command 在受控端的 permission enforcement 不完整。
- Android custom-scheme config import 可改写 rendezvous/API/trust key；update/plugin 缺完整 signature/hash/containment gate。
- ZEGO token transport/credential 和 Android auto-accept 存在隐私风险。
- peer secure handshake、secretbox nonce 和本地 password protection 需密码学专项审计。
- 产品 HTTP/sync 路径存在 plaintext/auth boundary 风险。
- Windows privacy/injection/virtual display 是高权限、崩溃恢复敏感区。
- 当前缺失的`libadb.so`及external driver/helper assets使clean-clone reproducibility未闭环。
- 新增静态风险：认证前PortForward outbound connect、敏感参数日志、Android MultiClipboards permission、terminal owner绑定、privacy hook/restore失败顺序；详见Security Model，不视为已复现。

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

## 7. Current Development Direction

- 用户已选择后续开发在独立worktree进行；不要默认修改原项目目录。Git提交/合并/同步按具体任务执行，工作区之间不自动同步文件。
- 2026-10-02，T-2026-10-02-002形成`docs/plans/ADB_REMOTE_MIRRORING_PLAN.md`：远程ADB投屏/输入、侧按钮、无障碍管理、P0—P6和ADBM-01—30。
- ADR-0014 / D-015已接受分阶段实施；T003新增默认关闭的本机身份/helper/H264采样原型源码和测试源码；未build/test/device，P0整体未通过。ADR-0007 local-only边界保持。不得把“收到编码样本”当PC解码/呈现或远程权限已实现。
- 原型地图/精确验证需求：`docs/plans/ADB_P0_VALIDATION_RUNBOOK.md`。旧Runner自动重连与持久shell长命令仍能打断原型，正式remote前必须完成transport lease；普通共享/无障碍运行未改。
- 后续先验证APK本机自举helper→原relay→PC解码，及节点/系统启用/防触profile；不能承诺所有ROM或受保护内容支持。

## 8. Open Asset Gaps

- 当前快照之前的完整Git/upstream/DaXianDesk history，包括旧文档所引用对象。
- hbbs/hbbr source、version、config 和部署拓扑。
- 产品 backend/OpenAPI/DB/schema/migration/backup。
- ZEGO broker 独立受控 project/commit、dependency lock、production provenance 和 credential ownership；当前 deployment script 只内嵌了部分 Go server source。
- ADB binary、Windows driver/helper 的 source revision、license 和 hashes。
- 当前本机仍未获取native二进制；`android-adb/ladb-prebuilt.lock.json`固定官方LADB commit/blob/ABI，服务器prepare与Gradle校验补齐供应；旧本机hash不等于当前资产已取得。helper protocol2须重新构建，普通remote打包独立于P0诊断flag。
- 正式 Android/Windows build 与 regression evidence。

这些缺口未补齐前，不得宣称全系统接管或发布就绪。

完整状态、owner、所需材料和 release impact 统一维护在 `EXTERNAL_ASSET_REGISTRY.md`，本节只保留摘要。
