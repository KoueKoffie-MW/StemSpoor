# 🎙️ StemSpoor

> **Jou daaglikse stemspoor • All-day ambient voice buffer, acoustic speaker learning, and cognitive second brain.**

---

## 🧭 What is StemSpoor?

**StemSpoor** is a privacy-first, local-first Android companion that turns your everyday spoken thoughts, meetings, and conversations into a structured, searchable **Obsidian Knowledge Vault**.

Unlike conventional voice recorders that require you to remember to press "Record" and "Stop", **StemSpoor** operates silently 24/7 in the background with near-zero battery impact, capturing only meaningful voice segments and discarding dead silence.

---

## ⚡ Core Superpowers

### 1. 🔋 All-Day Low-Power Ambient Buffer
* **Zero-Touch Capture:** Runs continuously via a low-overhead foreground service.
* **Silero VAD (Voice Activity Detection):** Powered by an on-device ONNX runtime to segment speech in real-time while discarding ambient noise and dead silence.
* **Circular Audio Buffer:** Prevents runaway storage and preserves battery life.

### 2. 🗣️ Continuous Acoustic Voiceprints & Speaker Diarization
* **On-Device Acoustic Learning:** Enrolls speaker voiceprints (e.g. *Jan*, *Angelique*, team members) using spectral centroids and MFCC feature vectors.
* **Adaptive Speaker Assignment:** Seamlessly learns and updates speaker tags across sessions with instant local sidecar persistence.

### 3. 🌐 Trilingual Code-Switching Transcription
* **Natural Multilingual Support:** High-precision speech-to-text specifically tuned for natural code-switching across **Afrikaans**, **English**, and **German**.
* **Flexible AI Engines:** Supports cloud-based Gemini multi-turn transcription as well as on-device Whisper models.

### 4. 📓 Deep Obsidian Vault Integration
* **Daily Journal Timelines:** Automatically compiles and deduplicates daily notes (`vault/daily/YYYY-MM-DD.md`) with clean speaker tags and interactive audio citations.
* **Batch Vault Sync Engine:** Allows offline or review-first workflows, cleanly rebuilding daily notes without duplicating transcript blocks or overwriting manual notes.
* **Topic Wiki Links:** Generates `[[Topic]]` cross-references for frictionless second-brain graph navigation.

### 5. 🧠 Gemma Local Assistant & Persistent Chat
* **Ask StemSpoor AI:** Query your vault, extract action items (`- [ ]`), or synthesize executive summaries in English, Afrikaans, or German.
* **Full Chat Persistence:** Automatically saves conversation histories to structured JSON and readable Obsidian Markdown (`vault/chats/`).

### 6. ☁️ Google Drive Sync & Backup
* **Safe Cloud Offloading:** Automatically or manually backs up your recordings, companion sidecars, and vault markdown documents to Google Drive.

---

## 🏗️ Architecture & Storage Structure

StemSpoor adheres to a **Local-First, Transparent Storage Architecture** rooted in `/sdcard/Documents/RecMe/` (or your configured vault path):

```
Documents/RecMe/
├── <Recording_Basename>.opus         # Compressed audio recording
├── <Recording_Basename>.json         # Sidecar metadata, speech segments, speaker tags
├── vault/
│   ├── daily/
│   │   └── YYYY-MM-DD.md             # Consolidated daily timeline & action items
│   ├── recordings/
│   │   └── <Recording_Basename>.md   # Detailed individual recording note
│   ├── topics/
│   │   └── [[Topic]].md              # Generated topic reference notes
│   └── chats/
│       ├── chat_history.json         # Persistent Ask AI conversation history
│       └── AskAI-Latest.md           # Live Markdown view of recent AI queries
```

---

## 🛠️ Tech Stack & Libraries

* **Framework:** Android SDK (API 34+), Kotlin Coroutines & Flows
* **UI:** Jetpack Compose, Material 3, Navigation Compose
* **Audio & AI:**
  * ONNX Runtime Android (`com.microsoft.onnxruntime:onnxruntime-android`)
  * Silero VAD ONNX Engine
  * Google Generative AI SDK (`com.google.ai.client.generativeai`)
  * Kotlinx Serialization (`kotlinx-serialization-json`)
* **Storage & Auth:** Google Drive REST API v3, Google Play Services Auth

---

## 🚀 Building from Source

```bash
# Clone the repository
git clone https://github.com/jan/stemspoor.git
cd stemspoor

# Assemble debug APK
./gradlew assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the MIT License.
