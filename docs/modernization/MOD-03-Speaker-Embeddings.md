# MOD-03 — Speaker Diarization & Embeddings (sherpa-onnx 3D-Speaker)

**Status:** Approved  
**Priority:** High  
**Phase:** Phase 2  
**Related:** [MOD-02-VoiceGate-Privacy-Legal.md](MOD-02-VoiceGate-Privacy-Legal.md), [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md)

---

## Objective
Implement production-grade on-device speaker diarization and embedding extraction using **sherpa-onnx** (`OfflineSpeakerDiarization` with `3D-Speaker eres2net / CAM++ INT8`). Enables multi-speaker separation, offline timeline tagging, and continuous voiceprint refinement.

---

## Architecture Overview
- **Engine:** `com.github.k2-fsa:sherpa-onnx-android:1.10.0`
- **Models:**
  - Segmentation: `pyannote-segmentation-3.0` ONNX (~5MB)
  - Embedding: `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx` / `cam++` INT8 (~16MB)
- **Execution:** WorkManager background job running upon WAV part finalization.

---

## Sub-Plans
- [MOD-03a-Diarization-Design-Decisions.md](MOD-03/MOD-03a-Diarization-Design-Decisions.md) — Design trade-offs, windowing & clustering specifications
- [MOD-03b-Sherpa-ONNX-Diarization-Implementation.md](MOD-03/MOD-03b-Sherpa-ONNX-Diarization-Implementation.md) — Implementation details of `OfflineSpeakerDiarization`
- [MOD-03c-Speaker-Profile-Continuous-Learning.md](MOD-03/MOD-03c-Speaker-Profile-Continuous-Learning.md) — Multilingual centroids & online profile adaptation
