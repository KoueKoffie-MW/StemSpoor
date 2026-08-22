# MOD-06b — Core Modules Migration Spec

**Parent:** [MOD-06-Modular-Architecture.md](../MOD-06-Modular-Architecture.md)  
**Status:** Approved

---

## Step-by-Step Core Migration Plan

1. **Create `:core:model`**:
   - Move `com.example.recme.domain.model.*` (RecordingItem, SpeechSegment, SpeakerProfile, GateDecision, FilterStats).
   - Move `com.example.recme.storage.SpeechSegmentData`, `SidecarData`.
2. **Create `:core:database`**:
   - Move `com.example.recme.data.db.*` (AppDatabase, DAOs, Entities, Converters).
   - Move `com.example.recme.data.repository.*` implementations.
3. **Create `:core:audio`**:
   - Move `com.example.recme.audio.*` (AudioCaptureEngine, VadStateMachine, SileroVadDetector, CircularAudioBuffer).
   - Move `com.example.recme.storage.WavAudioWriter`, `OpusAudioCompressor`.
4. **Create `:core:ai`**:
   - Move `com.example.recme.ai.voicegate.*`, `com.example.recme.ai.speaker.*`.
   - Move `com.example.recme.ai.gemini.*`, `com.example.recme.ai.models.*`.
5. **Create `:core:vault`**:
   - Move `com.example.recme.vault.*` (VaultManager, WikilinkIndex, VaultSyncWorker).
