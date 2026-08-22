# ADR-0002: VAD Buffer State Machine and Crash-Resilient Audio Writer

## Status
Accepted

## Context
Continuous background audio capture at 16 kHz 16-bit PCM (32 KB/s) requires real-time Silero VAD evaluation (512-sample / 32 ms chunks) with 600 ms pre-roll and post-roll buffers.
Standard RIFF/WAV files store chunk and file lengths in the first 44 bytes. If the application process is terminated mid-recording (e.g. OOM, battery death, force quit), an open, unfinalized WAV header will cause standard audio players to consider the file corrupted.

## Decision
1. **3-State VAD Machine:**
   - **`Listening` (Silence):** Audio frames (512 samples / 32 ms) are fed to Silero ONNX. Frames are pushed into an in-memory circular ring buffer of size 19 frames (~608 ms). No disk writes occur.
   - **`Recording` (Active Speech):** When $P_{\text{speech}} \ge \text{threshold}$ (default 0.5), transition to `Recording`. Immediately flush the 19 pre-roll frames to the active `.wav` file stream, then stream new incoming frames directly to disk.
   - **`PostRoll` (Hangover Buffer):** When $P_{\text{speech}} < \text{threshold}$, keep streaming incoming audio frames to disk for up to 608 ms (19 frames). If speech re-triggers, reset to `Recording`. If silence reaches 608 ms, finalize the speech segment.
2. **Crash-Resilient Header & Sidecar Sync:**
   - On every speech segment finalization (during silence):
     - Seek to position 0 in the `.wav` file (`RandomAccessFile` / `FileChannel`) and update the 44-byte RIFF header with the latest byte count.
     - Atomically write the updated sidecar `.json` metadata to a `.tmp` file and rename it to `<basename>.json`.
   - On app startup, scan existing WAV files in the recording directory: if any file has an invalid 0-byte header length but non-zero file size, automatically repair the 44-byte RIFF header based on actual file length.

## Consequences
- **Pros:**
  - Zero disk I/O during silence saves flash memory write cycles and battery.
  - Pre-roll and post-roll prevent clipping the beginning and end of words.
  - Abrupt kills or power losses leave all prior speech segments intact and playable.
- **Cons:**
  - A crash occurring *mid-sentence* may lose the final ongoing sentence or leave it missing in the header until the auto-repair routine runs.
