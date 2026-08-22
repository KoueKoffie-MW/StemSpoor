# MOD-01c — Hilt & Dependency Injection Implementation (Level 3)

**Parent Level 1:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Parent Level 2:** Builds on the layering in MOD-01b  
**Focus:** One thing only — setting up Hilt for clean DI across the app.  
**Status:** Draft (Implementation Plan)  
**Date:** 2026-08-22

**Important:** This is a narrow, focused implementation plan. Execute only this scope. Link back to parents. When decisions are made during implementation, update this doc and propagate to higher levels (MOD-01b and Level 1 MOD-01).

**Recent Code Changes to Preserve:**
- Logo and branding (stemspoor_icon.png and docs/branding).
- Segment merge gap setting (do not touch its current SharedPrefs location in this scope — migration is in a separate plan).
- Brand colors in theme (Kalahari Sunset & Veld Earth palette in Color.kt and Theme.kt). Do not regress these.

**Tool Version Note:** Verify latest Hilt version and setup instructions at https://developer.android.com/training/dependency-injection/hilt-android (agent knowledge may be outdated).

---

## Scope (One Thing)

Introduce Hilt so that:
- `VadRecordingService`, repositories, managers, and future UseCases can be injected cleanly.
- Manual wiring in managers is reduced.
- This enables the repositories from MOD-01b and Voice Gate integration.

Out of scope (separate plans):
- Room schema creation (MOD-01d)
- Repository implementations
- Migration/bootstrap
- Moving the segment merge setting
- Branding work

---

## Current State Snapshot

- No Hilt.
- `RecMeApplication.kt` extends Application.
- Heavy manual `new` and constructor passing in `StorageManager`, `SpeakerProfileManager`, `VadRecordingService`, etc.
- `VadRecordingService` uses `getSharedPreferences` directly and constructs engines.
- SettingsScreen and other UI use `remember { Manager(context) }`.

---

## Implementation Steps (Granular — One at a Time)

1. **Add Dependencies**
   - In project-level build.gradle: classpath for Hilt.
   - In app build.gradle.kts: plugins (id("dagger.hilt.android.plugin")), implementations (hilt-android), kapt (hilt-compiler).
   - Sync and verify latest stable versions from official docs.

2. **Annotate Application**
   - Add `@HiltAndroidApp` to `RecMeApplication`.
   - Ensure it is declared in AndroidManifest.xml as the application class.

3. **Create Base Hilt Modules (Start Minimal)**
   - Create `di/AppModule.kt` (provide Context, DataStore if ready, legacy prefs bridge).
   - Create `di/ServiceModule.kt` for now (or include in AppModule).

4. **Enable Hilt in Activities & Services**
   - Annotate `MainActivity` with `@AndroidEntryPoint`.
   - Annotate `VadRecordingService` with `@AndroidEntryPoint`.
   - Inject needed dependencies later (do not wire yet).

5. **Update Existing Managers for Constructor Injection (Incremental)**
   - Change `StorageManager(private val context: Context)` to be injectable where possible.
   - Do the same for `SpeakerProfileManager`.
   - In this step, only make constructors take what Hilt can provide; do not move logic.

6. **Provide Legacy SharedPreferences Bridge**
   - In a module: `@Provides fun providePrefs(@ApplicationContext context: Context): SharedPreferences = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)`
   - This preserves the new segment merge gap setting and other prefs during transition.

7. **Test Basic Injection**
   - Inject a simple dependency (e.g. a dummy) into `VadRecordingService` or a test Activity.
   - Build and run. Confirm no Hilt errors.

8. **Document & Feedback**
   - Note any issues with the new branding colors or icon loading in this scope (should be none).
   - Update this doc with any deviations.
   - Propagate decisions (e.g. module split decisions) to MOD-01b and Level 1 MOD-01.

---

## Acceptance Criteria for This Plan Only

- App compiles with Hilt plugin and dependencies.
- `@HiltAndroidApp` and `@AndroidEntryPoint` are in place for main entry points.
- Basic injection works in `VadRecordingService` (even if just Context for now).
- No breakage to existing logo, colors, or segment merge UI/setting.
- This plan's steps are marked complete only when the above pass.

---

## Links & Next

- After this: Move to MOD-01d for Room schema implementation.
- Sibling plans will use the Hilt setup created here.
- When complete, update parents so future agents see "Hilt is ready — see MOD-01c".

**Always re-read the parent MOD-01 and MOD-01b before and after working on this.**

This is one focused piece of the Foundations implementation pack. Complete, self-contained, and linkable.