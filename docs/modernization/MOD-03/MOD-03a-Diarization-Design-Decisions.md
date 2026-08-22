# MOD-03a — Speaker Diarization Design Decisions

**Parent:** [MOD-03-Speaker-Embeddings.md](../MOD-03-Speaker-Embeddings.md)  
**Status:** Approved

---

## Key Design Decisions

1. **Framework Selection:**
   - **Decision:** Adopt `sherpa-onnx` Android Kotlin AAR.
   - **Rationale:** Native C++ spectral clustering, zero custom complex math in Kotlin, pre-quantized INT8 models.
2. **Execution Timing:**
   - **Decision:** Background batching in `TranscriptionWorker` / `DiarizationWorker`.
   - **Rationale:** Saves battery, avoids thermal throttling during real-time 24/7 capture.
3. **Clustering Strategy for Unknowns:**
   - **Decision:** Unmatched clusters are tagged as `Speaker 1`, `Speaker 2` with persistent cluster centroids for quick user naming.
