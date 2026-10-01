# ADR-0005: Use `pkg2230` as Active Android JNI Route

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：current source；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-005
- Implementation State：implemented；Evidence：V0

## Decision

`libs/scrap/src/android/mod.rs` exports `pkg2230`; `pkg2230.rs`/`pkg2230.kt` are the active JNI route. `ffi.rs`/`ffi.kt` remain compatibility/reference paths and must be inspected for drift, not assumed identical.

## Consequences

JNI changes require active symbol、Kotlin declaration、buffer ownership and compatibility review. Removing or reactivating `ffi` requires a new ADR or explicit migration decision.

## Verification / Rollback

V0 checks module exports and symbols. Formal JNI linkage、ownership and device behavior use RST-03/04 and Android cases; generated/artifact changes require C3.

