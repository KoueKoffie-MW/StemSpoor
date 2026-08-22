# ADR-0010: Local Multilingual Transcription via Whisper ASR and Gemma LLM Polish

## Status
Accepted

## Context
RecMe continuously records conversations throughout the day, often capturing spontaneous multilingual code-switching between **Afrikaans**, **English**, and **German**. Users require complete, private, on-device transcription without sending audio to third-party cloud APIs, formatted for both interactive app playback and desktop Markdown/Obsidian second-brain workflows.

## Decision
1. **Two-Stage On-Device Audio AI Pipeline:**
   - **Stage 1 (Acoustic ASR):** Quantized OpenAI Whisper (Multilingual INT8/INT4, supporting Small and Large-v3 Turbo tiers) via native runtime (ONNX Runtime / GGML) with dynamic language identification (`af`, `en`, `de`) per segment.
   - **Stage 2 (Grammar & Code-Switching Polish):** Local quantized Gemma 2B INT4 LLM executing on-device to format punctuation, correct domain vocabulary, and seamlessly handle multilingual transitions.
2. **Modular On-Demand Model Management:**
   - Provide an in-app "AI Models" manager in Settings where model weights (~240MB - 1.4GB) are downloaded on-demand over Wi-Fi with progress tracking, preserving APK size.
3. **Execution & Power Policy:**
   - **Automated Charging Batch:** Scheduled via Android `WorkManager` with `RequiresCharging` and `NetworkType.UNMETERED` constraints to preserve battery while mobile.
   - **Manual On-Demand:** Immediate "Transcribe Now" trigger per recording in the UI.
4. **Dual Artifact Output Schema:**
   - **Interactive JSON Sidecar (`.json`):** Extended `SpeechSegmentData` with `detected_language: String`, `raw_text: String`, and `polished_text: String` for tap-to-seek interactive player reading.
   - **Obsidian / Second Brain Markdown (`.md`):** Automatically exports a formatted Markdown document (`YYYYMMDD-HHmm-PartXXX.md`) featuring YAML frontmatter, duration, language mix, and wall-clock timestamps (`**[14:22:05]** [AF] ...`).
5. **Google Drive Cloud Sync:**
   - `GoogleDriveSyncWorker` automatically uploads companion `.md` files to the `RecMe/` Google Drive root folder alongside audio and JSON sidecars.

## Consequences
- **Pros:**
  - 100% private, offline, on-device transcription.
  - High accuracy across Afrikaans, English, and German code-switching.
  - Generates ready-to-use Obsidian notes with wall-clock alignment.
  - Zero battery drain while mobile due to charging-constrained batch execution.
- **Cons:**
  - Requires initial one-time model weight download over Wi-Fi.
