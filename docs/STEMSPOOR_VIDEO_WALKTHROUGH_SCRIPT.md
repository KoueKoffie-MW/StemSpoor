# 🎬 StemSpoor — Official Video Walkthrough & Marketing Demo Script

**Suggested Title:** *StemSpoor: The Private Cognitive Brain for Engineers (Full Feature Test & Demo)*  
**Presenter:** Jan  
**Duration:** ~10–12 minutes  
**Format:** Screen recording (Android device + Obsidian on desktop) combined with webcam / talking-head camera setup.

---

## 🎯 Video Objectives
1. **Live Technical Verification:** Prove that every subsystem (Silero VAD, Voice Gate, Speaker Diarization, Dual ASR, Local Vector Search, Storage Watchdog) operates reliably in real time.
2. **Value Proposition & Marketing:** Clearly articulate *why* StemSpoor is fundamentally different from conventional voice recorders (Legal §201 StGB privacy by design, zero-cloud dependency, local dense embeddings, Obsidian Vault integration).
3. **Authentic Engineer Narrative:** A pragmatic, candid demonstration rooted in engineering rigor, self-reliance, and radical privacy.

---

## 📋 Production Overview & Scene Timeline

```
[00:00 - 01:15]  Scene 1: The Hook & The Problem (Memory vs. Strict Privacy Laws)
[01:15 - 02:45]  Scene 2: 1-Tap Quick Settings Tile & Silero VAD Ambient Listening
[02:45 - 04:30]  Scene 3: Voice Gate & The 10-Second Circular RAM Buffer (§201 StGB Live Test)
[04:30 - 05:45]  Scene 4: Telephony Call Watchdog (Auto-Pause & Seamless Resume)
[05:45 - 07:15]  Scene 5: Speaker Diarization & Continuous Voiceprint Adaptation
[07:15 - 09:00]  Scene 6: Dual Transcription Pipeline (Offline SenseVoice vs. Cloud Gemini Flash)
[09:00 - 10:15]  Scene 7: Obsidian Vault Integration & Local 384-d Semantic Vector Search
[10:15 - 11:15]  Scene 8: Storage Integrity Watchdog (WAV Header Auto-Repair & Room Sync)
[11:15 - 12:00]  Scene 9: Conclusion & Open Source Call to Action
```

---

## 🎥 Scene-by-Scene Script

### Scene 1: The Hook & The Problem (00:00 – 01:15)
* **Visual:** Camera on Jan at his engineering desk. The phone is resting next to the workstation. On the main monitor, an automotive / Simscape model or Obsidian graph is visible.
* **On-Screen Title:** `StemSpoor: Private Ambient Voice Intelligence`

> **Jan (Spoken):**  
> *"As an engineer and father, my mind is constantly moving through ideas, system architectures, and technical decisions. But whenever you articulate a breakthrough thought while walking, driving, or working, it’s often lost within five minutes.*  
>  
> *Traditional voice recording apps fail for two reasons: First, the friction of manually opening an app and pressing record means you rarely do it. Second—and critically here in Germany and the EU—you face strict privacy regulations like Section 201 of the German Criminal Code (§201 StGB). You cannot legally or ethically record conversations or ambient speech of people around you without consent.*  
>  
> *That is why I built **StemSpoor**. It is not just an audio recorder; it is a private, on-device cognitive brain that verifies your voice before anything touches storage, respects ambient privacy mathematically, and automatically turns your thoughts into a structured Obsidian knowledge graph. Let me show you how every single feature works under the hood."*

---

### Scene 2: 1-Tap Quick Settings Tile & Silero VAD (01:15 – 02:45)
* **Visual:** Android screen recording. Jan swipes down the Android notification shade and taps the **StemSpoor Mic Tile**.
* **Action:** The tile flips from inactive to `StemSpoor Active (Recording active)`. Jan opens the app to show the Kalahari sunset waveform visualizer responding to voice.

