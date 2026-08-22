# ADR-0005: Jetpack Compose UI Architecture and Dual-Timeline Synchronized Playback

## Status
Accepted

## Context
The application records condensed speech audio without silence and outputs a companion JSON sidecar. The user needs to:
1. Start and stop background listening with clear visual feedback of whether speech is currently being detected.
2. Adjust VAD sensitivity thresholds and buffer configurations.
3. Browse recorded files and play them in a custom player that connects the continuous audio playhead with real-world wall-clock timestamps.
4. Seamlessly use the app in both portrait and landscape device orientations.

## Decision
1. **Architecture & Framework:**
   - Single-Activity Jetpack Compose application following MVI/MVVM pattern with Kotlin Coroutines and StateFlow.
   - Media playback powered by **AndroidX Media3 ExoPlayer**.
2. **Main Screen (Recorder View):**
   - High-contrast, scannable central Start/Stop recording toggle.
   - Dynamic status visualizer:
     - 🟢 *Listening (Silent)*: Pulsing ambient green ring.
     - 🔴 *Recording (Speech Detected)*: Glowing red active ring with live elapsed recording timer.
   - Quick navigation bar to Recordings Browser and Settings.
3. **Playback Screen (Dual-Timeline Synchronized Player):**
   - **Condensed Audio Scrub Bar:** Traditional slider tracking $00:00$ to total condensed audio duration.
   - **Real-World Timecode Display:** Real-time calculated timestamp showing the exact wall-clock time when the current audio sample was originally spoken in real life ($t_{\text{real}} = t_{\text{speech\_start\_epoch}} + (t_{\text{playhead}} - t_{\text{audio\_start}})$).
   - **Interactive Speech Segment List:** Scrollable list of recorded speech cards showing real-world start/end times and segment duration. Tapping any card seeks the audio directly to that speech block. The currently playing segment is visually highlighted and auto-scrolled.
   - **Playback Controls:** Play/Pause, Seek $\pm5\text{s}$, and Speed adjustment ($0.75\times, 1.0\times, 1.25\times, 1.5\times, 2.0\times$).
4. **Responsive Layout:**
   - Portrait: Stacked vertical player controls and segment list.
   - Landscape: Two-column layout with playback controls on the left and segment list on the right.

## Consequences
- **Pros:**
  - Intuitive navigation of compressed speech without losing real-world context.
  - Zero cognitive friction jumping directly to spoken utterances.
  - Modern, responsive Compose UI.
- **Cons:**
  - Requires loading and parsing the `.json` sidecar file when opening a `.wav` file in the player.
