# ADR-0001: Retain RustDesk-derived Core

- Status：`retrospective`
- Record Type：`backfill`
- Decision Date：2026-04-13 or earlier；Recorded：2026-07-12
- Original Decision Owner / Alternatives：not recorded
- Related Decision Log：D-001
- Implementation State：implemented
- Evidence / Verification：V0；exact upstream fork point unknown

## Context and Decision

Current source is a RustDesk-derived multi-platform remote-desktop core imported as a DaXianDesk snapshot. The repository proves the retained architecture but not the original approval, exact upstream commit or complete alternatives. Maintenance continues on this core rather than assuming a clean rewrite.

## Consequences

CloudSend inherits protocol/platform maturity plus legacy names、compatibility paths、large files、AGPL/third-party obligations and upstream-diff debt. This record remains retrospective until the owner explicitly re-approves it with lineage evidence.

## Compatibility / Verification / Reversal

Do not delete upstream compatibility blindly. A core rewrite or upstream rebase requires a new ADR, migration plan, protocol/platform matrix and rollback. Source lineage is tracked in `docs/BASELINE/01_UPSTREAM_BASELINE.md`.

