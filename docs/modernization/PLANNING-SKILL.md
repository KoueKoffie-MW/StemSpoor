```markdown
<!-- This file documents the planning approach used for the recme modernization project.
     It is a living skill-reference: see skill-hierarchical-modernization-planning.md for the canonical version. -->

# Hierarchical Modernization Planning (Skill Reference)

**Location:** This document and the executable guidance live in:
- [Index](INDEX.md) (read-first)
- [skill-hierarchical-modernization-planning.md](skill-hierarchical-modernization-planning.md) (canonical, reusable skill)

## Where Skills Are Saved In This Setup

- **Hermes-native skills directory:** `~/AppData/Local/hermes/skills/` — this is Hermes' default skills store (managed by the `skill_manage` tool). Skills there are available across Hermes sessions via `skill_view(name)`.
- **Project-saved skill copy:** `docs/modernization/skill-hierarchical-modernization-planning.md` (this project) — so you can **show the other agent the exact method** without depending on Hermes internals.

**→ For sharing with another agent working in this repo:** point them to `docs/modernization/skill-hierarchical-modernization-planning.md`. It contains the same content as the canonical Hermes skill.

## Core Rules (Enforced In Practice)

1. **Parallel branches always.** Each level splits into focused a/b/c/... docs — never one big plan.
2. **One thing per plan.** Each implementation doc covers a single feature/fix/area.
3. **Big picture survives.** Back-links between every level; INDEX ties it together.
4. **Preserve recent changes.** Logo, segment merge gap setting, brand colors are explicitly referenced where relevant.
5. **Hand-off ready.** Enough context + acceptance criteria + links for another agent to execute standalone.
6. **Verify latest tooling.** Official-docs link + "knowledge may be outdated" note in every plan.

## Recent Code Changes (hard facts for any agent)

1. **Logo added** — `app/src/main/res/drawable/stemspoor_icon.png` (+ mipmap variants). Branding in `docs/branding/`.
2. **Segment merge gap setting** added — `KEY_SEGMENT_MERGE_GAP_MS` / `DEFAULT_SEGMENT_MERGE_GAP_MS` in `VadRecordingService.kt`; used in `StorageManager.mergeAdjacentSegments/remerge*`, `TranscriptionRunner`, and a new UI control in `SettingsScreen`. (Tests: `SegmentMergerTest`.)
3. **Colors updated to match logo/icon** — new palette in `theme/Color.kt` ("Kalahari Sunset & Veld Earth"); `Theme.kt` sets `dynamicColor = false`.

## Planning Stack (as built 2026-08-22)

```
docs/modernization/
├── INDEX.md                              ← read first
├── skill-hierarchical-modernization-planning.md  ← reusable canonical skill
├── MODERNIZATION_PLAN.md (parent)
├── MOD-01-PHASE1-Foundations.md          ← Level 1
│   └── MOD-01/
│       ├── MOD-01a-Room-and-Consent-Model.md        (Level 2)
│       ├── MOD-01b-Full-Room-Schema-Repositories-Migration.md (Level 2)
│       ├── MOD-01-Level3-Implementation-Overview.md
│       ├── MOD-01c-Hilt-DI-Implementation.md        (Level 3 — narrow)
│       ├── MOD-01d-Room-Schema-Implementation.md    (Level 3 — narrow)
│       ├── MOD-01e-Repositories-Implementation.md   (Level 3 — narrow)
│       ├── MOD-01f-Migration-Bootstrap-Implementation.md (Level 3 — narrow; handles segment merge setting migration)
│       └── MOD-01g-Branding-Logo-Color-Integration.md (Level 3 — narrow; preserves logo + colors)

├── MOD-02-VoiceGate-Privacy-Legal.md     ← Level 1
│   └── MOD-02/
│       └── MOD-02a-VoiceGate-Design-Decisions.md     (Level 2)
└── MOD-03..MOD-08 ... (Level 1, pending deeper levels)
```

## Canonical Skill: `hierarchical-modernization-planning`

The full, executable version (with step-by-step workflow, templates, acceptance checks, troubleshooting) is in:
`docs/modernization/skill-hierarchical-modernization-planning.md`

**You can also use `skill_view(name='hierarchical-modernization-planning')` if the skill is registered in Hermes' own skills directory.** (The canonical copy is `docs/modernization/skill-hierarchical-modernization-planning.md` for portability within this repo — this file mirrors it.)
