# ADR-0003: Storage Strategy, Public File Organization, and Smart Splitting

## Status
Accepted

## Context
The app must store ~50 MB chunked `.wav` files alongside companion `.json` sidecar files in a public shared directory (e.g. `Documents/RecMe/`) that is directly visible and copyable over USB MTP and third-party file managers. Modern Android (API 30+) restricts filesystem access for arbitrary non-media sidecar files unless explicit storage management is utilized. Files must also split cleanly on sentence boundaries without mid-word cuts.

## Decision
1. **Directory Location & Permissions:**
   - Store recordings in public storage at `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)/RecMe/`.
   - Request `MANAGE_EXTERNAL_STORAGE` (`ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`) on Android 11+ (API 30+) for unrestricted, direct `File` / `RandomAccessFile` operations on paired `.wav` and `.json` files, with fallback to app-specific external files if permission is withheld.
2. **File Naming Convention:**
   - Format: `YYYYMMDD-HHMM-PartNNN.wav` and `YYYYMMDD-HHMM-PartNNN.json` (e.g. `20260819-2315-Part001.wav`).
   - Timestamps represent the start of the recording session / file creation.
3. **Smart Splitting Mechanism:**
   - Soft threshold: $50 \times 1024 \times 1024$ bytes (~52.4 MB).
   - If the active `.wav` file reaches or exceeds the threshold during an ongoing speech utterance, set `isSplitPending = true` without interrupting the recording.
   - When the speech segment ends (the 600 ms post-roll silence concludes):
     1. Complete and flush the current `.wav` RIFF header and `.json` sidecar.
     2. Increment part counter (`Part002`).
     3. Instantiate the new `.wav` and `.json` files.
     4. Clear `isSplitPending`.
     5. Maintain the 600 ms pre-roll circular buffer continuously across the transition to ensure zero lost frames.

## Consequences
- **Pros:**
  - Fast, unbuffered native I/O with standard Java/Kotlin file APIs.
  - Paired `.wav` and `.json` files sit side-by-side in user-visible `Documents/RecMe/`, trivially accessible via USB cable.
  - Zero mid-word file cut-offs.
- **Cons:**
  - Requires guiding the user to the system "All files access" settings toggle upon first launch.
