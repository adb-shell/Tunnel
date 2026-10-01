# ADR-0009: Use Amyuni as Active Windows Virtual Display

- Status：`accepted`
- Record Type：`backfill`
- Decision Date：current source；Recorded：2026-07-12
- Original Approver / Alternatives：not recorded
- Related Decision Log：D-009
- Implementation State：implemented；Evidence：V0, V3 required

## Decision

`src/virtual_display_manager.rs::IDD_IMPL` selects Amyuni/`usbmmidd_v2` as the active virtual-display backend. Retained RustDesk IDD source/config is dormant and must not be mixed into the active path.

## Consequences

Windows privacy/display depends on external signed driver assets、OS compatibility and topology recovery. Two implementations increase configuration and maintenance debt.

## Compatibility / Rollback / Verification

Switching backend requires a superseding ADR, driver provenance, install/update/uninstall and topology rollback. Verify WIN-03/04/05/08 on supported Windows 10/11; current asset provenance remains blocking.

