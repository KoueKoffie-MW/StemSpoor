# MOD-01b — Full Room Schema, Repositories & Migration Strategy (Level 2)

**Parent:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Driven by / Coordinates with:** [MOD-02a](../MOD-02/MOD-02a-VoiceGate-Design-Decisions.md), MOD-03, MOD-05  
**Level:** 2 (Detailed Data Layer Design)  
**Status:** Draft  
**Date:** 2026-08-22

---

## Objective

Define a complete, production-ready Room-based data layer for Phase 1 that:
- Provides fast, relational queries for the Voice Gate (MOD-02), speaker features, vault search, and daily operations.
- Maintains **sidecars as the single source of truth** for audio content and full transcripts.
- Includes clear repository and use-case boundaries.
- Has a robust, testable migration path from the current pure file-based system.

This builds directly on the minimal entities defined in **MOD-01a**.

---

## Design Principles

1. **Sidecars are authoritative** — WAV/Opus + full sidecar JSON (with real timestamps) never get replaced by Room.
2. **Room is a query projection + state store** — metadata, consent flags, gate decisions, indexes, and fast relationships.
3. **Domain models > persistence models** — Clean domain objects in repositories; Room entities are internal.
4. **Incremental & reversible** — Migration must be safe; user can fall back to file-only if needed.
5. **Hilt-first** — All repositories and DAOs provided via Hilt.

---

## Full Room Schema (v1)

### 1. RecordingEntity
```kotlin
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,                    // e.g. "2026-08-22_143022"
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,
    val sidecarPath: String,                       // relative or absolute to sidecar JSON
    val audioPath: String,                         // path to the condensed audio
    val isProcessed: Boolean = false,              // transcription done?
    val createdAt: Long = System.currentTimeMillis()
)
```

**Indices:** `(startTimeWallMs)`, `(isProcessed)`

### 2. SpeechSegmentEntity
```kotlin
@Entity(
    tableName = "speech_segments",
    foreignKeys = [
        ForeignKey(entity = RecordingEntity::class, parentColumns = ["id"], childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("recordingId"), Index("startTimeWallMs"), Index("speakerId"), Index("gateDecision")]
)
data class SpeechSegmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,

    // Speaker / Gate (critical for MOD-02)
    val speakerId: String?,                        // FK to SpeakerProfile or null
    val gateDecision: String,                      // "ALLOWED", "DENIED_UNKNOWN", "DENIED_LOW_CONF", "TEMP_ALLOWED"
    val gateProfileId: String?,
    val gateConfidence: Float?,
    val gateReason: String?,

    // Content
    val language: String?,
    val hasTranscript: Boolean = false,
    val transcriptPath: String?,                   // path to polished .md or sidecar field

    // Vault linkage
    val dailyNoteDate: String?,                    // "2026-08-22"
    val topicLinks: List<String> = emptyList(),    // for quick filtering

    val createdAt: Long = System.currentTimeMillis()
)
```

### 3. SpeakerProfileEntity (projection + consent state)
```kotlin
@Entity(tableName = "speaker_profiles")
data class SpeakerProfileEntity(
    @PrimaryKey val id: String,                    // stable slug: "jan_jvr"
    val name: String,
    val relationship: String,
    val colorHex: String,

    // Voice Gate / Consent (from MOD-02a)
    val allowedToRecord: Boolean = false,
    val consentTimestamp: Long? = null,
    val consentNote: String? = null,
    val gateConfidenceOverride: Float? = null,
    val expiresAt: Long? = null,                   // for temporary guests

    // Stats for priority sorting
    val estimatedMinutes: Double = 0.0,
    val sampleCount: Int = 0,

    val lastUpdated: Long = System.currentTimeMillis()
)
```

**Note:** The full voiceprint centroids and language centroids stay in the JSON sidecar (or a separate `voiceprints/` folder) for now. Room only holds consent + summary stats.

### 4. VaultIndexEntity (lightweight for MOD-05)
```kotlin
@Entity(tableName = "vault_index")
data class VaultIndexEntity(
    @PrimaryKey val id: String,
    val type: String,                              // "segment", "daily", "topic"
    val contentHash: String?,                      // for change detection
    val textSnippet: String,                       // for FTS + embedding
    val embedding: ByteArray?,                     // quantized vector (BLOB)
    val date: String?,
    val speakerIds: List<String> = emptyList(),
    val recordingId: String?,
    val segmentId: String?,
    val lastIndexed: Long
)
```

Add FTS virtual table on `textSnippet` later.

### 5. GateAuditEntity (for statistics & compliance)
```kotlin
@Entity(tableName = "gate_audit")
data class GateAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val decision: String,
    val profileId: String?,
    val confidence: Float?,
    val reason: String?,
    val segmentId: String?
)
```

---

## DAOs

```kotlin
@Dao
interface SpeechSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SpeechSegmentEntity>)

    @Query("SELECT * FROM speech_segments WHERE recordingId = :recordingId ORDER BY startTimeWallMs")
    fun getForRecording(recordingId: String): Flow<List<SpeechSegmentEntity>>

    @Query("SELECT * FROM speech_segments WHERE gateDecision != 'ALLOWED' AND timestamp > :since ORDER BY timestamp DESC")
    suspend fun getRecentDiscards(since: Long): List<SpeechSegmentEntity>
}

@Dao
interface SpeakerProfileDao {
    @Query("SELECT * FROM speaker_profiles WHERE allowedToRecord = 1 ORDER BY estimatedMinutes DESC")
    suspend fun getAllowedSortedByPriority(): List<SpeakerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SpeakerProfileEntity)
}
```

