# MOD-03 — Speaker Diarization & Embeddings

**Status:** Draft  
**Priority:** High  
**Phase:** Phase 2  
**Related:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Phase 2 Speaker Diarization) + MOD-02 (Voice Gate)

---

## Objective

Improve speaker identification quality and make the speaker system production-grade, swappable, and suitable for use as both:
- A **hard gate** before recording (see MOD-02)
- Post-recording diarization and tagging

---

## Current State

- Custom 192-dimensional embedding (ONNX neural + acoustic filterbank fallback)
- Centroid + language-aware adaptation in `SpeakerProfileManager`
- `HybridSpeakerIdentifier` combining acoustics + vault context
- Basic enrollment flow in Settings

**Limitations**
- Current embedding quality is basic (especially in noisy or multi-speaker conditions)
- No proper clustering for unknown speakers
- Tight coupling to the current embedding format
- Limited evaluation data / benchmarks

---

## Requirements

- Replace or significantly improve the current embedding model
- Support both verification (for Voice Gate) and identification/diarization
- Make the embedding engine pluggable
- Better handling of short utterances and code-switching
- Continuous learning remains high quality
- Provide a path for clustering unknown speakers

---

## Proposed Design

### Selected Production Option: **sherpa-onnx + 3D-Speaker (eres2net INT8)**
* **Model:** `3dspeaker-eres2net` (or `cam++`) quantized INT8 (~16MB–22MB footprint).
* **Latency:** ~5ms–12ms per 2-second PCM slice on modern Android ARM64 hardware.
* **Bindings:** Official `sherpa-onnx` Kotlin/Java bindings running natively or via ONNX Runtime Mobile.
* **Output:** 192-d or 512-d normalized speaker vector with cosine distance scoring.
* **Acoustic Fallback:** Retain lightweight internal MFCC centroid extractor as offline safety fallback if neural model fails to load.

### Architecture
- `SpeakerEmbeddingEngine` interface:
  ```kotlin
  interface SpeakerEmbeddingEngine {
      suspend fun extractEmbedding(pcm: FloatArray): FloatArray
      fun computeSimilarity(embeddingA: FloatArray, embeddingB: FloatArray): Float
  }
  ```
- Modularized in `:core:ai` and injected via Hilt.
- Separate concerns:
  1. **Voice Gate Verification**: Rapid online scoring against top-priority allowed profiles (Jan, family).
  2. **Post-Recording Diarization**: Multi-speaker segment tagging and clustering.
  3. **Continuous Centroid Update**: Adaptive learning for verified high-confidence segments.

---

## Implementation Steps

1. Evaluate sherpa-onnx speaker models on real data
2. Define `SpeakerEmbeddingEngine` interface + keep current impl
3. Add at least one new implementation (sherpa-onnx)
4. Update `SpeakerProfileManager` and `HybridSpeakerIdentifier` to use the interface
5. Add model capability / size selection
6. Add clustering support for unknown speakers (future)
7. Benchmark accuracy + latency + power

---

## Acceptance Criteria

- At least two embedding backends can be swapped
- Voice Gate (MOD-02) and post-recording tagging both work with the new model
- Measurable improvement in speaker identification accuracy
- Model is quantized and runs efficiently on target devices

---

## Risks

- Model licensing / size
- Accuracy regression on Afrikaans or child voices
- Integration complexity with existing continuous learning logic

---

**Ready for specification-driven review.**