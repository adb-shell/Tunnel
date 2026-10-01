# CloudSend Engineering Task Template

> Copy this template for every development, diagnosis, migration, security, build or release-readiness task. Fill every field or write `N/A — reason`. The template does not authorize any action and must not contain secrets, production addresses, peer/device identifiers or PII.

## 0. Task Metadata

| Field | Value |
|---|---|
| Task ID / Title | |
| Status | `draft / analyzing / awaiting-confirmation / approved / implementing / verifying / blocked / complete / cancelled` |
| Current T-State | `T0`—`T8` |
| Requester / Product Owner | |
| Principal Engineer / Primary Skill | |
| Domain Owners | |
| Security Reviewer | |
| Release Owner | |
| Created / Updated | |
| Source Branch / HEAD | |
| Baseline ID | See `docs/BASELINE/BASELINE_INDEX.md` |
| Initial Dirty State / Existing User Changes | |
| Risk Level | `low / medium / high / critical` |
| Required Highest Verification | `V0`—`V5` |
| Related ADR / D-ID | |
| Related TEST_MATRIX IDs | |
| Related External Asset IDs | |
| Project State Revision | `.codex/PROJECT_STATE.md` |
| Current Work Registry Revision | `.codex/CURRENT_WORK.md` |
| Change Event IDs | |

## T0. Authority and Baseline

- Session Recovery Record / state consistency：
- Task type：
- Exact authorized outcome：
- Authorized edits/actions：
- Explicitly forbidden：
- Current permission ceiling：`C0 / C1 / C2 / C3 subset`
- Non-goals：
- Stop conditions：
- Worktree conflicts/protection plan：

### Confirmation Record

| Level / Action Class | Applicable | Authorized By / Time | Exact Scope | Still Forbidden | Reconfirmation Trigger |
|---|---|---|---|---|---|
| C0 observe | | | | | |
| C1 docs/rules/Skills | | | | | |
| C2 business code/config/protocol/dependency/build script | | | | | |
| C3 Git write | | | | | |
| C3 build/test/analyze/codegen/device/integration | | | | | |
| C3 delete/move/history rewrite | | | | | |
| C3 version/sign/package | | | | | |
| C3 upload/deploy/release/store | | | | | |
| C3 production/secret/credential | | | | | |

One C3 row never authorizes another. `N/A` is not approval.

## T1. Requirements

### Problem and Current Behavior

- Background / evidence：
- Current observable behavior：
- Expected observable behavior：
- Affected versions/platforms/environments：

### Acceptance Criteria

1. 

### Assumptions and Unknowns

- Assumptions：
- Unknowns and owner：
- Questions requiring product/security/external decision：

## T2. Impact Map

| Domain | Entry / Call Path | Files/Modules | Impact | Owner | Evidence Level |
|---|---|---|---|---|---|
| Rust/FFI | | | | | |
| Flutter | | | | | |
| Android | | | | | |
| Windows | | | | | |
| Network/Protocol | | | | | |
| API/Data | | | | | |
| Security/Privacy/License | | | | | |
| Build/Release | | | | | |
| Documentation/Memory | | | | | |

### Asset Classification

| Asset | `active / compatibility / dormant / generated / tracked / local-only / external / missing` | Owner / Provenance | Impact |
|---|---|---|---|
| | | | |

### Compatibility Surface

- Protocol field/default/unknown behavior：
- Persisted config/schema/data：
- Old controller/new endpoint and reverse：
- OS/device/ABI/window/session：
- External service/version skew：

## T3. Design

### Recommended Design

- Summary：
- Why：
- Cross-layer order：
- Architecture invariants：
- Fail-open/fail-closed behavior：

### Alternatives

| Option | Benefits | Costs/Risks | Decision |
|---|---|---|---|
| | | | |

### ADR Assessment

- New long-term decision required?：
- Related/conflicting ADRs：
- Proposed ADR status/owner：

### Migration and Rollback Before Change

- Safe abort point：
- Code/config/protocol/data rollback：
- Compatibility window：
- Feature flag / kill switch：
- Irreversible operation：
- Rollback owner and verification：

## T4. Implementation Confirmation

```text
《操作确认》
- Requested action and target：
- Exact files/modules/environment：
- Expected side effects：
- Stop boundary：
- Rollback：
- Authorized by / time：
```

If confirmation is missing or scope expands, stop before T5.