---

## Repository Layer Design

```kotlin
interface SpeakerRepository {
    suspend fun getAllowedProfilesSorted(): List<SpeakerProfile>   // domain model
    suspend fun setAllowedToRecord(id: String, allowed: Boolean, consentNote: String?)
    suspend fun updateStats(id: String, additionalMinutes: Double)
    // ...
}

interface RecordingRepository {
    suspend fun insertRecording(recording: Recording)
    suspend fun getRecentRecordings(limit: Int): List<Recording>
    // ...
}

interface GateAuditRepository {
    suspend fun logDecision(decision: GateDecision)
    suspend fun getFilterStats(since: Long): FilterStats
}
```

**Implementation notes**
- Repositories live in `:domain` or `:data` module (see MOD-06).
- They coordinate Room + sidecar file I/O.
- Domain models (`SpeakerProfile`, `SpeechSegment`) are defined in `:core`.
- During transition, repositories can fall back to reading JSON when Room is empty.

---

## UseCase Examples (Phase 1 starters)

1. `GetAllowedProfilesForGateUseCase` — used by VoiceGate.
2. `RecordSpeechSegmentUseCase` — after VAD + gate decision.
3. `UpdateSpeakerStatsUseCase` — after successful transcription / enrollment.
4. `QueryDiscardedSegmentsUseCase` — for "Filter Health" screen.
5. `BootstrapVaultIndexUseCase` — one-time scan of sidecars.

---

## Migration Strategy (Critical)

### Sidecar Schema Versioning
- Add top-level field in every sidecar JSON:
  ```json
  "schemaVersion": 2,
  "gate": { ... }
  ```
- When writing, always write current version.
- Old files are readable (backward compatible).

### Room Database Versioning
- Start at version 1.
- Use `RoomDatabase.Callback` + `Migration` classes.
- For v1 → v2 (if needed later): add columns for new gate fields.

### Bootstrap / One-time Migration (First Launch after MOD-01)

```kotlin
suspend fun bootstrapFromSidecars() {
    if (roomDbIsEmpty()) {
        val sidecars = scanExistingSidecars()
        val recordings = sidecars.map { parseToRecordingEntity(it) }
        val segments = sidecars.flatMap { parseSegmentsWithGateInfo(it) }
        val profiles = mergeConsentFromProfilesJson()

        recordingDao.insertAll(recordings)
        segmentDao.insertAll(segments)
        speakerDao.upsertAll(profiles)
    }
}
```

- Run in a WorkManager one-time job on app start (after Hilt is ready).
- Show progress in a "Health" debug screen.
- Idempotent — safe to re-run.

### Dual-Write Period (during transition)
- When writing new segments: write to sidecar **first**, then to Room.
- On read for queries: prefer Room, fall back to sidecar scan if missing.
- After 30–60 days or N recordings, drop the fallback path.

### Rollback Plan
- If something goes wrong, user can delete the app's Room database file.
- All data remains in sidecars + vault markdown.

---

## Integration with Existing Code

- `SpeakerProfileManager` will be refactored to delegate consent/allowed state to `SpeakerRepository`.
- `StorageManager` / sidecar writer will receive a `GateDecision` object and write it to JSON + notify repository.
- `VadRecordingService` will call the new `VoiceGate` (injected), which will use `SpeakerRepository.getAllowedProfilesSorted()`.

---

## Testing Strategy

- Unit tests for repositories using Room in-memory database.
- Integration test: simulate sidecar bootstrap → verify Room contents.
- Golden-file tests for migration from sample sidecars (v0 → current schema).

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Inconsistency between sidecar and Room | Always write sidecar first; use content hash for verification |
| Large initial bootstrap on users with months of data | Run in background, show "Indexing..." once |
| Schema evolution mistakes | Strict versioning + comprehensive migration tests |
| Performance of embedding storage (MOD-05) | Store quantized vectors (ByteArray, 4x or 8x smaller) |

---

## Open Questions / Decisions Made Here

- **Decision:** Keep full voiceprint vectors out of Room for v1 (too large, binary). Only summary stats + consent.
- **Question:** Should we add a `VaultIndexEntity` in Phase 1 or defer to MOD-05? → **Decision:** Include a minimal version now (helps statistics and future search).
- **Question:** How to handle profile renames? → Use stable `id` slug + `name` as display. Update references by id.

---

## Implementation Order (Recommended)

1. Define entities + DAOs (builds on MOD-01a).
2. Add basic repositories + Hilt modules.
3. Implement bootstrap migration job.
4. Wire into `SpeakerProfileManager` and sidecar writer.
5. Add UseCases for Voice Gate and basic queries.
6. Add "Health" debug screen for migration status.
7. Write tests.

---

## References & Freshness Notes

- Parent: MOD-01 Foundations
- Voice Gate requirements: MOD-02 + MOD-02a
- Room documentation: https://developer.android.com/training/data-storage/room (verify latest version & best practices — agent knowledge may be outdated)
- Existing sidecar format: see current `SidecarMetadataWriter` and tests in the project

**This document should be reviewed together with MOD-01 and MOD-02a before coding begins.**

---

*Deeper layer — always re-read parents for context.*