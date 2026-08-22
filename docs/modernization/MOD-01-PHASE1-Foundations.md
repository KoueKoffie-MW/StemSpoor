# MOD-01 — Phase 1: Foundations

**Status:** Draft  
**Priority:** Critical (Foundation for everything else)  
**Phase:** 1  
**Owner:** Jan / Eben  
**Related:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md#phase-1-foundations-4–8-weeks)  
**Target Duration:** 4–8 weeks

---

## Objective

Establish a clean, maintainable, testable architectural foundation so that future AI, privacy, and UX improvements can be built without fighting technical debt.

**Primary Goals**
- Introduce proper Dependency Injection (Hilt)
- Add a real data persistence layer (Room) for fast queries and relationships
- Create a Repository + UseCase layer
- Replace scattered SharedPreferences with typed DataStore configuration
- Improve observability and testability
- Reduce manual wiring and god-manager classes

---

## Current State (as of 2026-08-22)

- Single `:app` module
- No DI framework — heavy manual construction of managers and engines
- Pure file-based storage (sidecars + markdown) + SharedPreferences
- Large manager classes:
  - `StorageManager`
  - `VaultManager`
  - `SpeakerProfileManager`
  - `RecordingSessionManager`
  - etc.
- No fast metadata queries
- Sidecar JSON is the source of truth (good), but scanning files for everything is slow and brittle
- Some tests exist for core audio logic

**Key Existing Files**
- `app/src/main/java/.../storage/StorageManager.kt`
- `app/src/main/java/.../vault/VaultManager.kt`
- `app/src/main/java/.../ai/speaker/SpeakerProfileManager.kt`
- `service/VadRecordingService.kt`
- `RecMeApplication.kt`

---

## Detailed Requirements

### Must Have
- Hilt set up for the entire app (including `VadRecordingService`)
- Room database with at least these entities:
  - `Recording`
  - `SpeechSegment`
  - `SpeakerProfile` (or link to existing voiceprints)
  - Basic vault index entities (optional for Phase 1)
- Repository interfaces + implementations:
  - `RecordingRepository`
  - `SpeakerRepository`
  - `VaultRepository` (or at least metadata)
- UseCase layer for core flows (start with 2–3 high-value ones)
- DataStore for all configuration (replace SharedPreferences)
- Schema versioning + migration strategy for sidecars + Room

### Should Have
- Clear separation: Domain models vs persistence models
- Basic logging improvements (Timber)
- Unit tests for new Repository and UseCase layers

### Nice to Have
- Initial Dagger/Hilt modules for AI engines
- Simple "Health" debug screen showing storage + DB state

---

## Proposed Design

### Layering
```
UI / Compose
    ↓
ViewModels
    ↓
UseCases (Domain)
    ↓
Repositories (Interface)
    ↓
Room DAOs + FileSystem (Sidecars)
```

### Key Components
- **Hilt Modules**: `AppModule`, `DatabaseModule`, `StorageModule`, `AiModule`
- **Room Entities** (start minimal):
  - `RecordingEntity`
  - `SpeechSegmentEntity` (with speaker, language, timestamps, file refs)
- **Repositories** delegate to Room for metadata + still use sidecars for audio truth
- Sidecars remain authoritative for audio data (no big rewrite)

---

## Implementation Steps (Suggested Order)

1. **Setup Hilt**
   - Add Hilt dependencies
   - Create `RecMeApplication` with `@HiltAndroidApp`
   - Annotate `MainActivity` and `VadRecordingService`

2. **Add Room**
   - Define entities + DAOs for Recording + SpeechSegment
   - Create `AppDatabase` with version + migration
   - Add basic indexes for common queries (by date, speaker)

3. **Introduce Repositories**
   - Create `RecordingRepository` interface + impl
   - Move relevant logic out of `StorageManager`

4. **Extract Use Cases**
   - `ProcessNewSpeechSegmentUseCase`
   - `ExportDailyVaultUseCase`
   - `QueryRecordingsUseCase`

5. **Migrate Configuration**
   - Introduce DataStore
   - Move speaker thresholds, toggles, etc.

6. **Refactor Existing Managers** (incrementally)
   - Make managers take dependencies via constructor (Hilt)

7. **Testing & Validation**
   - Unit tests for repositories and use cases
   - On-device smoke test of the full recording → storage flow

---

## Dependencies

- MOD-01 is a prerequisite for most later phases.
- The Voice Gate (MOD-02) will benefit heavily from Room + proper repositories.

---

## Risks & Open Questions

| Risk / Question | Mitigation / Decision Needed |
|-----------------|------------------------------|
| Big refactor risk while the always-on service is live | Incremental migration + feature flags |
| Sidecar vs Room consistency | Sidecars remain source of truth; Room is query cache + fast metadata |
| Migration of existing user data | One-time upgrade path + backup before migration |
| How much to normalize speaker profiles into Room vs keep in JSON | Start with metadata in Room, voiceprint files stay on disk |

---

## Acceptance Criteria

- App builds and runs with Hilt injection
- Room database is created and basic queries work (recordings by date/speaker)
- At least two core flows go through UseCases
- SharedPreferences usage for config is removed or isolated
- Unit test coverage for new layers > 60%
- On-device recording + vault export still works end-to-end

---

## References

- Main plan: Phase 1 section
- Existing ADRs in `docs/adr/`
- Current managers in `storage/`, `vault/`, `ai/speaker/`

---

**This sub-plan is intended for specification-driven review before implementation.**