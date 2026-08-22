# StemSpoor (RecMe) Modernization Plan

**Date:** 2026-08-22  
**Status:** Draft / Proposed  
**Owner:** Jan (with Eben support)  
**Goal:** Evolve the current solid prototype into a maintainable, evolvable, production-grade personal cognitive second brain while preserving its core strengths.

---

## Executive Summary

StemSpoor is already an impressive local-first ambient voice system with excellent fundamentals:
- Sophisticated Silero VAD pipeline with pre/post-roll and crash resilience
- Sidecar metadata + dual-timeline design (real-world time vs condensed audio)
- Multilingual (Afrikaans/English/German) transcription using Whisper + Gemma
- Obsidian vault export with speaker tags and wiki links
- Speaker voiceprint learning
- Google Drive sync

The current implementation is **early-stage** (initial commit) but architecturally thoughtful, as evidenced by the ADR collection.

**Modernization focus:** Reduce technical debt, improve maintainability and queryability, modernize the AI stack, and add the missing "professional" layers (DI, proper data layer, modularity, observability) **without** compromising the zero-touch, low-power, privacy-first experience.

---

## Current State Assessment

### Strengths
- Strong domain modeling in CONTEXT.md and ADRs
- Excellent VAD state machine and audio buffering (zero-allocation ONNX patterns)
- Thoughtful storage strategy (smart splitting, Opus post-processing, midnight rollover)
- Local AI (Whisper decoder + Gemma via llama.cpp)
- Dual-timeline playback
- Obsidian-native output
- Comprehensive test suite for core components

### Weaknesses / Technical Debt
- **No dependency injection** — heavy manual wiring in services and managers
- **Pure file-based storage** — sidecars + markdown are the only persistence. No fast queries, search is brittle, no relational integrity
- **Monolithic single-module structure** (`:app` only)
- **Custom AI plumbing** is powerful but hard to evolve or test in isolation
- **Limited observability** in background paths (logging, error recovery, progress)
- Speaker diarization is basic (spectral + MFCC)
- **Legal/compliance risk** — ambient recording of non-consented voices (especially under German §201 StGB and GDPR biometric rules) is a significant exposure
- UI is functional but basic Compose
- Google Drive sync is custom REST + WorkManager (brittle)
- No CI/CD
- Heavy permissions (`MANAGE_EXTERNAL_STORAGE`) — future Android risk

### Key Versions (as of inspection)
- AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01
- ONNX Runtime 1.29.0
- Media3 1.11.0
- WorkManager 2.11.2
- llama.cpp-kotlin 0.4.0
- No Room, no Hilt/Koin, no semantic search

---

## Guiding Principles

1. **Local-first, privacy by default** — Everything must work offline. Cloud is optional backup/sync only.
2. **Low power & zero-friction** — The "always listening but only when speaking" magic must not be compromised.
3. **Engineering rigor** — Explicit state machines, repositories, clear boundaries, comprehensive tests.
4. **Multilingual excellence** — Prioritize Afrikaans/English/German code-switching quality.
5. **Obsidian as source of truth** — Vault markdown remains the human-readable durable layer.
6. **Evolvability** — Make it easy to swap AI models, storage backends, or add new export targets.
7. **Family & personal use** — Speaker profiles, action items, and family voice tracking are first-class.

---

## Phased Roadmap

### Phase 1: Foundations (4–8 weeks)
**Goal:** Make the codebase maintainable and queryable.

- Introduce Hilt for dependency injection across the app
- Add Room database for:
  - Recordings metadata
  - Speech segments (with speaker tags, timestamps, language)
  - Speaker profiles & voiceprints
  - Vault index / search cache
- Create Repository layer (`RecordingRepository`, `VaultRepository`, `SpeakerRepository`, `TranscriptionRepository`)
- Refactor `StorageManager`, `SidecarMetadataWriter`, `VaultManager` to use repositories
- Add schema versioning for sidecars + migration strategy
- Introduce UseCase / Interactor layer for core flows (e.g., `ProcessSpeechSegmentUseCase`, `ExportDailyVaultUseCase`)
- Centralize configuration (replace scattered SharedPreferences with DataStore + typed config)
- Improve logging with Timber + structured tags
- Expand unit tests for new layers