## T5. Change Record

| Planned File | Actual File | Source of Truth / Generated | Behavior Difference | Existing User Change Protected |
|---|---|---|---|---|
| | | | | |

- Out-of-plan discoveries：
- Reconfirmation performed：
- Dependency/version/artifact changes：
- Generated output handling：
- Logical Change Event IDs：

## T6. Verification

### Verification Level Summary

| Level | Required | Planned Cases / Environment | Executed | Result | Evidence / Not-run Reason | Owner |
|---|---|---|---|---|---|---|
| V0 static | | | | | | |
| V1 lint/unit/analyzer | | | | | | |
| V2 formal product build | | | | | | |
| V3 device/OS runtime | | | | | | |
| V4 isolated integration/security | | | | | | |
| V5 staging/production observation | | | | | | |

- Required highest level：
- Achieved highest level：
- `TEST_MATRIX.md` case selection rationale：

### Test Cases

| Case ID | Domain | Preconditions | Action | Expected Oracle | Level | Status | Evidence |
|---|---|---|---|---|---|---|---|
| | | | | | | `NOT_RUN` | |

Allowed result states: `PASS / FAIL / BLOCKED / NOT_RUN / N/A / WAIVED`. Only `PASS` is pass.

### 《编译验证需求》

```text
- 命令：
- 环境要求：
- 执行目录：
- 验证目标：
```

Use only when formal execution is required but not authorized/available. Do not run it from the template.

## Security / Privacy / Compliance Review

| Review Item | Finding / Control | Evidence | Reviewer | Residual Risk |
|---|---|---|---|---|
| Trust boundary / untrusted source / privileged sink | | | | |
| Authentication / authorization / consent | | | | |
| Credential / PII / retention / redaction | | | | |
| Crypto / network / replay / downgrade | | | | |
| Download/update/plugin/path handling | | | | |
| JNI/unsafe/concurrency | | | | |
| ADB/Accessibility/driver/injection | | | | |
| License/SBOM/source offer | | | | |

## Build / Release Review

| Item | Required State / Evidence | Current State | Owner |
|---|---|---|---|
| Target platform/channel | | | |
| Toolchain/build environment | | | |
| Generated output/artifact provenance | | | |
| Version/SBOM/hash/signature | | | |
| External asset readiness | | | |
| Install/upgrade/uninstall | | | |
| Rollout/monitoring/rollback | | | |
| G2/G3 status | | | |

This review does not authorize version、build、sign、package、upload or release.

## T7. Documentation Delta

Mark each `updated / N/A — reason / blocked`:

- [ ] `docs/AI_ENGINEERING/` domain truth
- [ ] `docs/ADR/`
- [ ] `docs/BASELINE/`
- [ ] `.codex/DECISION_LOG.md`
- [ ] `.codex/TASK_HISTORY.md`
- [ ] `EXTERNAL_ASSET_REGISTRY.md`
- [ ] `TEST_MATRIX.md`
- [ ] `.codex/PROJECT_STATE.md`
- [ ] `.codex/CURRENT_WORK.md`
- [ ] `.codex/CHANGELOG_AI.md`
- [ ] `.codex/CHANGE_EVENT_LOG.md`
- [ ] Security/build/debug/roadmap
- [ ] Entry/rules/Skills

## T8. Handoff and Closure

- Outcome / acceptance status：
- Changed files / behavior：
- Executed validation and highest V level：
- Not executed / residual risk：
- Compatibility / rollback / recovery：
- Documentation synchronized：
- Global memory sync：`PROJECT_STATE / CURRENT_WORK / CHANGELOG_AI / CHANGE_EVENT_LOG / DECISION_LOG`
- Final worktree state：
- Git actions actually performed：
- Build/test actions actually performed：
- Delete/version/sign/package/release actions actually performed：
- Blocked owner or next confirmation gate：

### Definition of Done

- [ ] Acceptance criteria are met.
- [ ] Diff matches authorized scope and preserves user changes.
- [ ] Security/privacy/license/compatibility are reviewed.
- [ ] Required verification is achieved or risk acceptance is explicit.
- [ ] ADR/baseline/docs/memory/registry are synchronized as applicable.
- [ ] Final worktree contains no unexpected files, deletion or sensitive value.
- [ ] No unauthorized Git/build/delete/version/release/external action occurred.
