# MOD-07 — UX, Platform & System Integration

**Status:** Draft  
**Priority:** Medium  
**Phase:** Phase 4  
**Parent:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Phase 4)  
**Index:** [INDEX.md](../INDEX.md)

---

## Objective

Deliver a polished, modern, usable interface and deep system integration so that the app feels like a natural part of daily life rather than a technical tool that needs constant attention.

---

## Current State

- Basic Compose UI (Recordings list, Player, Settings, Vault views).
- Dual-timeline player exists but can be improved.
- Limited system integrations (mostly foreground service notification).
- Heavy use of `MANAGE_EXTERNAL_STORAGE`.

---

## Detailed Requirements

### UI/UX
- Modern Material 3 implementation.
- Fast, searchable Recordings and Vault lists with filters (speaker, date, topic).
- High-quality waveform + speaker labels in the player.
- In-app vault browser with clickable links and embedded audio playback.
- Better onboarding for enrollment and the Voice Filter (MOD-02).

### Platform Integration
- App widget showing current status + quick actions.
- Quick Settings tile.
- Better notification actions (pause, tag last segment, open today’s vault).
- Improved scoped storage story (reduce `MANAGE_EXTERNAL_STORAGE` dependency).

### Accessibility & Power Users
- Good keyboard + screen reader support.
- Data management screen (export range, delete by speaker/date, backup status).
- Review queue for low-confidence segments or unknown speakers.

---

## Proposed Design

- Invest heavily in a few key screens: Recordings, Player (DualTimeline), Vault browser, Search, Settings.
- Use Compose best practices (State, ViewModel, paging).
- Create reusable components for waveform, speaker chips, audio citations.
- Widget + tile implemented via standard Android APIs.

---

## Implementation Steps

1. Audit current Compose screens against Material 3.
2. Build improved Recordings list + filtering.
3. Enhance the Player with better waveform, speaker overlays, and bookmarks.
4. Create Vault browser with live Markdown rendering + audio jumps.
5. Implement widget + Quick Settings tile.
6. Add data management and export UI.
7. Reduce broad storage permission usage (app-specific directories + SAF fallback).

---

## Dependencies

- Benefits from MOD-01 (fast queries from Room) and MOD-05 (semantic search).
- Voice Gate (MOD-02) should have corresponding UI (toggles already partially planned).

---

## Risks

- Scope creep in UI work.
- Android widget limitations.
- Storage permission changes on newer Android versions.

---

## Acceptance Criteria

- Major screens feel modern and responsive.
- Widget and Quick Settings tile exist and work.
- User can manage data without relying on file manager.
- Voice Filter and speaker enrollment flows are clear and well-guided.

---

## Tool & Version Notes

- Material 3 / Compose BOM: Always check https://developer.android.com/jetpack/compose for the latest stable BOM and patterns.
- Widget/Quick Settings: Refer to official Android developer documentation.

---

**Always re-read the main plan and relevant AI sub-plans (especially MOD-02) when working on UI that touches the recording gate or speaker features.**