**Quick Wins in Phase 1**
- Add Hilt + basic modules
- Room for RecordingItem + SpeechSegmentData
- Extract one UseCase (e.g., vault batch sync)

### Phase 2: AI & Intelligence Modernization (6–10 weeks)
**Goal:** Better quality, faster, more observable, and evolvable AI.

- Speaker Diarization
  - Replace/ augment current spectral approach with modern on-device speaker embedding model (ONNX)
  - Add clustering + profile management UI
- **Voice Gate / Selective Recording Filter (Legal Compliance)**
  - Implement a post-VAD "speaker gate" that **only commits audio to disk** (and later transcription) when the speaker matches an **enrolled + explicitly "allowed"** profile
  - Priority ordering: check profiles sorted by recorded minutes (typically user → wife → children)
  - Fully optional (global toggle, default = off for backward compatibility)
  - Per-profile "Allow recording" flag + explicit consent acknowledgment UI
  - Log filter decisions (kept/discarded + matched profile) in sidecars for audit
  - Technical path: extend `SpeakerEmbeddingEngine` + `HybridSpeakerIdentifier` (already present); evaluate sherpa-onnx speaker models (e.g. 3dspeaker_eres2net or equivalent) for better accuracy + low-power performance on Android
  - Directly mitigates German §201 StGB ("Verletzung der Vertraulichkeit des Wortes") and GDPR Article 9 biometric data risks by **design** — only consented voices are ever recorded
- Transcription Pipeline
  - Modularize `WhisperEngine` + `GemmaPostProcessor` behind clear interfaces
  - Add streaming support and partial results
  - Model management (download, quantization levels, device capability detection)
  - Better language detection and code-switch handling
- Add local semantic search
  - On-device text embeddings (e.g., via ONNX or small sentence-transformer equivalent)
  - Store embeddings in Room
  - "Ask AI" can combine vector search + LLM
- Evaluate Gemma/Whisper upgrades
  - Newer llama.cpp or alternative inference (MediaPipe if suitable)
  - Quantized smaller models for real-time use
- Make transcription queue more robust (retry, prioritization, progress reporting)

### Phase 3: Architecture & Modularity (parallel with Phase 2)
- Split into feature modules:
  - `:core` (models, utils, DI)
  - `:audio` or `:recording` (VAD + capture)
  - `:transcription`
  - `:ai` (speaker, chat, embeddings)
  - `:vault`
  - `:sync`
  - `:ui` (or keep Compose in app for now)
- Adopt consistent MVI / unidirectional data flow patterns in Compose screens
- Strengthen existing state machines (extend the VAD pattern)

### Phase 4: UX, Platform & Integration (ongoing)
- Modern Compose UI
  - Material 3 + latest idioms
  - Searchable Recordings + Vault lists with filters (speaker, date, topic)
  - Improved DualTimelinePlayer (waveform, speaker labels, bookmarks)
  - In-app vault browser with live Markdown + audio citations
- System integrations
  - App widget + Quick Settings tile
  - Better notifications (per-speaker or topic highlights)
- Accessibility & power-user features
- Reduce `MANAGE_EXTERNAL_STORAGE` usage (prefer app-specific + Documents/RecMe with clear migration)
- Add data management screen (export all, delete range, backup status)

### Phase 5: Ops, Reliability & Future-Proofing
- GitHub Actions CI (build debug + release, run unit tests, optional emulator tests)
- Performance & battery benchmarks (instrumented + manual profiles)
- Robust background service patterns (handle Android 14+ / 15+ restrictions)
- Alternative sync options (WebDAV, local folder, or self-hosted later)
- Full data export/import bundle format
- Documentation: keep ADRs up to date for every major change
- Release process & versioning strategy

---

## Detailed Recommendations

### Architecture
- **DI**: Hilt (standard for Android apps of this complexity)
- **Data Layer**: Room + FileSystem (sidecars remain authoritative for audio)
- **Domain Layer**: UseCases + clean models (keep `SpeechSegmentData`, `SidecarData`, etc.)
- **Presentation**: ViewModels + Compose State
- **Navigation**: Keep/expand androidx.navigation3

### Storage
- Sidecar JSON schema → version it explicitly
- Room entities for fast queries and relationships
- Consider a "Vault Index" table for semantic + full-text search

