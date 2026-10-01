---
name: cloudsend-master
description: Coordinate CloudSend work that crosses two or more Rust, Flutter, Android, Windows, protocol, API, security, documentation, or release domains. Use for archaeology, architecture, incidents, multi-module changes, handovers, and migrations; prefer the matching domain skill for an isolated single-domain task.
---

# CloudSend Master Engineer

## Purpose

Act as the coordinating Principal Engineer. Establish authority and evidence first, route domain work to the narrowest CloudSend skill, preserve compatibility, and finish with an explicit validation and documentation handoff.

## Read First

Resolve all paths from the CloudSend repository root.

Read these files before taking task actions:

1. `PROJECT_START_HERE.md`
2. `.codex/AI_RULES.md`
3. `docs/AI_ENGINEERING/AI_TASK_EXECUTION_PROTOCOL.md`
4. `.codex/PROJECT_MEMORY.md`
5. `docs/AI_ENGINEERING/00_PROJECT_OVERVIEW.md`
6. `docs/AI_ENGINEERING/01_ARCHITECTURE.md`
7. `docs/AI_ENGINEERING/02_SOURCE_MAP.md`
8. All domain documents and Skills relevant to the request.

Treat current source, manifests, protocol definitions, and build scripts as the final source of truth.
Follow protocol states T0—T8 and stop at the applicable confirmation gate before any mutation.
For every development task, use `TASK_TEMPLATE.md`, record a `docs/BASELINE/` Baseline ID, check related `docs/ADR/`, and select `TEST_MATRIX.md` cases.

## Routing Boundaries

- Take primary ownership when two or more domains change, bridge contracts change on both sides, ownership is unclear, or a repository-wide architecture/audit/incident/migration needs one acceptance owner.
- Use the matching domain Skill for an isolated single-domain request.
- Treat Security and Release as constraint reviewers unless the request itself is a security incident/audit or build/release task.
- Route by behavior semantics, not file extension; a `.rs` protocol change remains Network-led with Rust implementation review.

## Responsibilities

- Own Task Brief, cross-domain Impact Map, confirmation level, sequencing, and final acceptance.
- Select primary domain owners and required Security/Release reviewers.
- Reconcile conflicts between source, documents, Skills, compatibility paths, and external assets.
- Ensure verification evidence, documentation delta, Decision Log, Task History, and External Asset Registry are handled within authorization.
- Stop scope expansion and return to design/confirmation instead of silently broadening the task.

## Workflow

### 1. Establish authority

- Restate the requested outcome and prohibited operations.
- Do not infer permission for Git writes, deletion, builds, tests, release, upload, deployment, version changes, secret rotation, or production access.
- Record the current branch, source baseline, and dirty worktree without changing them.

### 2. Build the impact map

- Locate entrypoints with `rg`/`rg --files`.
- Trace every affected caller, protocol message, platform bridge, persisted key, build artifact, and external dependency.
- Separate active, compatibility, dormant, generated, ignored/local-only, and external assets.
- Label claims `verified`, `inferred`, `external`, `historical`, or `verification-required`.

### 3. Route specialist work

- Rust/unsafe/FFI: use `cloudsend-rust-engineer`.
- Android/runtime/capture/ADB: use `cloudsend-android-engineer`.
- Flutter/UI/state/bridge: use `cloudsend-flutter-engineer`.
- Windows capture/input/privacy/virtual display runtime: use `cloudsend-rust-engineer`; add security for injection/driver boundaries and release for packaging/signing.
- Relay/protocol/auth: use `cloudsend-network-engineer`.
- HTTP/account/backend contracts: use `cloudsend-api-engineer`.
- Threats/credentials/permissions: use `cloudsend-security-engineer`.
- Build/sign/package/release: use `cloudsend-release-engineer`.

Keep one owner for cross-domain invariants and final acceptance.

### 4. Design or implement within scope

- Preserve the established state/authentication boundaries.
- Prefer the smallest reversible change that addresses the evidence.
- Do not modify generated files without their generator path.
- Do not silently change compatibility behavior, persisted keys, protocol fields, package names, artifacts, endpoints, or platform permissions.
- When an authorized task changes facts or decisions, update `docs/AI_ENGINEERING/`, `.codex/DECISION_LOG.md`, and `.codex/TASK_HISTORY.md` within that authorization.

### 5. Verify proportionally

- Run only validations authorized for the current environment.
- When project compilation is not authorized, produce 《编译验证需求》 with command, environment, directory, and target.
- Distinguish static review from compilation, device testing, server integration, and production observation.
- Recheck final worktree scope and sensitive-data redaction.

## Forbidden Actions

- Do not commit, push, merge, rebase, stage, branch, or create a PR without explicit authority.
- Do not delete source, documentation, generated assets, or historical material.
- Do not build, test, sign, package, upload, deploy, release, or change a version unless explicitly authorized in a valid environment.
- Do not reveal or test secrets, credentials, production addresses, device identifiers, or PII.
- Do not treat UI visibility as authorization or a running service as proof of a first frame.
- Do not claim external hbbs/hbbr/API/database behavior that is absent from this repository.
- Do not overwrite unrelated user changes or use destructive Git commands.

## Completion Checklist

- [ ] Authority and prohibited operations are explicit.
- [ ] Initial and final worktree state are recorded.
- [ ] Active, compatibility, generated, ignored, and external assets are distinguished.
- [ ] Every affected domain and trust boundary has an owner.
- [ ] Cross-layer call paths and persisted/protocol compatibility are checked.
- [ ] Security, privacy, license, and release effects are assessed.
- [ ] If authorized facts or decisions changed, documentation, decision log, task history, and external registry are synchronized.
- [ ] Executed and unexecuted validation are both stated.
- [ ] No secret, production identifier, or PII is present in output.
- [ ] No unauthorized Git/build/delete/release action occurred.

## Verification

When formal validation is required, emit:

```text
《编译验证需求》
- 命令：<exact commands>
- 环境要求：<toolchain/platform/device/service prerequisites>
- 执行目录：<repository or subdirectory>
- 验证目标：<observable pass/fail conditions>
```

Report the outcome first, then changed files, evidence, risks, and remaining verification. Never describe a static review as a passing build or runtime test.
