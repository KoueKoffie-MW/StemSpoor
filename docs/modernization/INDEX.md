# StemSpoor Modernization — Master Sub-Plan Index

**Master Plan:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md)  
**Created:** 2026-08-22  
**Last Updated:** 2026-08-22 (MOD-08 Completed)

---

## Strategic Roadmap & Status

| ID | Module / Feature Area | Phase | Priority | Status | Entry Document |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MOD-01** | Foundations (Room 2.7.2, KSP, DAOs, Koin DI) | Phase 1 | Critical | **Completed** | [MOD-01-PHASE1-Foundations.md](MOD-01-PHASE1-Foundations.md) |
| **MOD-02** | Voice Gate & Legal Consent (§201 StGB, 10s Circular Buffer) | Phase 2a | Critical | **Completed** | [MOD-02-VoiceGate-Privacy-Legal.md](MOD-02-VoiceGate-Privacy-Legal.md) |
| **MOD-03** | Speaker Diarization & Embeddings (SpeakerDiarizationEngine) | Phase 2b | High | **Completed** | [MOD-03-Speaker-Embeddings.md](MOD-03-Speaker-Embeddings.md) |
| **MOD-04** | Dual Transcription Pipeline (Local SenseVoice + Cloud Gemini Flash) | Phase 2c | High | **Completed** | [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md) |
| **MOD-05** | Local Semantic Search & Vault Intelligence (Vector + FTS5) | Phase 2d | High | **Completed** | [MOD-05-Semantic-Search-Vault.md](MOD-05-Semantic-Search-Vault.md) |
| **MOD-07** | UX & System Integration (Quick Settings Tile, Call Interruption) | Phase 4 | Medium | **Completed** | [MOD-07-UX-Platform.md](MOD-07-UX-Platform.md) |
| **MOD-08** | Storage Integrity Watchdog & Auto-Repair | Phase 5 | Medium | **Completed** | [MOD-08-Ops-CI-Reliability.md](MOD-08-Ops-CI-Reliability.md) |
| **MOD-06** | Modular Architecture (8 Gradle Modules Extraction) | Phase 3 | Medium-High | **Deferred to Phase 3** | [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md) |

---

## Detailed Hierarchical Structure (Level 2 & Level 3 Plans)

### 🎙️ Phase 2b: MOD-03 Speaker Diarization & Embeddings
* [MOD-03-Speaker-Embeddings.md](MOD-03-Speaker-Embeddings.md) — Level 1 Overview (Completed)
  * [MOD-03a-Diarization-Design-Decisions.md](MOD-03/MOD-03a-Diarization-Design-Decisions.md) — Level 2 sherpa-onnx & C++ Clustering Design
  * [MOD-03b-Sherpa-ONNX-Diarization-Implementation.md](MOD-03/MOD-03b-Sherpa-ONNX-Diarization-Implementation.md) — Level 3 Diarization Engine Implementation
  * [MOD-03c-Speaker-Profile-Continuous-Learning.md](MOD-03/MOD-03c-Speaker-Profile-Continuous-Learning.md) — Level 3 Multilingual Profile Adaptation

---

### 📝 Phase 2c: MOD-04 Dual Transcription Pipeline
* [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md) — Level 1 Overview (Completed)
  * [MOD-04a-Dual-ASR-Design-Decisions.md](MOD-04/MOD-04a-Dual-ASR-Design-Decisions.md) — Level 2 Offline vs Cloud User Choice
  * [MOD-04b-SenseVoice-Whisper-Local-Pipeline.md](MOD-04/MOD-04b-SenseVoice-Whisper-Local-Pipeline.md) — Level 3 On-Device SenseVoice-Small ASR
  * [MOD-04c-Gemini-Cloud-Audio-Studio-Pipeline.md](MOD-04/MOD-04c-Gemini-Cloud-Audio-Studio-Pipeline.md) — Level 3 Google AI Studio Flash Integration

---

### 🔍 Phase 2d: MOD-05 Local Semantic Search & Obsidian Intelligence
* [MOD-05-Semantic-Search-Vault.md](MOD-05-Semantic-Search-Vault.md) — Level 1 Overview (Completed)
  * [MOD-05a-Vector-FTS5-Search-Design.md](MOD-05/MOD-05a-Vector-FTS5-Search-Design.md) — Level 2 Hybrid BM25 + Cosine Retrieval
  * [MOD-05b-BGE-Micro-Embeddings-Vault-Indexer.md](MOD-05/MOD-05b-BGE-Micro-Embeddings-Vault-Indexer.md) — Level 3 384-d Embedding & Bidirectional Wikilink Indexer

---

### 📱 Phase 4: MOD-07 UX & System Integration
* [MOD-07-UX-Platform.md](MOD-07-UX-Platform.md) — Level 1 Overview (Completed)
  * [MOD-07a-Quick-Settings-Tile-and-Call-Interruption.md](MOD-07/MOD-07a-Quick-Settings-Tile-and-Call-Interruption.md) — Level 2/3 Quick Settings Tile & Telephony Watchdog

---

### 🛡️ Phase 5: MOD-08 Storage Integrity Watchdog
* [MOD-08-Ops-CI-Reliability.md](MOD-08-Ops-CI-Reliability.md) — Level 1 Overview (Completed)
  * [MOD-08a-Storage-Watchdog-AutoRepair-RoomReindex.md](MOD-08/MOD-08a-Storage-Watchdog-AutoRepair-RoomReindex.md) — Level 2/3 WAV Auto-Repair & Room Sync Watchdog

---

### 🏗️ Phase 3: MOD-06 Modular Architecture
* [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md) — Level 1 Overview
  * [MOD-06a-Module-Graph-and-Dependencies.md](MOD-06/MOD-06a-Module-Graph-and-Dependencies.md) — Level 2 Topology & Dependency Rules
  * [MOD-06b-Core-Modules-Migration.md](MOD-06/MOD-06b-Core-Modules-Migration.md) — Level 3 Core Layer (:core:model, :core:database, :core:audio, :core:ai, :core:vault)
  * [MOD-06c-Feature-Modules-Migration.md](MOD-06/MOD-06c-Feature-Modules-Migration.md) — Level 3 Feature Layer (:feature:record, :feature:recordings, :feature:settings, :app)
