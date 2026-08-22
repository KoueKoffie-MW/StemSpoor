# MOD-07a — Quick Settings Tile & Call Interruption Spec

**Parent:** [MOD-07-UX-Platform.md](../MOD-07-UX-Platform.md)  
**Status:** Approved

---

## System Integration Details
1. **`StemSpoorTileService` (`TileService`):**
   - Renders active recording state in Android status shade.
   - Tap toggles `VadRecordingService` without opening the app.
2. **`CallInterruptionWatchdog`:**
   - Listens to `TelephonyCallback.CallStateListener`.
   - Automatically pauses microphone capture when call starts, flushes WAV part, and resumes seamlessly after call hangs up.
