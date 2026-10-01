# ADR-0006: Android First Frame Uses Normal Refresh Only

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：2026-06；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-006
- Implementation State：implemented；Evidence：V0, V3 required

## Decision

Controller waiting/reconnect may request refresh of an already-authorized normal video path. It must not automatically enable ignore/screenshot、rebind VirtualDisplay or request MediaProjection. Any real RGBA/Texture frame clears waiting.

## Consequences

The user may see waiting instead of an automatic alternate source, but permission and frame-source semantics stay truthful and recoverable.

## Compatibility / Verification

Explicit side-button/user actions may still select share/fallback behavior. Verify AND-02/04 and FLT-03/04 with static and dynamic frames, network loss and reconnect. Reintroducing automatic fallback requires a new ADR.

