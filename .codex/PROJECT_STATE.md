# Tunnel Project State

Schema Version：`1.0`  
State Revision：`STATE-20261002-004`
Last Updated：2026-10-02（Asia/Shanghai）
Observed At：2026-10-02（Asia/Shanghai）
Evidence Scope：`repository-observed / V0 / external and runtime verification pending`

> 本文件是截至 `Observed At` 的当前状态快照，不是生产实时监控，也不替代源码、`docs/AI_ENGINEERING/`、ADR 或 Baseline。新会话必须用只读检查确认其新鲜度；发现漂移时记录 conflict/stale，不得用本文件覆盖源码事实或改写旧 Baseline。

## 1. Source and Product Snapshot

| Field | Last Observed State | Evidence |
|---|---|---|
| Product/runtime | `Tunnel` | source + `PROJECT_MEMORY.md` |
| Rust / Flutter version | `5.2.1` / `5.2.1+59` | manifests + Baseline |
| Baseline ID | `TUN-BL-2026-10-02-IDENTITY` | `docs/BASELINE/BASELINE_INDEX.md`；当前未提交身份迁移 |
| Branch | test（独立worktree） | 本任务只读Git观察；用户选择后续在worktree开发 |
| HEAD | `532637a7b4084ec8ebf7deddbab12982628b1c1c` | 较业务baseline仅文档提交；本轮未做Git写入 |
| Worktree | T002方案/T003默认关闭P0源码保留；T004全仓品牌身份、包路径和消费者迁移；未stage/commit | T-2026-10-02-004；每次会话重查 |
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
| Remote ADB mirroring | 方案接受分阶段实施；默认关闭的本机P0源码已加，remote未接入；P0未通过 | docs/plans/ADB_P0_VALIDATION_RUNBOOK.md；ADR-0014 accepted, staged |

## 3. Current Blocking Areas

| Category | Representative IDs / Gap | Current State | Owner / Next Gate |
|---|---|---|---|
| Credential/security incident | `SEC-001` and related credential-type exposure | 当前tracked字面量；有效性/远端公开性/历史传播未验；不复制值 | project owner + Security / G0 |
| Transport/auth/privacy/logs | `SEC-002`—`SEC-019` applicable items | source-reviewed；auth前connect、日志、terminal owner等未动态复现 | domain + Security / G1—G2 |
| Backend and data | `EXT-SVC-001`—`003`, `EXT-DATA-001` | server/source/schema/owner missing | backend/infra owner |
| ZEGO service | `EXT-SVC-004`, `EXT-SVC-005` | partial/external；incident and lock drift | API/Security owner |
| Android native assets | `EXT-BIN-ADB-001`、`EXT-REF-ADB-002`、`EXT-REF-ADB-003` | 当前MISSING；旧local-only/hash仅历史 | Android/Release owner |
| Remote ADB helper | EXT-SRC-ADB-HELPER-001、EXT-BIN-ADB-HELPER-001 | SOURCE_IMPORTED / V0；binary NOT_BUILT；P0未运行 | Android/Release/Security |
| Windows native assets | `EXT-WIN-001`—`009` applicable items | external/generated/unverified | Windows/Release/Security owner |
| Build/sign/release | `EXT-SIGN-*`, `EXT-BUILD-*`, `EXT-CI-001`, `EXT-REL-001` | formal environments and custody incomplete | Release owner / G2—G3 |
| Upstream/compliance | `EXT-HIST-001`, `EXT-COMP-001` | exact fork lineage、SBOM/NOTICE/source-offer incomplete | source/legal/release owner |

## 4. Current Memory Pointers

- Active task registry：`CURRENT_WORK.md`。
- Detailed task history：`TASK_HISTORY.md`。
- Latest completed AI task：`T-2026-10-02-004`（身份迁移源码/V0；runtime未验）。
- Current active task：T-2026-10-02-003，paused / T6；用户要求先改名后编译；不是整体P0完成。
- Latest recorded modification event：`CE-20261002-T004-01`。
- Latest governance decision：`D-014`（accepted and synchronized）。
- Architecture decisions：ADR-0014 / D-015 accepted for staged implementation；ADR-0007的local-only边界在P0证据门前保持。
- Identity decision：ADR-0015 / D-016 accepted；supersedes ADR-0002；新Android包com.tunnel.app、ORG com.tunnel；配置/授权独立，外部broker另行部署，证书未变。
- 本轮Decision assessment：用户明确授权身份迁移源码与必要路径移动，工作树交付；未执行C3构建/设备/Git。
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
