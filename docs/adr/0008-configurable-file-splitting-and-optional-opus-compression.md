# ADR-0008: Configurable File Splitting with 95 MB Default and Optional Opus Post-Processing

## Status
Accepted

## Context
ADR-0007 introduced background Opus post-processing and a 700 MB splitting limit. However, users may have different workflow requirements:
1. Some users require pristine uncompressed 16-bit PCM WAV files without any lossy transcoding or file format conversion for downstream audio analysis.
2. A 700 MB file size is large for quick transfers, whereas a **95 MB default** file size (~45–50 minutes of concentrated speech per file) is easier to manage, share, and backup while keeping files reasonably sized.
3. Users need flexible user settings to toggle Opus compression on/off and configure the maximum split size in megabytes.

## Decision
1. **Default WAV Split Size Reduced to 95 MB:**
   - Change the default soft file splitting ceiling from 700 MB to **95 MB** ($95 \times 1024 \times 1024$ bytes, corresponding to ~49.5 minutes of continuous 16-bit 16 kHz Mono speech).
   - Splitting remains smart: deferred to the end of an active speech utterance during the subsequent silence hangover to prevent mid-word cuts.
2. **Configurable Split Size:**
   - Add a configuration slider/selector in Settings (range: 25 MB to 700 MB, default: 95 MB) persisted in SharedPreferences (`key_split_size_mb`).
3. **Optional Opus Post-Processing:**
   - Add an Opus compression toggle in Settings persisted in SharedPreferences (`key_opus_compression`, default: true/enabled).
   - When enabled: completed session parts are asynchronously converted to 32 kbps Opus (`.opus`) upon silence rollover or session stop, updating the sidecar JSON and deleting the raw `.wav`.
   - When disabled: completed files remain pristine uncompressed `.wav` files and the sidecar JSON retains `.wav` references.

## Consequences
- **Pros:**
  - Users have full control over audio compression and file segmentation.
  - 95 MB default produces manageable file sizes for quick USB transfers and sharing.
  - Pristine PCM WAV workflow is fully preserved when Opus compression is toggled off.
- **Cons:**
  - Disabling Opus compression will increase long-term storage usage (~600 MB/day for 5h speech).
