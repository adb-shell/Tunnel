# Tunnel Project State

Schema Version：`1.0`  
State Revision：`STATE-20261003-006`
Last Updated：2026-10-03（Asia/Shanghai）
Observed At：2026-10-03（Asia/Shanghai）
Evidence Scope：`repository-observed / V0 / external and runtime verification pending`

> 本文件是截至 `Observed At` 的当前状态快照，不是生产实时监控，也不替代源码、`docs/AI_ENGINEERING/`、ADR 或 Baseline。新会话必须用只读检查确认其新鲜度；发现漂移时记录 conflict/stale，不得用本文件覆盖源码事实或改写旧 Baseline。

## 1. Source and Product Snapshot

| Field | Last Observed State | Evidence |
|---|---|---|
| Product/runtime | `Tunnel` | source + `PROJECT_MEMORY.md` |
| Rust / Flutter version | `5.2.1` / `5.2.1+59` | manifests + Baseline |
| Baseline ID | `TUN-BL-2026-10-03-ADB` | `docs/BASELINE/BASELINE_INDEX.md`；HEAD+当前源码补丁 |
| Branch | test（独立worktree） | 本任务只读Git观察；用户选择后续在worktree开发 |
| ADB实施起点 | `bc50fe5b2061970e4e88ff58bbd76c7827de1159` | 初始干净；后续交付提交由Git log确认 |
| Source delivery | ADB全链源码、本地配对/native供应、A11y适配、portable及文档；用户授权提交“ADB版本首次测试”并推送origin/test | T-2026-10-03-001；每次会话重查当前HEAD与远端ref |
| Current source | test；基于3c22a25，T003 Windows构建修复+T004远程配对+T005会话生命周期；当前用户授权Git交付“尝试修复ADB” | 精确HEAD与origin/test在每次会话以Git重新核对；本次范围为现有源码与文档，不包含正式构建/运行 |
| Windows build baseline | TUN-BL-2026-10-03-WINDOWS-BUILD；统一CMD/PS入口、固定driver供应、Cargo DLL路径及payload核验 | docs/plans/WINDOWS_BUILD_GUIDE.md；V0，正式服务器NOT_RUN |
| Highest completed verification | `V0` repository/document/schema checks | `TEST_MATRIX.md` + task records |
| Formal build/runtime evidence | `NOT_RUN / VERIFICATION-REQUIRED` | 本轮未执行；未提供绑定当前HEAD的正式证据 |
| Release readiness | `BLOCKED` | security、external assets、V2—V4、signing/SBOM/rollback gaps |

## 2. Governance and Knowledge Readiness

| Area | State | Canonical Reference |
|---|---|---|
| Repository archaeology | 全仓inventory+关键链V0复核完成；旧Git历史不可重放 | `docs/AI_ENGINEERING/audits/2026-10-02/README.md` |
| AI task governance | T0—T8、C0—C3、ADR、Baseline、Task Template、Test Matrix active | `PROJECT_START_HERE.md` + task protocol |
| Global session memory | 既有体系在`T-2026-10-02-001`重核并同步 | `SESSION_START_PROTOCOL.md` + memory files |
| Rust/Flutter/Android/Windows source understanding | 关键路径深查V0；其他平台/全部异常分支非全面审计 | domain docs + dated coverage tables |
| External infrastructure | incomplete / external | `EXTERNAL_ASSET_REGISTRY.md` |
| Formal build/device/integration validation | not executed | `TEST_MATRIX.md` |
| Commercial release gate | not satisfied | Security Model + External Registry |
| Remote ADB mirroring | 全链源码接入；手机ADB页移除，PC typed pair/authorize fresh probe后conn会话scopes；无10分钟/生产helper一小时期限，退出ADB撤销，断线/撤权/取消阻断lategrant；P0—P6未验收 | docs/plans/ADB_REMOTE_IMPLEMENTATION_GUIDE.md；ADR-0016限定替代phone-local consent；ADR-0017会话生命周期；ADBP-01—12 NOT_RUN |

## 3. Current Blocking Areas

