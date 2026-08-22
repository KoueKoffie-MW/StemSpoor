# MOD-01f — Migration & Bootstrap Implementation (Level 3)

**Parent Level 1:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Parent Level 2:** [MOD-01b-Full-Room-Schema-Repositories-Migration.md](MOD-01b-Full-Room-Schema-Repositories-Migration.md)  
**Focus:** One thing only — the migration strategy, sidecar versioning, one-time bootstrap, and dual-write.  
**Status:** Completed (2026-08-22)  
**Date:** 2026-08-22

**Critical Recent Change Integration:**
- The new **segment merge gap setting** (KEY_SEGMENT_MERGE_GAP_MS / DEFAULT 1000L in VadRecordingService, used in StorageManager.mergeAdjacentSegments, TranscriptionRunner, SettingsScreen) must be migrated.
- On bootstrap, read the value from SharedPreferences and seed it into the new AppConfigEntity (or DataStore later).
- Preserve the current behavior of mergeAdjacentSegments and remerge* methods.
- Logo and brand colors: untouched in this scope.

**Scope:** Only migration/bootstrap. Schema = MOD-01d, Repos = MOD-01e, Hilt = MOD-01c.

---

## Steps

1. Add `schemaVersion` to SidecarData (bump as needed, make backward compatible).

2. Implement sidecar reader helpers that can parse old and new versions.

3. Create the bootstrap worker / one-time initializer that:
   - Scans existing recordings via current StorageManager.
   - Populates RecordingEntity, SpeechSegmentEntity (carry over any existing speaker/gate info if present).
   - Seeds SpeakerProfileEntity consent/allowed flags (initially false or from any existing profile data).
   - Seeds AppConfigEntity for "segment_merge_gap_ms" from the current SharedPrefs value.
   - Uses content hashes or file timestamps for idempotency.

4. Add dual-write hooks in StorageManager (after writing sidecar, notify repository to upsert metadata).

5. Implement safe rollback (clear Room DB leaves sidecars intact).

6. Add progress reporting for the bootstrap (for the Health screen).

7. Test with sample data: existing recordings appear correctly in Room queries, segment merge value is preserved, new recordings dual-write.

8. Update this plan and propagate decisions (e.g. exact AppConfig key naming) to higher levels.

---

## Acceptance Criteria

- Bootstrap successfully populates Room from existing sidecars without data loss.
- The segment merge gap setting value is correctly seeded into the new storage on first run.
- Dual-write works for new recordings.
- Rollback is safe.
- No impact on logo, colors, or live merging behavior.

**This is the focused migration piece of the pack.** Complete it independently, then link results upward.

---

**Rule for the pack:** Each of these (c, d, e, f, ...) is a standalone, narrow implementation plan. Agents can pick one at a time while always referencing parents for context. The 3 code changes are called out in the relevant plans. 

Update parents when this is done.