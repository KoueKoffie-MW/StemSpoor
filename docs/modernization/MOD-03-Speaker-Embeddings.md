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

### Options to Evaluate
1. **sherpa-onnx speaker models** (recommended starting point)
   - 3dspeaker series (eres2net, etc.)
   - Ready Android support
   - Good verification + diarization APIs

2. **TitaNet Small** (NVIDIA) exported to ONNX
3. **ECAPA-TDNN** variants
4. Keep current 192-d as fallback

### Architecture
- `SpeakerEmbeddingEngine` interface
  - `extractEmbedding(pcm: FloatArray): FloatArray`
- Multiple implementations behind Hilt (or factory)
- Separate concerns:
  - Embedding extraction
  - Scoring / verification
  - Diarization / clustering (later)

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