> **Jan (Spoken):**  
> *"First: zero friction. I don't even need to open the app. From my Android Quick Settings shade, I tap the **StemSpoor Tile** once.*  
>  
> *Under the hood, **Silero VAD** (Voice Activity Detection) is running 24/7 in a foreground service. Notice what happens when I stay silent... the microphone captures raw 16kHz audio in RAM, but zero bytes of empty silence are written to disk. The moment I speak, the Kalahari sunset waveform illuminates instantly and begins capturing.*  
>  
> *This eliminates gigabytes of dead air, cuts battery consumption to near zero, and ensures you capture only genuine speech."*

---

### Scene 3: Voice Gate & The 10-Second Circular RAM Buffer (§201 StGB) (02:45 – 04:30)
* **Visual:** Navigate to `Settings` $\rightarrow$ `Voice Gate & Legal Privacy`. Show the enrolled profile (`Jan`) with consent switches.
* **Live Test:**
  1. **Authorized Voice:** Jan speaks directly into the phone: Screen displays `Voice Gate: ALLOWED (Jan - 0.91 confidence)`.
  2. **Unauthorized Voice:** Jan plays an unauthorized voice snippet from a secondary speaker: Screen displays `Voice Gate: DENIED (Unauthorized voice discarded)`.

> **Jan (Spoken):**  
> *"Now for the core privacy innovation: **The Voice Gate**.*  
>  
> *Under §201 StGB in Germany, capturing third-party speech without explicit consent is a criminal offense. Most ambient recorders ignore this. StemSpoor solves it architecturally using a **10.24-second pre-allocated circular RAM buffer**.*  
>  
> *When speech is detected, the audio remains strictly in volatile memory while our neural embedding engine extracts a 192-dimensional acoustic timbre vector and computes cosine similarity against my enrolled voice profile.*  
>  
> *If the speaker is verified as me—the buffer is retroactively flushed to the WAV recording. But if an unauthorized third party speaks—the entire audio buffer is silently purged from memory. Not a single byte is ever committed to disk. That is legal compliance guaranteed by math, not policies."*

---

### Scene 4: Telephony Call Watchdog (04:30 – 05:45)
* **Visual:** An incoming phone call notification triggers on screen.
* **Action:** The recording engine immediately finalizes the current audio chunk, releases the microphone hardware, and sets state to `PAUSED (Call in progress)`. When the call ends, StemSpoor resumes capturing automatically.

> **Jan (Spoken):**  
> *"What happens when an actual phone call arrives while StemSpoor is listening?*  
>  
> *Our **Telephony Call Interruption Watchdog** listens to Android's call state. The instant the phone rings or an outgoing call begins, the current audio part is cleanly finalized and closed, freeing the microphone hardware completely for the phone call.*  
>  
> *As soon as you hang up, StemSpoor automatically detects the idle state and resumes background listening without requiring any user interaction. You never have to remember to turn your recorder back on."*

---

### Scene 5: Multi-Speaker Diarization & Continuous Adaptation (05:45 – 07:15)
* **Visual:** Open the `Recordings` screen. Select a multi-person conversation. Show the companion `.json` sidecar.
* **Action:** Segments are neatly partitioned and labeled with speaker IDs (`Jan`, `Speaker 2`) and confidence scores.

> **Jan (Spoken):**  
> *"Once an audio recording session is finalized, our offline **Speaker Diarization Engine** takes over in the background. It analyzes overlapping audio windows, groups speaker turns using acoustic timbre clustering, and tags who spoke when.*  
>  
> *Even better: StemSpoor features **continuous voiceprint adaptation**. Whether I am speaking Afrikaans, English, or German, every time a segment matches my profile with high confidence ($\ge 84\%$), it updates my multi-language voice centroids using exponential moving averages. The more I use the app, the sharper its recognition becomes."*

---

### Scene 6: Dual Transcription Pipeline (07:15 – 09:00)
* **Visual:** Navigate to `Settings` $\rightarrow$ `AI Models & Transcription Engine`. Show the 3 modes: `Local Only (Private Offline)`, `Google AI Studio (Gemini Flash)`, and `Smart Hybrid`.
* **Action:**
  1. Show offline on-device transcription running on CPU via **SenseVoice-Small** (15x real-time).
  2. Switch to **Smart Hybrid** with Google AI Studio API key: Demonstrate Gemini Flash producing structured JSON with verbatim transcripts, executive summary, and action items checklist.

