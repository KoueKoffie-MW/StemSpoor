# Domain Context: VAD Smart Recorder (RecMe)

## Overview
RecMe is an Android background audio recording engine and companion player. It utilizes on-device Voice Activity Detection (VAD) via ONNX Runtime (Silero VAD) to continuously monitor the microphone, capturing only human speech while stripping silence.

---

## Core Ubiquitous Language & Glossary

| Term | Definition |
| :--- | :--- |
| **Audio Frame** | A raw, fixed-size slice of PCM audio (512 samples / 32 ms at 16 kHz) fed directly into the VAD inference engine. |
| **Pre-Roll Buffer** | A circular in-memory audio ring buffer (600 ms / ~19 frames) captured *before* speech confidence crosses the activation threshold, preventing word-onset clipping. |
| **Post-Roll Buffer** | Audio retained for a fixed duration (600 ms / ~19 frames) *after* speech confidence drops below the deactivation threshold before closing a segment. |
| **Speech Segment** | A continuous block of recorded audio in the condensed WAV file consisting of: `[Pre-Roll] + [Active Speech] + [Post-Roll]`. |
| **Condensed WAV** | A standard, uncompressed RIFF/WAV file (16-bit PCM, Mono, 16 kHz) containing concatenated speech segments without silence. Universally playable in third-party media players. |
| **Sidecar JSON** | A companion metadata file (`.json`) with identical base filename to the condensed WAV, recording real-world wall-clock timestamps (`epoch_ms`) mapped to internal audio timeline offsets (`audio_ms`). |
| **Smart Splitting** | Splitting recording files when reaching size target (~700 MB / ~5-6h of speech), deferred until the end of the current speech segment (during silence) to prevent mid-utterance cuts. |
| **Midnight Rollover** | Automatic session boundary transition at `00:00:00` during the next silent pause to cleanly partition recordings into separate calendar days. |
| **Opus Post-Processing** | Background asynchronous re-encoding of finalized WAV files into 32 kbps Opus audio (`.opus`), shrinking disk usage by 90% while updating sidecar JSON pointers. |
| **Google Drive Sync** | Background WorkManager service that uploads paired audio and JSON sidecar files to a dedicated `RecMe/` Google Drive folder under the restricted `drive.file` scope. |
| **Multilingual Transcription** | Dual-tier on-device AI pipeline using Whisper ASR (acoustic tokens + language ID for Afrikaans, English, German) and Gemma 2B LLM (grammar, punctuation, code-switching polish). |
| **Obsidian Vault Export** | Formatted `.md` Markdown companion file generated alongside recordings with YAML frontmatter and wall-clock timestamps (`**[HH:MM:SS]** [LANG] text`). |
| **In-App Vault** | Native Jetpack Compose second-brain interface featuring daily journals, bidirectional `[[links]]`, live Markdown rendering, and interactive audio jump points. |
| **Ask AI (Gemma Chat)** | On-device conversational assistant powered by Gemma 2B INT4 allowing users to query, summarize, and extract action items from their recorded day. |
| **Listening State** | Foreground service is actively processing audio frames through Silero VAD into the circular pre-roll ring buffer without writing to disk. |
| **Recording State** | Foreground service is actively streaming speech frames and buffer segments to the current WAV file. |
| **Dual-Timeline Sync** | Playback system that maps continuous condensed audio playhead seconds ($t_{\text{audio}}$) to real-world wall-clock timestamps ($t_{\text{real}}$). |

---

## Architectural Decision Records (ADRs)

- [ADR-0001: Standard RIFF/WAV Audio Format and Sidecar JSON Metadata Schema](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0001-sidecar-audio-metadata-schema.md)
- [ADR-0002: VAD Buffer State Machine and Crash-Resilient Audio Writer](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0002-vad-state-machine-and-crash-resilience.md)
- [ADR-0003: Storage Strategy, Public File Organization, and Smart Splitting](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0003-storage-strategy-and-smart-splitting.md)
- [ADR-0004: Foreground Service, Background Audio Lifecycle, and Boot Recovery](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0004-foreground-service-and-boot-recovery.md)
- [ADR-0005: Jetpack Compose UI Architecture and Dual-Timeline Synchronized Playback](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0005-ui-architecture-and-synchronized-playback.md)
- [ADR-0006: Silero VAD ONNX Runtime Integration and Zero-Allocation Engine](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0006-silero-vad-onnx-engine.md)
- [ADR-0007: Post-Processing Opus Compression, 700 MB Threshold, and Midnight Rollover](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0007-post-processing-opus-compression-and-midnight-rollover.md)
- [ADR-0008: Configurable File Splitting with 95 MB Default and Optional Opus Post-Processing](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0008-configurable-file-splitting-and-optional-opus-compression.md)
- [ADR-0009: Automated Google Drive Cloud Sync via WorkManager](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0009-google-drive-cloud-sync-workmanager.md)
- [ADR-0010: Local Multilingual Transcription via Whisper ASR and Gemma LLM Polish](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0010-local-multilingual-whisper-gemma-transcription.md)
- [ADR-0011: In-App Obsidian Vault, Markdown Journal & Local Gemma AI Assistant](file:///y:/AntiGravity/Android_Apps/recme/docs/adr/0011-in-app-obsidian-vault-and-gemma-ai-chat.md)