| Category | Representative IDs / Gap | Current State | Owner / Next Gate |
|---|---|---|---|
| Credential/security incident | `SEC-001` and related credential-type exposure | 当前tracked字面量；有效性/远端公开性/历史传播未验；不复制值 | project owner + Security / G0 |
| Transport/auth/privacy/logs | `SEC-002`—`SEC-019` applicable items | source-reviewed；auth前connect、日志、terminal owner等未动态复现 | domain + Security / G1—G2 |
| Backend and data | `EXT-SVC-001`—`003`, `EXT-DATA-001` | server/source/schema/owner missing | backend/infra owner |
| ZEGO service | `EXT-SVC-004`, `EXT-SVC-005` | partial/external；incident and lock drift | API/Security owner |
| Android native assets | `EXT-BIN-ADB-001`、`EXT-REF-ADB-002`、`EXT-REF-ADB-003` | 官方LADB固定blob供应脚本/receipt校验；本地binary仍未取得 | Android/Release owner |
| Remote ADB helper | EXT-SRC-ADB-HELPER-001、EXT-BIN-ADB-HELPER-001 | SOURCE_IMPORTED / V0；binary NOT_BUILT；P0未运行 | Android/Release/Security |
| Windows native assets | `EXT-WIN-001`—`009` applicable items | external/generated/unverified | Windows/Release/Security owner |
| Build/sign/release | `EXT-SIGN-*`, `EXT-BUILD-*`, `EXT-CI-001`, `EXT-REL-001` | formal environments and custody incomplete | Release owner / G2—G3 |
| Upstream/compliance | `EXT-HIST-001`, `EXT-COMP-001` | exact fork lineage、SBOM/NOTICE/source-offer incomplete | source/legal/release owner |

## 4. Current Memory Pointers

- Active task registry：`CURRENT_WORK.md`。
- Detailed task history：`TASK_HISTORY.md`。
- Latest completed AI task：`T-2026-10-02-004`（身份迁移源码/V0；runtime未验）。
- Current implementation：ADB T-2026-10-03-001承接T-2026-10-02-003；Windows后续T-2026-10-03-003源码/V0修复交付，正式服务器/设备验收待执行；状态以CURRENT_WORK为准。
- Latest recorded modification event：`CE-20261003-T005-02`（当前用户授权提交/推送“尝试修复ADB”；结果以Git核对）。
- Latest architecture amendment：`D-019` / ADR-0017（授权持续至会话结束、生产helper无固定期限）；治理决定D-014仍保持。
- Architecture decisions：ADR-0014 / D-015由D-017追记用户顺序调整；有限typed远程范围扩展ADR-0007，任意shell仍local-only。
- Identity decision：ADR-0015 / D-016 accepted；supersedes ADR-0002；新Android包com.tunnel.app、ORG com.tunnel；配置/授权独立，外部broker另行部署，证书未变。
- 本轮Decision assessment：用户明确要求先完成P0—P6源码及三项故障修复；仅工作树源码/V0，未执行本地构建/设备/Git/发布。
- Coverage / feature navigation：`docs/AI_ENGINEERING/audits/2026-10-02/README.md`、`docs/AI_ENGINEERING/12_FEATURE_MAP.md`。

## 5. Standing Authority State

- There is no standing C2 or C3 authorization.
- Historical approvals in memory、Task History、Decision Log or ADR never transfer to a new session.
- `T-2026-10-02-002` C1方案文档任务已关闭；后续实施从P0和具体当前授权范围开始。
- Git write、build/test/analyze/codegen、delete/move、version/sign/package、upload/deploy/release and production/credential operations remain forbidden.
- T004已完成的必要改名路径移动由该次明确用户指令授权；此记录不扩大后续会话权限。

## 6. Freshness and Update Contract

At every new session:

1. Compare branch、HEAD、dirty state and Baseline against a new read-only observation.
2. Read `CURRENT_WORK.md` and detect concurrent、stale or conflicting tasks.
3. If source or canonical engineering documents disagree with this file, mark this state stale and return to T0/T2.
4. Baseline drift creates a new Baseline ID through the approved process；never rewrite the old snapshot.
5. Do not store secret values、production addresses、peer/device identifiers、PII、absolute user paths or full command output here.

For an authorized persisted-change task, update this file at T7/T8 after canonical sources and event records are synchronized. A C0 read-only task reports `reviewed-no-change` or `not-authorized` instead of editing this file.
