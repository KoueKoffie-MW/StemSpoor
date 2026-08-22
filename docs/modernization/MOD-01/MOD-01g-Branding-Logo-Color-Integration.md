# MOD-01g — Branding, Logo & Color Integration (Level 3)

**Parent Level 1:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Focus:** One thing only — preserving and integrating the recent branding updates (logo, icon, colors) during Foundations work.  
**Status:** Draft (Implementation Plan)  
**Date:** 2026-08-22

**Scope:** Narrow. Do not mix with schema, repos, or migration.

## Recent Changes (to preserve)
- Added logo: `app/src/main/res/drawable/stemspoor_icon.png` (and mipmap-* variants).
- Updated colors to match logo/icon: "StemSpoor Brand Palette: Kalahari Sunset & Veld Earth" in `theme/Color.kt`.
- Theme.kt now defaults dynamicColor = false to use bespoke brand colors (oranges, sands, acacia, dark canvas).

## Implementation Steps
1. Confirm stemspoor_icon is referenced in AndroidManifest.xml (application icon) and any launcher.
2. In any new theme/composable work, import from the brand Color.kt objects (StemSpoorOrange, DarkBackground, etc.).
3. Do not override primary/onPrimary/surface with Material defaults that break the palette.
4. Add a small note in Settings "About" section if desired (optional).
5. Verify on device that icon and colors look correct after Hilt/Room changes (no accidental theme regression).

## Acceptance Criteria
- Icon remains the app launcher icon.
- Brand palette from the recent update is used and not regressed.
- No color hard-codes that conflict with the new palette.

**This is a dedicated narrow plan for the branding changes.** Link results back to Level 1.

See parents for context. Always check latest Compose/Material3 theming at developer.android.com.