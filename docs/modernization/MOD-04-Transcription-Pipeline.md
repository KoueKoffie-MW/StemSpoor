# MOD-04 — Transcription Pipeline Modernization

**Status:** Draft  
**Priority:** High  
**Phase:** Phase 2  
**Related:** Main Modernization Plan – Phase 2 Transcription

---

## Objective

Make the Whisper + Gemma transcription stack modular, observable, upgradeable, and capable of streaming/partial results.

---

## Current State

- Custom Whisper large-v3-turbo decoder (ONNX)
- Gemma via llama.cpp for post-processing and polishing
- Some Gemini cloud fallback
- Transcription happens after recording (via WorkManager in some paths)
- Tight coupling between engines

---

## Requirements

- Pluggable transcription engines (interface)
- Support for streaming / partial results
- Better language detection and Afrikaans/English/German code-switching handling
- Model management (download, quantization, device capability)
- Clear progress and retry logic
- Ability to swap post-processor (Gemma → newer model)

---

## Proposed Design

- `TranscriptionEngine` interface
- `WhisperEngine` + `GemmaPostProcessor` as implementations
- `TranscriptionRequest` / `TranscriptionResult` data models
- Queue improvements with prioritization and status
- Capability matrix (which model runs well on this device)

---

## Key Implementation Areas

1. Define clean interfaces
2. Refactor current Whisper + Gemma code behind interfaces
3. Add streaming support where the model allows
4. Improve language mix detection
5. Add model download / quantization selection UI
6. Better error handling and partial result saving

---

## Acceptance Criteria

- Can swap transcription backend with minimal changes elsewhere
- Streaming/partial results work for at least one engine
- Clear model selection and fallback behavior
- Improved code-switching quality on real family recordings

---

**Ready for review.**