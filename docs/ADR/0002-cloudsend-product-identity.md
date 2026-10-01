# ADR-0002: CloudSend Product Identity

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：2026-05；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-002
- Implementation State：implemented；Evidence：V0

## Decision

Use `CloudSend`/`cloudsend` for runtime、crate/library and native artifacts；Android uses `云计划` and `com.cloudsend.app`；deep link is `cloudsend`. Preserve inherited RustDesk names where they are compatibility or third-party anchors.

## Consequences

Identity changes must be full-chain across Cargo、Flutter、Android、SO/DLL/EXE、deep link、installer、update and backend contracts. Bulk brand replacement is prohibited.

## Compatibility / Rollback / Verification

Any future rename requires a superseding ADR, migration window, old-client compatibility and artifact rollback. Validate with `02_VERSION_MATRIX.md` and affected `TEST_MATRIX.md` cases; current evidence is V0 only.

