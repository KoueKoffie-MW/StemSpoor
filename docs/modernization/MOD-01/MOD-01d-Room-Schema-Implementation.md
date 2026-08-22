# MOD-01d — Room Schema & Entities Implementation (Level 3)

**Parent Level 1:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Parent Level 2:** [MOD-01b-Full-Room-Schema-Repositories-Migration.md](MOD-01b-Full-Room-Schema-Repositories-Migration.md)  
**Focus:** One thing only — implementing the Room schema, entities, and DAOs.  
**Status:** Completed (Verified & Deployed)  
**Date:** 2026-08-22

**Important:** Narrow scope only. This matches the structure of MOD-01a / MOD-01b at higher levels. Separate plans exist/cover other pieces (Hilt in MOD-01c, Repositories in next, Migration in another).

**Recent Code Changes to Respect:**
- The segment merge gap setting (in StorageManager, TranscriptionRunner, SettingsScreen, VadRecordingService) — this plan adds schema support for it via AppConfigEntity. Actual migration logic is in a dedicated plan.
- Logo (stemspoor_icon.png) and brand colors (new palette in Color.kt / Theme.kt) — do not touch theme or drawable resources.

**Tool Version Note:** Check latest Room version and setup at https://developer.android.com/training/data-storage/room (knowledge may be outdated).

---

## Scope (One Thing)

Create and wire the Room database, all entities, DAOs, and basic database class exactly as specified in the parent MOD-01b.

Out of scope (other focused plans):
- Hilt setup
- Repository implementations
- Full migration/bootstrap code
- Moving the segment merge setting into the new schema (schema support only here)
- UseCases or service integration

---

## Schema to Implement (Direct from Parent)

Use the entities defined in MOD-01b:

- RecordingEntity
- SpeechSegmentEntity (include gateDecision, gateProfileId, gateConfidence, gateReason for Voice Gate support)
- SpeakerProfileEntity (include allowedToRecord, consentTimestamp, consentNote, estimatedMinutes, expiresAt, gateConfidenceOverride)
- VaultIndexEntity (minimal for now)
- GateAuditEntity
- AppConfigEntity (key + longValue for segment_merge_gap_ms etc.)

Add appropriate @Entity, @PrimaryKey, @ForeignKey, @Index annotations.

---

## Implementation Steps (Granular)

1. Add Room dependencies (room-runtime, room-ktx, room-compiler via kapt). Verify versions.

2. Create the entity data classes in a `data/db/entity/` package (or core if preferred). Copy exact fields from MOD-01b.

3. Create DAOs in `data/db/dao/`:
   - RecordingDao
   - SpeechSegmentDao (queries for recording, recent discards, gate decisions)
   - SpeakerProfileDao (getAllowedSortedByPriority, upsert)
   - GateAuditDao
   - AppConfigDao (for the new merge gap and future config)

4. Create `AppDatabase` abstract class with @Database annotation listing all entities, version = 1, exportSchema = false initially.
   - Add type converters if needed (e.g. for lists or Instant).

5. Add database callback for initial setup if needed (light — full bootstrap in separate plan).

6. **Verify Build & Basic Usage**
   - Build the project.
   - Write a small test (in-memory database) that inserts a RecordingEntity and SpeechSegmentEntity with gate fields.
   - Confirm no compile/runtime issues.

7. Document any schema adjustments needed due to the segment merge change (e.g. ensuring AppConfigEntity can hold the gap).

8. Update this plan and feed back to MOD-01b / Level 1 (e.g. "Schema implemented, one small adjustment for AppConfigEntity").

---

## Acceptance Criteria (This Scope Only)

- All entities from MOD-01b + AppConfigEntity compile and are registered in AppDatabase.
- DAOs have the key queries listed in MOD-01b (getAllowedSorted, getForRecording, log gate decisions, etc.).
- In-memory database test passes for insert/query of core entities including gate fields.
- No impact on existing logo, colors, or the live segment merge setting code.
- This plan is complete when the above are true and documented here.

---

## Links to Other Pieces

- After this: Use MOD-01e (Repositories) which will depend on these DAOs.
- The migration plan (MOD-01f or similar) will use this schema for bootstrap.
- Voice Gate (MOD-02a) depends on SpeechSegmentEntity.gate* fields and SpeakerProfileEntity.allowedToRecord.

**Re-read parents (MOD-01, MOD-01b) before starting. After finishing, update higher levels with status.**

This is one focused, complete piece of the granular implementation pack.