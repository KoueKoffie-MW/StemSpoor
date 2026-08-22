# MOD-04c — Gemini Cloud Audio Studio Pipeline Spec

**Parent:** [MOD-04-Transcription-Pipeline.md](../MOD-04-Transcription-Pipeline.md)  
**Status:** Approved

---

## Cloud Integration Details
- Transcribes 16kHz audio using `gemini-2.0-flash` or `gemini-2.5-flash`.
- Emits structured markdown:
  - Verbatim timestamped transcript.
  - Action items & key decisions.
  - Wikilink candidate tags `[[Subject]]`.
