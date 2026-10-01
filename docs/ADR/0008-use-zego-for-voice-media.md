# ADR-0008: Use ZEGO for Voice Media

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：2026-05/06；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-008
- Implementation State：implemented with security gaps；Evidence：V0, V4 required

## Decision

Use RustDesk peer messages only for voice invitation/control state；carry 1v1 voice media through Flutter ZEGO SDK and an external token/RTC boundary. Do not route current voice media through the original RustDesk `audio_service`.

## Consequences

Media is decoupled from the remote-desktop transport but adds external token、tenant、privacy、consent、dependency and availability risks. Invitation authorization、token authorization and microphone consent remain separate.

## Compatibility / Rollback / Verification

Fallback to the old audio path is not implicit. Verify AND-07、NET-07、API-08 and cross-domain E2E with explicit accept/reject、permission denial、busy cleanup and token TTL. Credential/token blockers remain open.

