# MOD-01e — Repository Layer Implementation (Level 3)

**Parent Level 1:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Parent Level 2:** [MOD-01b-Full-Room-Schema-Repositories-Migration.md](MOD-01b-Full-Room-Schema-Repositories-Migration.md)  
**Focus:** One thing only — implementing the Repository interfaces and their Room-backed (plus sidecar coordination) implementations.  
**Status:** Completed (Verified & Deployed)  
**Date:** 2026-08-22

**Scope Discipline:** This plan covers *only* the repository layer. Hilt is in MOD-01c, schema/DAOs in MOD-01d, migration in a separate focused plan, integration with segment merge in another.

**Recent Code Changes Note:**
- The segment merge gap setting (StorageManager, Settings, TranscriptionRunner) will be coordinated here via a thin ConfigRepository or direct in StorageManager during transition. Full move is out of scope for this plan.
- Preserve logo and brand colors.

---

## Scope

Deliver the repository layer as specified in Level 2 MOD-01b:
- Domain model separation
- SpeakerRepository, RecordingRepository, GateAuditRepository (and supporting ones)
- Implementations that talk to DAOs + coordinate with sidecars where needed

---

## Steps (Narrow)

1. Define or refine domain models in core/domain/model (SpeakerProfile, Recording, SpeechSegment, GateDecision, FilterStats). Keep them clean.

2. Create the interfaces in core/domain/repository/.

3. Implement the impls in data/repository/impl/ (inject DAOs via constructor).

4. For SpeakerRepositoryImpl: handle allowedToRecord / consent from Room, voiceprints/centroids still from JSON sidecars for now.

5. Basic coordination for RecordingRepository (metadata in Room, content paths point to sidecars).

6. Wire the new repositories into existing managers temporarily (e.g. SpeakerProfileManager delegates consent state).

7. Unit test the repositories using in-memory DB (focus on gate-related queries and priority sorting).

8. Document and propagate (e.g. any decisions on how to expose the segment merge gap via a future ConfigRepository).

---

## Acceptance Criteria

- Interfaces match the signatures in MOD-01b.
- Implementations compile and basic queries work (especially getAllowedProfilesSortedByMinutes and gate audit).
- Existing segment merge logic and branding are untouched.
- Tests pass for the repository methods used by Voice Gate.

**One focused piece.** Complete this, then move to the next narrow plan. Always link back to parents.