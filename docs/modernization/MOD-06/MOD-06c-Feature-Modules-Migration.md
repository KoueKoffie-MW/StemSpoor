# MOD-06c — Feature Modules Migration Spec

**Parent:** [MOD-06-Modular-Architecture.md](../MOD-06-Modular-Architecture.md)  
**Status:** Approved

---

## Step-by-Step Feature Migration Plan

1. **Create `:feature:record`**:
   - Move `com.example.recme.service.VadRecordingService`.
   - Move recording dashboard composables and live VAD meters.
2. **Create `:feature:recordings`**:
   - Move `com.example.recme.ui.screens.RecordingsScreen` and player sheet components.
3. **Create `:feature:settings`**:
   - Move `com.example.recme.ui.screens.SettingsScreen` and enrollment dialogs.
4. **Update `:app`**:
   - `MainActivity.kt`, `RecMeApplication.kt`, navigation graph, root Koin module assembly.
