# MOD-08 — Ops, CI, Reliability & Sync

**Status:** Draft  
**Priority:** Medium  
**Phase:** Phase 5  
**Parent:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Phase 5)  
**Index:** [INDEX.md](../INDEX.md)

---

## Objective

Make the app reliable for long-term always-on use, provide strong developer tooling, and ensure data safety and portability.

---

## Current State

- No CI.
- Custom Google Drive sync via WorkManager + REST (brittle).
- Good foreground service + boot recovery (see ADR-0004), but needs ongoing maintenance against Android changes.
- Limited observability and diagnostics in the wild.
- No formal release or versioning process yet.

---

## Detailed Requirements

### CI / Tooling
- GitHub Actions for build + unit tests (debug + release).
- Optional instrumentation tests on emulator.
- Static analysis / lint enforcement.

### Reliability
- Robust handling of Android 14+ / 15+ background restrictions.
- Battery and thermal impact monitoring.
- Crash reporting (local only or optional).
- Health checks and self-recovery for the recording service.

### Sync & Data
- Improve Google Drive sync reliability (or add alternatives).
- Full export/import bundle format.
- Self-hosted / local sync options (WebDAV, Syncthing-style, or simple folder sync) as future-proofing.

### Documentation & Process
- Keep ADRs up to date.
- Release process and versioning strategy.
- Performance & battery benchmark harness.

---

## Proposed Design

- GitHub Actions workflow matrix for AGP + Kotlin versions.
- Better WorkManager chaining and retry policies for sync/transcription.
- Structured logging + local diagnostic export.
- Define a clear "RecMe Bundle" export format (recordings + sidecars + vault + profiles).

---

## Implementation Steps

1. Set up GitHub Actions CI.
2. Add basic instrumentation test job.
3. Improve sync reliability and error handling.
4. Create export/import bundle feature.
5. Add battery/performance instrumentation.
6. Document Android version compatibility matrix (update ADR-0004 as needed).
7. Explore one alternative sync backend.

---

## Dependencies

- Benefits from modularization (MOD-06) for faster CI.
- Should happen after core data/AI stability.

---

## Risks

- Android keeps changing background rules.
- Sync is inherently unreliable (network, permissions, rate limits).
- Over-investing in CI before the app stabilizes.

---

## Acceptance Criteria

- Every push builds and runs unit tests in CI.
- Background service survives reboots and Doze on target Android versions.
- User can do a full export and import on a new device.
- Clear documentation of what is supported on which Android versions.

---

## Tool & Version Notes

- WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
- GitHub Actions for Android: https://github.com/actions (check current best practices)
- Google Drive API: https://developers.google.com/drive (verify current auth scopes and quotas)

---

**When working on reliability, always re-read ADR-0004 and the main plan's Background & Lifecycle section.**