> **Jan (Spoken):**  
> *"For speech-to-text, StemSpoor gives you complete control through our **Dual Transcription Pipeline**.*  
>  
> *If you require 100% offline privacy where no data ever leaves your hardware, the local engine uses **SenseVoice-Small** and Whisper INT8. It runs directly on the CPU at 15x real-time speed, with remarkable accuracy across multilingual code-switching.*  
>  
> *If you're online and want deeper synthesis, select **Smart Hybrid**. It routes the audio directly to Google AI Studio's **Gemini 2.5 Flash**, returning not just a verbatim transcript, but an executive summary, key decisions, and actionable markdown checkboxes in one shot."*

---

### Scene 7: Obsidian Vault & Local 384-d Semantic Search (09:00 – 10:15)
* **Visual:** Switch view to the desktop monitor showing the **Obsidian Vault**. The Daily Note (`2026-08-22.md`) contains the new transcription, timestamps, speaker tags, and `[[Wikilinks]]`.
* **Action on Phone:** Open the `Vault` tab in StemSpoor. In the search bar, search for a conceptual phrase: *"wheel dynamics and damper tuning"*.
* **Result:** The search engine immediately ranks the relevant note at `94% SEMANTIC_CONCEPT` match, even though the spoken words were *"Simscape Multibody suspension test"*.

> **Jan (Spoken):**  
> *"Every transcript is exported into your personal **Obsidian Vault** with clean Markdown formatting and bidirectional `[[Wikilinks]]`.*  
>  
> *To search your thoughts, we implemented a **Hybrid Semantic Search Engine**. We compute 384-dimensional dense BGE vector embeddings and store them directly in SQLite. When I search for a concept, it doesn't just match exact keywords—it understands semantic meaning and retrieves the exact thought instantly, completely offline."*

---

### Scene 8: Storage Integrity Watchdog & In-Place Auto-Repair (10:15 – 11:15)
* **Visual:** Go to Android App Settings and hit **Force Stop** while a recording is actively capturing.
* **Action:** Re-open StemSpoor. Show log output: `StorageIntegrityWatchdog: Repaired WAV header in-place (DataSize corrected), Room SQLite resynced`. Open the recording and play it back—the audio plays cleanly from start to finish.

> **Jan (Spoken):**  
> *"In engineering, you design for failure. What happens if your phone battery dies or the OS kills the app during a recording? Normally, an unfinalized WAV file has corrupt header sizes and is unplayable.*  
>  
> *StemSpoor includes an autonomous **Storage Integrity Watchdog**. On app startup, it inspects every WAV file on disk, recalculates the exact PCM byte length, repairs the 44-byte RIFF header in-place, and resynchronizes Room SQLite. Your recorded data is immune to unexpected termination."*

---

### Scene 9: Conclusion & Call to Action (11:15 – 12:00)
* **Visual:** Jan on camera with the StemSpoor GitHub repository and Kalahari Sunset logo displayed on screen.
* **On-Screen Text:** `Open Source. Zero Cloud Lock-in. Built for Engineers.`

> **Jan (Spoken):**  
> *"StemSpoor is more than just an app—it is an external cognitive brain that protects your privacy, captures your spontaneous thinking, and turns your spoken words into structured, searchable knowledge.*  
>  
> *No subscriptions, no cloud lock-in, and 100% data ownership. The entire codebase is open source on GitHub.*  
>  
> *Clone it, build it, and take control of your voice trace. Thanks for watching!"*

---

## 🛠️ Pre-Recording Checklist

- [ ] Install latest APK build (`main` / `assembleDebug`).
- [ ] Grant Microphone and All Files Storage permissions on test phone.
- [ ] Enroll 1 speaker profile (`Jan`) under Settings $\rightarrow$ Voice Gate.
- [ ] Add the StemSpoor Mic Tile to the Quick Settings shade.
- [ ] Verify Google AI Studio API key is configured for the Gemini Flash demo.
- [ ] Open Obsidian connected to `Documents/RecMe/vault` to show live synchronization.
