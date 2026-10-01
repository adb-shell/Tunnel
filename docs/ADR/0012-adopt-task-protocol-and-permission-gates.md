# ADR-0012: Adopt Task Protocol and Permission Gates

- Status：`accepted`
- Record Type：`backfill`
- Decision Date / Recorded Date：2026-07-12
- Decision Owner：CloudSend project owner
- Related Decision Log：D-012
- Implementation State：implemented；Evidence：V0

## Decision

All AI-assisted work enters through `PROJECT_START_HERE.md`, follows T0—T8, uses C0—C3 authorization and V0—V5 evidence, and registers external assets. Git、build/test、delete、version、sign/package、upload/deploy/release and production/credential actions remain independently authorized C3 categories.

## Consequences

Tasks become auditable and less autonomous at high-risk boundaries. Planning or successful validation never silently expands authority.

## Reversal / Verification

Downstream Skills may only narrow these rules. A change to permission semantics requires a superseding ADR and security/release review. V0 checks templates、Skills and navigation；formal execution remains task-specific.

