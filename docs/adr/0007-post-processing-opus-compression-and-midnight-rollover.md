# ADR-0007: Post-Processing Opus Compression, 700 MB Threshold, and Midnight Rollover

## Status
Accepted

## Context
Capturing 5+ hours of active speech daily produces ~600 MB to ~660 MB of raw 16-bit PCM WAV audio per day. Real-time audio compression during live microphone streaming introduces unnecessary CPU spikes, thermal throttling, and potential audio frame drops on mobile devices.
However, retaining uncompressed WAV indefinitely results in ~18–20 GB of storage per month.

## Decision
1. **700 MB Split Ceiling:**
   - Increase the soft file splitting threshold from 50 MB to $700\text{ MB}$ ($700 \times 1024 \times 1024$ bytes).
   - This accommodates ~5.5 to 6 hours of continuous speech in a single daily session file (`YYYYMMDD-HHMM-Part001.wav`).
2. **Midnight Date Rollover:**
   - When recording continuously across midnight (`00:00:00`), the session manager automatically marks a day-rollover event.
   - During the next silent pause, the completed day's file is closed and finalized, a new part is started with the new day's timestamp, and the completed file is dispatched for background compression.
3. **Background Opus Post-Processing:**
   - When a session part closes (via user Stop, 700 MB limit, or midnight rollover), an asynchronous background encoder converts the completed `.wav` file into an Opus-compressed audio file (`.opus` via Android `MediaCodec` + `MediaMuxer` @ 32 kbps, 16 kHz Mono).
   - Once the `.opus` file passes integrity checks, the companion `.json` sidecar's `file_name` field is atomically updated to point to the `.opus` file, and the original raw `.wav` file is deleted to reclaim ~90% of disk space.
4. **Player Compatibility:**
   - Android Media3 ExoPlayer and standard desktop media players (VLC, Audacity, etc.) natively play `.opus` containers with exact millisecond seeking parity.

## Consequences
- **Pros:**
  - Zero CPU/thermal overhead during live listening and recording.
  - 10x storage savings: 5 hours of speech compresses from ~600 MB to ~60 MB.
  - Clean 1-file-per-day organization.
- **Cons:**
  - While recording is actively in progress, temporary uncompressed disk space (~600 MB) is required until the session is finalized and compressed.
