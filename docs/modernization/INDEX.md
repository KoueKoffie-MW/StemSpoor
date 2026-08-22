# StemSpoor Modernization — Sub-Plan Index (Level 1)

**Master Plan:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md)  
**Created:** 2026-08-22  
**Last Updated:** 2026-08-22

**Philosophy:**  
When working on modernization:
1. Start with the overall `MODERNIZATION_PLAN.md`
2. Move to the relevant sub-plan(s) at this level
3. Go deeper into more detailed specs / ADRs / code only when needed
4. Always keep the big picture (main plan + sibling sub-plans) in context

This hierarchy allows very detailed planning while preventing tunnel vision.

---

## Current Level 1 Sub-Plans

| ID     | Title                                      | Phase     | Priority     | Status | File |
|--------|--------------------------------------------|-----------|--------------|--------|------|
| MOD-01 | Foundations (DI, Room, Repos, UseCases)    | Phase 1   | Critical     | Completed | [MOD-01-PHASE1-Foundations.md](MOD-01-PHASE1-Foundations.md) |
| MOD-02 | Voice Gate + Privacy & Legal Compliance    | Phase 2   | **Highest**  | Completed | [MOD-02-VoiceGate-Privacy-Legal.md](MOD-02-VoiceGate-Privacy-Legal.md) |
| MOD-03 | Speaker Diarization & Embeddings           | Phase 2   | High         | In Progress | [MOD-03-Speaker-Embeddings.md](MOD-03-Speaker-Embeddings.md) |
| MOD-04 | Transcription Pipeline Modernization       | Phase 2   | High         | Draft  | [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md) |
| MOD-05 | Local Semantic Search & Vault Intelligence | Phase 2   | Medium-High  | Draft  | [MOD-05-Semantic-Search-Vault.md](MOD-05-Semantic-Search-Vault.md) |
| MOD-06 | Modular Architecture                       | Phase 3   | Medium-High  | Draft  | [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md) |
| MOD-07 | UX, Platform & System Integration          | Phase 4   | Medium       | Draft  | [MOD-07-UX-Platform.md](MOD-07-UX-Platform.md) |
| MOD-08 | Ops, CI, Reliability & Sync                | Phase 5   | Medium       | Draft  | [MOD-08-Ops-CI-Reliability.md](MOD-08-Ops-CI-Reliability.md) |

---

## Hierarchy Rules

- Every sub-plan must link back to the **Parent** (main plan) and to relevant **Siblings**.
- When a sub-plan needs more detail, create a deeper level (e.g. `MOD-02a-VoiceGate-Implementation-Details.md`) and link both ways.
- For tools, libraries, and versions: always include links to official documentation and a note that the reader should verify the latest information.

---

## Recommended Workflow

1. Read main plan for overall vision and priorities.
2. Open the specific sub-plan for the area you're working on.
3. Check sibling sub-plans for interactions (especially MOD-01 ↔ everything, MOD-02 ↔ MOD-03/MOD-05).
4. Only then go into code, existing ADRs, or deeper specs.

---

## Next Level Planning

When a sub-plan becomes large or implementation-specific, we will create a deeper layer inside or next to it (e.g. `MOD-02/details/` or `MOD-02a-...`).

**Current Level 1 is now complete.**


## Deeper Layers (Level 2+)

When more detail is needed, deeper documents are created under the relevant sub-plan.

**Current Deeper Documents:**

- **MOD-02 Voice Gate**
  - [MOD-02a-VoiceGate-Design-Decisions.md](MOD-02/MOD-02a-VoiceGate-Design-Decisions.md)
    - Resolves all open questions from Level 1
    - Detailed technical integration with `VadRecordingService`, `SpeakerEmbeddingEngine`, `SpeakerProfileManager`
    - Concrete decisions on unknown speakers, visitors, temporary allows, thresholds, and statistics
    - Model recommendations + graceful degradation

**Rule:** Deeper docs must link back to their parent Level 1 sub-plan. Level 1 must be updated to reference resolved decisions.


- **MOD-01 Foundations**
  - [MOD-01a-Room-and-Consent-Model.md](MOD-01/MOD-01a-Room-and-Consent-Model.md)
    - Minimal Room entities driven by Voice Gate needs (allowedToRecord, consent, gateDecision on segments)
    - Strategy for sidecar vs Room truth + initial migration


- **MOD-01 Foundations**
  - [MOD-01b-Full-Room-Schema-Repositories-Migration.md](MOD-01/MOD-01b-Full-Room-Schema-Repositories-Migration.md)
    - Complete Room entities (Recording, SpeechSegment with gate fields, SpeakerProfile with consent, VaultIndex, GateAudit)
    - Repository + DAO design
    - Full migration + bootstrap strategy from sidecars (authoritative sidecars, Room as projection)
    - Integration with Voice Gate


- **MOD-01 Foundations** (deepest)
    - Hand-off ready spec for another agent.
    - Accounts for 3 recent code changes (logo, segment merge threshold setting, brand colors).
    - Task-by-task breakdown, exact files, Room + repo details, migration code, tests.


- **MOD-01 Foundations (Level 3 Implementation Plans — one per thing)**
  - [MOD-01c-Hilt-DI-Implementation.md](MOD-01/MOD-01c-Hilt-DI-Implementation.md) — Hilt setup only
  - [MOD-01d-Room-Schema-Implementation.md](MOD-01/MOD-01d-Room-Schema-Implementation.md) — Entities + DAOs only (includes support for segment merge config)
  - [MOD-01e-Repositories-Implementation.md](MOD-01/MOD-01e-Repositories-Implementation.md) — Repository layer only
  - [MOD-01f-Migration-Bootstrap-Implementation.md](MOD-01/MOD-01f-Migration-Bootstrap-Implementation.md) — Migration, bootstrap, dual-write + segment merge setting migration
  - (Future granular ones for UseCases, integration, branding preservation, testing as needed)

**Note on structure:** Mirrors the a/b split at Level 2. Each Level 3 doc is narrowly scoped to one feature/fix/area so it can be handed off independently while linking to parents and noting the 3 recent code changes (logo, segment merge threshold, brand colors) where relevant.


**MOD-01 Foundations — Deeper Levels (structured to mirror higher a/b):**

Level 2:
- MOD-01a (consent/model)
- MOD-01b (schema + repos + migration)

Level 3 (Implementation — narrow plans, one per feature/fix):
- Overview: MOD-01-Level3-Implementation-Overview.md
- Focused (parallel branches):
  - c-Hilt (DI setup)
  - d-Schema impl
  - e-Repos impl
  - f-Migration (incl. recent segment merge change)
  - g-Branding (logo + colors)

Each Level 3 plan is self-contained for one thing, links to parents, notes the 3 recent code changes where relevant, and can be handed off independently.

