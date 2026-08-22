# MOD-03b — Sherpa-ONNX Diarization Implementation Spec

**Parent:** [MOD-03-Speaker-Embeddings.md](../MOD-03-Speaker-Embeddings.md)  
**Status:** Approved

---

## Implementation Details

1. **Gradle Setup:**
   ```kotlin
   implementation("com.github.k2-fsa:sherpa-onnx-android:1.10.0")
   ```
2. **Diarization Pipeline:**
   - Load WAV file.
   - Pass audio samples to `OfflineSpeakerDiarization.process(samples)`.
   - Map returned segments (`start`, `end`, `speaker_id`) to `SpeechSegmentEntity` in Room and `.json` sidecar.
