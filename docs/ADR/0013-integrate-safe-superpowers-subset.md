# ADR-0013: Integrate a Safe Superpowers Subset

- Status：`accepted`
- Record Type：`contemporaneous`
- Decision Date / Recorded Date：2026-07-12
- Decision Owner：CloudSend project owner
- Security / Release Review：required constraints incorporated
- Related Decision Log：D-013
- Implementation State：implemented as local policy adapter；Evidence：V0

## Context

Upstream Superpowers includes useful brainstorming、planning、systematic debugging、verification and review methods, but its broader workflow also contains worktree、plan execution、TDD/commit and branch-finishing behaviors. Direct installation or blanket enablement would conflict with CloudSend authority rules.

## Decision

Do not install or execute external Superpowers. Create the project-owned `cloudsend-superpowers-safe` Skill as a semantic adapter with an exact allowlist:

1. brainstorming
2. planning
3. debugging
4. verification
5. review

All other capabilities are denied. The adapter is read-only by default and never authorizes file edits、build/test、Git writes、commit、push、release、external/production access or credential use.

## Alternatives

- Install the full upstream package：rejected because it exposes out-of-scope execution/Git/release workflows and update/telemetry supply-chain surface.
- Reimplement five unrestricted execution skills：rejected because “verification/debugging” could be misread as command authority.
- Use a narrow local adapter：accepted；CloudSend rules remain authoritative.

## Consequences

The project gains structured reasoning without importing external code or expanding authority. Future upstream improvements are not automatic；the adapter must be reviewed deliberately.

## Verification / Reversal

V0 verifies the allowlist、forbidden actions、Skill schema/metadata and read-only forward scenarios. Remove or broaden the adapter only through a superseding ADR；commit/push/release remain hard-denied within this profile.

## Reference

- Official upstream reference reviewed 2026-07-12: `https://github.com/obra/superpowers`（reference only, not installed）.

