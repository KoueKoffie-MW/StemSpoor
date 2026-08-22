# MOD-04 — Dual Transcription Pipeline (Local SenseVoice/Whisper + Cloud Gemini)

**Status:** Approved  
**Priority:** High  
**Phase:** Phase 2  
**Related:** [MOD-03-Speaker-Embeddings.md](MOD-03-Speaker-Embeddings.md), [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md)

---

## Objective
Deliver a flexible, dual-mode transcription engine offering **100% offline private transcription** via `sherpa-onnx` SenseVoice-Small / Whisper-tiny INT8 and **high-fidelity cloud transcription & structured summarization** via Google AI Studio (Gemini 2.5/2.0 Flash Audio API).

---

## Sub-Plans
- [MOD-04a-Dual-ASR-Design-Decisions.md](MOD-04/MOD-04a-Dual-ASR-Design-Decisions.md) — Offline vs Cloud user preferences and fallback logic
- [MOD-04b-SenseVoice-Whisper-Local-Pipeline.md](MOD-04/MOD-04b-SenseVoice-Whisper-Local-Pipeline.md) — Offline ASR worker implementation
- [MOD-04c-Gemini-Cloud-Audio-Studio-Pipeline.md](MOD-04/MOD-04c-Gemini-Cloud-Audio-Studio-Pipeline.md) — Google AI Studio Flash integration & structured note generation