### AI Stack
- Keep ONNX for VAD + Whisper (excellent fit)
- Make inference engines swappable via interfaces
- Add model capability matrix (device RAM, battery, etc.)
- Local embeddings for semantic features

### Background & Lifecycle
- Keep `VadRecordingService` + BootReceiver core
- Use WorkManager for transcription, sync, post-processing (already partially done)
- Add explicit health checks and recovery paths
- Document exact Android version behaviors (reference existing ADR-0004)

### UI
- Invest in a few high-leverage screens: Recordings list, Player, Vault, Ask AI
- Use Compose best practices (remember, derivedStateOf, etc.)
- Add preview composables and screenshot testing where valuable

---

## Technology Choices

| Area                  | Current                  | Proposed                          | Rationale |
|-----------------------|--------------------------|-----------------------------------|---------|
| DI                    | Manual                   | Hilt                              | Standard, reduces boilerplate, scopes services correctly |
| Persistence           | Files + SharedPrefs      | Room + DataStore                  | Fast queries, transactions, migrations |
| AI Inference          | Custom ONNX + llama.cpp  | Keep ONNX + updated llama.cpp     | Excellent local performance; modularize |
| Search                | None / string scan       | Room FTS + on-device embeddings   | Enables real "second brain" queries |
| Modularity            | Single module            | Feature modules                   | Parallel development, faster builds |
| UI                    | Basic Compose            | Material 3 + advanced patterns    | Better UX, future-proof |
| Sync                  | Custom Drive REST        | WorkManager + improved client     | More reliable; consider alternatives |
| CI                    | None                     | GitHub Actions                    | Safety net for changes |

---

## Risks & Mitigations

- **Android background restrictions** — Test on real devices regularly. Keep foreground service minimal. Reference and update ADR-0004.
- **Model size & performance** — Provide multiple quantization levels. Add device capability detection.
- **Scope creep** — Prioritize Phase 1 foundations before big AI changes.
- **Data migration** — Plan sidecar + vault migration carefully; provide one-time upgrade path.
- **Over-engineering** — Keep the "simple for daily use" experience. New features should feel invisible.

---

## Success Criteria

- Can add a new transcription engine or speaker model with minimal changes to the rest of the app
- Vault search returns results in < 1s even with months of data
- Background service survives reboots, Doze, and Android version upgrades reliably
- Unit + integration test coverage for core pipelines > 70%
- Battery impact remains "near zero" (measured)
- Easy to onboard a new developer (or future self) via docs + architecture

---

## Quick Wins (Can Start Immediately)

1. Add Timber for logging.
2. Extract `StorageManager` responsibilities into Repository + UseCase.
3. Add a simple Room database with one entity (e.g., `Recording`).
4. Create a basic Hilt module for the service.
5. Add a "Health" debug screen showing service state, last segments, storage usage.
6. Write ADR for the new data layer.

---

## Immediate Recommended Next Steps

1. Review this plan and adjust priorities.
2. Set up a branch: `modernization/phase-1-foundations`.
3. Implement Hilt + first Room entity + basic Repository.
4. Update relevant ADRs.
5. Run on-device validation after each significant change.

---

## Appendix

### Useful Existing Documents
- `README.md`
- `CONTEXT.md`
- `App Requirements Document.md`
- `docs/adr/` (especially 0001–0011)

### Potential Model Upgrades to Investigate
- Newer Silero VAD if available
- Quantized Whisper variants
- Gemma 2 or 3 via updated llama.cpp or other GGUF runners
- Dedicated speaker embedding models (e.g., Resemblyzer-style or ONNX equivalents)

---

**This plan is a living document.** Update it as work progresses and new decisions are made (following the existing ADR pattern).

---

*Generated with Eben as strategic companion.*
## Privacy & Legal Compliance (added 2026-08-22)

### Voice Gate / Selective Recording Filter

**Motivation**
- German § 201 StGB ("Verletzung der Vertraulichkeit des Wortes") criminalizes recording the non-publicly spoken word of *another* person without authorization (up to 3 years).
- Recording only your own voice is generally not covered by this section.
- Any other person's voice requires consent.
- Under GDPR, voiceprints/embeddings for identification are biometric data (Article 9 special category) → requires explicit consent.

