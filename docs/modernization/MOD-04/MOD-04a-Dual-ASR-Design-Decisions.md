# MOD-04a — Dual ASR Design Decisions

**Parent:** [MOD-04-Transcription-Pipeline.md](../MOD-04-Transcription-Pipeline.md)  
**Status:** Approved

---

## User Control & Fallback Rules
1. **Mode Options in Settings:**
   - `Local Only (Private)`: Strictly offline SenseVoice/Whisper.
   - `Cloud Only (Gemini Flash)`: Google AI Studio API for deep nuance and formatting.
   - `Hybrid (Smart Fallback)`: Uses Cloud when connected, falls back seamlessly to Local when offline.
