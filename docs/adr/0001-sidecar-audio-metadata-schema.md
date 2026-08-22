# ADR-0001: Standard RIFF/WAV Audio Format and Sidecar JSON Metadata Schema

## Status
Accepted

## Context
The application records continuous audio while stripping silence using on-device Silero VAD. When silence is omitted, the timeline of the recorded audio file is discontinuous relative to wall-clock time. 
We need:
1. Universal playback compatibility across standard third-party media players (VLC, Audacity, QuickTime, OS default players).
2. Exact bi-directional synchronization between real-world wall-clock time (when words were spoken) and audio playhead position for our custom playback engine.

## Decision
1. **Audio Storage:** Save audio as standard, uncompressed RIFF/WAV files (16-bit Linear PCM, Mono, 16 kHz). The WAV file is standard-compliant and plays back seamlessly in any third-party player as a single continuous track containing only speech.
2. **Metadata Sidecar:** Store timeline synchronization data in an external companion JSON file named `<basename>.json` alongside `<basename>.wav`.
3. **Data Schema:** Each segment in the JSON defines both internal file offsets (`audio_start_ms`, `audio_end_ms`) and real-world Epoch timestamps (`speech_start_epoch_ms`, `speech_end_epoch_ms`, `started_at_epoch_ms`) along with buffer padding margins.

```json
{
  "version": 1,
  "file_name": "20260819-2315-Part001.wav",
  "sample_rate_hz": 16000,
  "channels": 1,
  "bit_depth": 16,
  "recording_session_id": "uuid-v4",
  "started_at_epoch_ms": 1787181300000,
  "segments": [
    {
      "segment_index": 0,
      "audio_start_ms": 0,
      "audio_end_ms": 4200,
      "speech_start_epoch_ms": 1787181300600,
      "speech_end_epoch_ms": 1787181304200,
      "pre_roll_ms": 600,
      "post_roll_ms": 600
    }
  ]
}
```

## Consequences
- **Pros:** 
  - Standard media players require zero modification or plugins to play the `.wav` files.
  - Custom player has O(1) timestamp-to-playhead seeking without parsing or decoding audio streams.
  - Clean separation of audio bytes and timecode metadata.
- **Cons:** 
  - If a user copies only the `.wav` file without the `.json` file to another computer, wall-clock timing data is lost for that copied instance (though the audio remains fully listenable).
