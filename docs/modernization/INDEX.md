# StemSpoor Modernization — Sub-Plan Index

**Master Plan:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md)  
**Created:** 2026-08-22  
**Purpose:** Break the overall modernization effort into focused, hand-offable, reviewable sub-plans. These can be used for:
- Specification-driven reviews before implementation
- Hand-off to other agents / developers
- Parallel workstreams
- Clear scoping and acceptance criteria

---

## Sub-Plans

| ID | Title | Phase | Priority | Status | File |
|----|-------|-------|----------|--------|------|
| MOD-01 | Foundations (DI, Room, Repositories, Use Cases) | Phase 1 | Critical | Draft | [MOD-01-PHASE1-Foundations.md](MOD-01-PHASE1-Foundations.md) |
| MOD-02 | Voice Gate + Privacy & Legal Compliance | Phase 2 + Cross-cutting | **Highest** | Draft | [MOD-02-VoiceGate-Privacy-Legal.md](MOD-02-VoiceGate-Privacy-Legal.md) |
| MOD-03 | Speaker Diarization & Embeddings | Phase 2 | High | Draft | [MOD-03-Speaker-Embeddings.md](MOD-03-Speaker-Embeddings.md) |
| MOD-04 | Transcription Pipeline Modernization | Phase 2 | High | Draft | [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md) |
| MOD-05 | Local Semantic Search & Vault Intelligence | Phase 2 | Medium | Planned | - |
| MOD-06 | Modular Architecture | Phase 3 | Medium-High | Planned | - |
| MOD-07 | UX, Platform & System Integration | Phase 4 | Medium | Planned | - |
| MOD-08 | Ops, CI, Reliability & Sync | Phase 5 | Medium | Planned | - |

---

## How to Use These Sub-Plans

1. **Specification-Driven Review**
   - Review each sub-plan in isolation.
   - Check Requirements, Design, Risks, and Acceptance Criteria.
   - Resolve Open Questions before starting code.

2. **Hand-off**
   - Give an agent (or developer) one complete sub-plan file + the main MODERNIZATION_PLAN.md.
   - They should be able to work mostly independently.

3. **Implementation Order Recommendation**
   - MOD-01 (Foundations) first — everything else builds on it.
   - MOD-02 (Voice Gate) early — high legal/compliance value.
   - Then the rest of Phase 2 AI work.

4. **Linking**
   - The main plan references these sub-plans.
   - Each sub-plan references the main plan and relevant ADRs.

---

## Next Actions

- [ ] Create remaining sub-plans (MOD-05 through MOD-08)
- [ ] Add cross-references from main MODERNIZATION_PLAN.md
- [ ] Start specification reviews (one by one)
- [ ] Decide implementation sequence

---

*Maintained as part of the StemSpoor modernization effort.*
| MOD-04 | Transcription Pipeline Modernization | Phase 2 | High | Draft | [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md) |

