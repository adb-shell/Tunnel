# ADR-0004: Separate Android Core Service and Screen Share

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：2026-06；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-004
- Implementation State：implemented；Evidence：V0, V3 required

## Decision

Treat Android core/JNI/relay、MediaProjection screen share、frame source and controller waiting as separate states. Projection loss releases share resources but keeps core/JNI/relay alive；hidden boot/reconnect/legacy paths never request new permission.

## Consequences

Recovery logic is more explicit but crosses Flutter、Rust/JNI and Kotlin. A service-running flag cannot be used as frame readiness. Android 14+ one-shot token behavior remains an invariant.

## Rollback / Verification

Re-coupling service and projection requires a superseding ADR and permission/privacy review. Verify AND-01/02/03/04 on Android 10/13/14/15; current record is static only.