**Design**
- Add an **optional post-VAD Voice Gate** (second filter after Silero VAD).
- Only commit audio to disk (pre-roll + continued recording) if the speaker matches an enrolled profile that is marked "allowed to record".
- Priority: sort allowed profiles by recorded minutes (user first, then family members with most data).
- Global toggle: "Voice Filter – Record only enrolled & allowed voices" (default = off).
- Per-profile toggle + consent acknowledgment.
- Log filter decisions (kept/discarded, matched profile, confidence) in sidecars.

**Technical Suggestions**
- Reuse/extend existing components:
  - `SpeakerEmbeddingEngine` (192-d ONNX or filterbank fallback)
  - `SpeakerProfileManager` (centroids, language-aware, continuous learning)
  - `HybridSpeakerIdentifier`
- Run a short embedding window (1–3 s) only on VAD-positive speech (not continuous listening).
- Make the gate a clean class (`VoiceGate` or `SpeakerGate`) that the `VadRecordingService` consults before committing buffers.
- For production accuracy, evaluate **sherpa-onnx** speaker models (e.g. 3dspeaker_eres2net_base or similar). Sherpa-onnx provides ready Android support, speaker verification/identification, and pairs naturally with the existing Silero VAD.
- Add Room entity (or extend profiles) to store:
  - `allowedToRecord: Boolean`
  - `consentTimestamp`
  - `consentNote` (free text for documentation)
- Record filter outcome in `SidecarData` / segment metadata.
- Add a small "Filter Health" debug view showing recent gate decisions.

**Implementation order recommendation**
1. Add UI toggles + per-profile "allowed" flag (quick win).
2. Wire the gate in the recording service (using current embedding engine).
3. Add logging/audit.
4. Evaluate + integrate a better speaker embedding model (sherpa-onnx recommended).
5. Add Room-backed consent tracking in Phase 1 foundations.

**Risks / Caveats**
- False negatives (dropping own speech) → good enrollment samples + adaptive threshold needed.
- Overlapping speech / noise will not be perfect.
- Even with the filter, the microphone is open — the app should never claim "100% only your voice".
- Consent must still be obtained and documented for every enrolled speaker.

This feature is one of the highest-leverage additions for both compliance and vault quality.


**Technical Recommendations (Voice Gate details)**
- Architecture: Insert `VoiceGate` after VAD segment detection but before buffer commit in `VadRecordingService`.
- Run embedding only on short VAD-positive chunks (power efficient).
- Use existing 192-d embedding + cosine; upgrade model via sherpa-onnx when ready.
- Add `SpeakerConsent` data in Room (Phase 1) with `allowed`, `consentDate`, `note`.
- Expose filter statistics in a new "Compliance & Filter" settings section.
- Update sidecar schema to include `gateDecision`, `gateProfile`, `gateConfidence`.
- Tests: Add unit tests for the gate logic (mock profiles + embeddings).


**Quick Wins (added for Voice Gate / Compliance)**
7. Add global + per-profile "Voice Filter / Allowed to record" toggles in SettingsScreen (with consent reminder text).
8. Wire a minimal `VoiceGate` stub that logs decisions but still records everything (behind the global toggle).
9. Add `gateDecision` fields to sidecar writing for future audit.
10. Document the legal rationale in a new ADR or in this plan.

**Model Recommendations (Speaker)**
- Primary candidate: sherpa-onnx speaker models (e.g. 3dspeaker_eres2net or similar from k2-fsa/sherpa-onnx releases).
- They provide ready Android builds, speaker verification, and integrate cleanly with the existing ONNX + Silero VAD setup.
- Alternatives: TitaNet Small (ONNX export), ECAPA-TDNN variants.


---

## Sub-Plans (Specification-Driven)

Detailed sub-plans have been created for focused work and hand-off:

- [docs/modernization/INDEX.md](docs/modernization/INDEX.md)
- [MOD-01 — Phase 1 Foundations](docs/modernization/MOD-01-PHASE1-Foundations.md)
- [MOD-02 — Voice Gate + Privacy & Legal Compliance](docs/modernization/MOD-02-VoiceGate-Privacy-Legal.md) ← **Highest priority**
- [MOD-03 — Speaker Diarization & Embeddings](docs/modernization/MOD-03-Speaker-Embeddings.md)

More sub-plans (Transcription, Semantic Search, Modularity, UX, Ops) will be added.

