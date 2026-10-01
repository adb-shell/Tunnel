# ADR-0003: Controller Sessions Use Relay Only

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：2026-06；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-003
- Implementation State：implemented；Evidence：V0, V4 required

## Decision

CloudSend controller sessions set force-relay and reject explicit direct-address entry. Controlled endpoint rendezvous/direct/LAN/NAT compatibility code remains present; the decision is not a global removal of direct connectivity.

## Consequences

Session availability depends strongly on external hbbr capacity and recovery. UI、Rust reconnect and protocol tests must keep the controller/endpoint boundary explicit.

## Compatibility / Rollback / Verification

Old endpoints remain compatible through retained endpoint code. Changing to global relay-only or restoring controller direct requires a new ADR. Verify NET-01/02/08 and relay outage/reconnect in isolated hbbs/hbbr; V0 is not runtime proof.

