# ADR-0006: Silero VAD ONNX Runtime Integration and Zero-Allocation Engine

## Status
Accepted

## Context
Continuous 24/7 background audio monitoring evaluates 512-sample (32 ms) PCM frames approx. 31 times per second. Naive allocation of tensors, buffers, and float arrays inside the audio loop triggers frequent Android Garbage Collection (GC) pauses, audio buffer drops, and battery drain.

## Decision
1. **Model Bundling & Execution:**
   - Package the quantized Silero VAD ONNX model directly inside `app/src/main/assets/silero_vad.onnx`.
   - Initialize ONNX Runtime using `OrtEnvironment` and `OrtSession` configured for CPU execution (`CPUExecutionProvider`).
2. **Zero-Allocation Pipeline:**
   - Maintain pre-allocated direct NIO buffers (`FloatBuffer` of 512 elements) and persistent state tensors (`state` or `h`/`c`).
   - Convert incoming 16-bit PCM samples to normalized float values ($[-1.0, 1.0]$) directly in place into the direct buffer.
   - Execute inference via `OrtSession.run()` using recycled input tensors.
   - Retain and update recurrent hidden state tensors across contiguous frames, resetting state after extended silence ($>5\text{s}$) to avoid recurrent drift.

## Consequences
- **Pros:**
  - Zero heap allocation during steady-state listening loops eliminates GC pauses.
  - Sub-millisecond CPU inference time ($<0.3\text{ ms}$ per 32 ms frame) keeps overall CPU consumption under 1%.
  - High accuracy voice detection with low battery footprint.
- **Cons:**
  - Requires handling raw native tensor buffers and ONNX lifecycle cleanup explicitly.
