# ADR-0004: Foreground Service, Background Audio Lifecycle, and Boot Recovery

## Status
Accepted

## Context
Continuous 24/7 background audio monitoring on modern Android (API 31+ through API 34+) is subject to strict OS execution boundaries:
1. Services accessing the microphone require `foregroundServiceType="microphone"`.
2. Starting a microphone foreground service directly from background broadcast receivers (e.g. `BOOT_COMPLETED`) is prohibited by Android 14+ security policies (`ForegroundServiceStartNotAllowedException`).
3. Aggressive Doze modes suspend background threads and CPU processing when the display is turned off.

## Decision
1. **Foreground Service & WakeLock:**
   - Implement `VadRecordingService` declared with `android:foregroundServiceType="microphone"`.
   - Acquire a `PowerManager.PARTIAL_WAKE_LOCK` upon service startup to ensure continuous audio sampling when the screen is turned off.
   - Maintain a persistent Foreground Notification displaying real-time status:
     - 🟢 *Listening (Silent)*: Monitoring `AudioRecord` frames via Silero ONNX.
     - 🔴 *Recording (Active)*: Writing audio segments and showing current part filename.
2. **Battery Optimization Exemption:**
   - Explicitly guide the user to grant `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
3. **Android 14+ Compliant Boot Restoration:**
   - Store recording state in persistent preferences (`isRecordingEnabled`).
   - On `ACTION_BOOT_COMPLETED`, if `isRecordingEnabled` is `true`:
     - Deliver an actionable high-priority status notification: *"RecMe: Tap to resume speech recording"*.
     - Tapping the notification triggers the app context to cleanly start the microphone foreground service without violating background service restrictions.

## Consequences
- **Pros:**
  - Robust, unkillable background listening loop across long sleep periods.
  - Zero crashes from Android 14+ background microphone initiation limits.
  - Clear user feedback via live persistent notification.
- **Cons:**
  - After a reboot, the user must tap the prompt notification once to resume background microphone recording on Android 14+.
