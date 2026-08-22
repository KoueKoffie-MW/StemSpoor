# ADR-0011: In-App Obsidian Vault, Markdown Journal & Local Gemma AI Assistant

## Status
Accepted

## Context
RecMe continuously records, compresses, and transcribes multilingual speech on-device. However, viewing raw transcripts as isolated files lacks synthesis and personal knowledge management (PKM) capabilities. Users require a built-in second-brain interface (similar to Obsidian / Logseq) to browse daily journals, navigate bidirectional `[[links]]`, jump directly to audio timestamps, and leverage the local Gemma 2B LLM for daily executive summaries, action item extraction, and conversational Q&A.

## Decision
1. **Native Compose Vault & Markdown Journal:**
   - Implement a native Jetpack Compose Markdown reader and editor supporting GitHub Flavored Markdown (GFM), bidirectional wiki-links (`[[Topic]]`), tags (`#tag`), and interactive audio timestamps (`**[HH:MM:SS]**`).
   - Tapping any timestamp in a note launches the synchronized audio player starting at that exact millisecond.
2. **Obsidian-Standard Directory Structure:**
   - Store vault artifacts in a standard structure on local disk (`/sdcard/Documents/RecMe/vault/`) and Google Drive:
     - `vault/daily/`: Daily journal logs (`YYYY-MM-DD.md`) aggregating the day's speech segments, executive summaries, and action items.
     - `vault/topics/`: Individual topic notes linked via `[[Topic]]`.
     - `vault/recordings/`: Paired audio files (`.opus` / `.wav`) and `.json` sidecars.
   - This directory can be opened natively by desktop Obsidian or Logseq as a standard vault.
3. **Local Gemma AI Intelligence Suite:**
   - **Executive Summaries:** Generates concise bulleted summaries of daily conversations.
   - **Action Item Extraction:** Parses commitments into checklist items (`- [ ] Task`).
   - **Auto [[Topic]] Linking:** Detects recurring domain topics and creates cross-links.
   - **Ask AI (Chat Assistant):** Conversational interface to query daily notes and transcripts with real-world time citations, supporting multilingual queries in Afrikaans, English, and German.
4. **App Navigation Architecture:**
   - Bottom Navigation Bar with 3 primary destinations:
     - 🎙️ **Recorder**: Live VAD audio capture, waveform, and status.
     - 📓 **Vault & Notes**: Daily journal timeline, markdown reader/editor, and topic graph.
     - 💬 **Ask AI**: Gemma conversational Q&A across the second-brain vault.

## Consequences
- **Pros:**
  - Complete end-to-end capture $\rightarrow$ transcribe $\rightarrow$ synthesize $\rightarrow$ query pipeline in a single private app.
  - Native Obsidian interoperability without proprietary lock-in.
  - Instant audio verification from any note via clickable timestamps.
  - 100% offline, private AI summaries and chat.
- **Cons:**
  - Requires maintaining the vault file hierarchy across sync workers.
