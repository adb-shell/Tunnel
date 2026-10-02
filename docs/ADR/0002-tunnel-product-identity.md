# ADR-0002: Tunnel Product Identity

- Status：`superseded` by [ADR-0015](0015-tunnel-product-identity-migration.md)
- Record Type：`backfill`
- Decision Date：2026-05；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-002
- Implementation State：implemented；Evidence：V0

## Decision

> 2026-10-02 T004 按用户要求规范化了本文的品牌文字和文件名；以下是历史决定的现行名称表述，不是原日期的逐字快照。新的包名、组织标识和迁移决定以 ADR-0015 为准，历史原文以原 Git 对象为准。

Use `Tunnel`/`tunnel` for runtime、crate/library and native artifacts；Android uses `隧道` and `com.tunnel.app`；deep link is `tunnel`. Preserve inherited RustDesk names where they are compatibility or third-party anchors.

## Consequences

Identity changes must be full-chain across Cargo、Flutter、Android、SO/DLL/EXE、deep link、installer、update and backend contracts. Bulk brand replacement is prohibited.

## Compatibility / Rollback / Verification

Any future rename requires a superseding ADR, migration window, old-client compatibility and artifact rollback. Validate with `02_VERSION_MATRIX.md` and affected `TEST_MATRIX.md` cases; current evidence is V0 only.

