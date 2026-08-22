# MOD-02a — Voice Gate: Detailed Design & Decisions (Level 2)

**Parent:** [MOD-02-VoiceGate-Privacy-Legal.md](../MOD-02-VoiceGate-Privacy-Legal.md)  
**Related:** [MOD-01](../MOD-01-PHASE1-Foundations.md), [MOD-03](../MOD-03-Speaker-Embeddings.md), Main [MODERNIZATION_PLAN.md](../../../MODERNIZATION_PLAN.md)  
**Level:** 2 (Technical Design + Resolved Decisions)  
**Status:** Draft  
**Date:** 2026-08-22

---

## Purpose of This Document

This is a **deeper layer** under MOD-02.  
It provides concrete technical design, integration points with existing code, and **resolves the open questions** listed in the parent MOD-02.

As decisions are made here, they will be:
- Documented in this file (with rationale).
- Propagated back to the Level 1 MOD-02.md (questions marked "Resolved — see MOD-02a").
- If they affect higher strategy, noted in the main MODERNIZATION_PLAN.md.

**Always read the parent MOD-02 and main plan before implementing from this doc.**

---

## Core Technical Design

### Placement in Pipeline
From analysis of `VadRecordingService.kt` + `AudioCaptureEngine`:

```
AudioCaptureEngine (circular pre-roll buffers)
   ↓
Silero VAD detects speech start
   ↓
[NEW] VoiceGate.decide(gateWindowSamples, context)
   ├── Allowed → commit pre-roll + continue recording
   │             → create SpeechSegment with gateDecision=ALLOWED
   └── Denied  → discard buffer window
                 → log gateDecision=DENIED + reason
```

**Window for Gate Decision**
- 1.5–3.0 seconds of 16 kHz PCM (starting from VAD trigger).
- Use the same buffer the service already maintains for pre-roll.
- This keeps power low (embedding only runs on speech, not silence).

### VoiceGate Class (Proposed)

```kotlin
interface VoiceGate {
    suspend fun decide(
        samples: FloatArray,           // short window
        languageHint: String? = null
    ): GateDecision
}

data class GateDecision(
    val allowed: Boolean,
    val matchedProfileId: String?,
    val confidence: Float,
    val reason: String,
    val usedFallback: Boolean = false
)
```

**Responsibilities**
- Load current "allowed" profiles (filtered from SpeakerProfileManager).
- Sort by priority (see below).
- Run embedding (via injected `SpeakerEmbeddingEngine`).
- Score + decide.
- Never block the audio thread — use coroutine.

**Injection (Hilt)**
- Provided by MOD-01 foundations.
- Depends on: `SpeakerProfileManager`, `SpeakerEmbeddingEngine`, `VoiceGateConfig`.

### Priority Ordering (Data Volume)

Current `SpeakerProfile` has `sampleCount`.  
**Decision:** Add `estimatedMinutes` (or derive from sidecar metadata later via Room).

Priority logic:
```kotlin
val allowedProfiles = profiles
    .filter { it.allowedToRecord }
    .sortedByDescending { it.estimatedMinutes }   // user usually has most
```

This matches the original requirement exactly.

---

## Resolved Open Questions (with Decisions)

### 1. "Record unknown speakers" fallback mode?

**Decision (Level 2):**  
- **Default when Voice Filter = ON**: Strict discard (unknown → not recorded).
- Configurable fallback in Settings: 
  - "Discard unknown" (recommended for compliance)
  - "Record as temporary Visitor (auto-delete after N days)"
- Visitor recordings get a special `speaker = "Visitor"` tag and are **never** used for voiceprint learning.

**Rationale:** Gives user control. Strict mode best protects against §201 StGB risk. Visitor mode useful for one-off situations without permanent enrollment.

**Impact on higher levels:** Updated in parent MOD-02 "Functional Requirements" and "Risks".

### 2. How to handle visitors / one-time guests?

**Decision:**
- Support two paths:
  a. **One-shot "Allow this session"** button in the notification or quick settings (lasts until service restart or user disables).
  b. **Temporary Guest Profile** — user can create a profile named e.g. "Uncle Piet" with `allowedToRecord=true` + expiration date (stored in Room/JSON).

**UI:** In Settings → Speaker profiles there will be an "Add Temporary Guest" flow.

**Data:** Temporary profiles have `expiresAtEpochMs`. Gate logic ignores expired ones.

### 3. Temporary "session allow" for a meeting?

**Decision:** Yes.
- "Allow for next X hours" (preset: 1h, 2h, 4h, or custom).
- Implemented as a runtime override list in `VoiceGateConfig` (in-memory + persisted to DataStore).
- Automatically expires.
- Logged clearly.

**Technical:** `VoiceGate` receives a `temporaryAllows: List<TemporaryAllow>`.

### 4. Minimum confidence threshold policy?

**Decision:**
- Global `gateConfidenceThreshold` (default 0.72, range 0.60–0.90). Reuse/extend existing `recognitionThreshold` from SpeakerProfileManager.
- Per-profile override possible (stored in SpeakerProfile).
- If best match < threshold → treat as unknown (apply fallback rule above).
- When language is known, prefer language-specific centroid if available (already supported in `matchEmbedding`).

**Rationale:** Existing hybrid matching logic is strong; we keep the same scoring but apply a gate-specific threshold.

### 5. How do we surface filter statistics?

**Decision:**
- **Daily vault note** (in the existing daily journal):  
  `**Voice Filter:** 87% of speech kept (12 segments discarded as unknown)`
- **Settings screen section:** "Voice Filter Health"
  - Last 7/30 days % kept
  - Top 3 reasons for discards
  - Button: "Review discarded segments (if any were saved in debug mode)"
- Optional debug mode (hidden): keep short discarded clips with consent warning.

**Implementation note:** After MOD-01 Room work, query from `SpeechSegment` table with `gateDecision` column.

### 6. False-Rejection Mitigation: 10-Second Rolling Circular Verification Buffer

**Decision:**
- **The Risk:** In noisy conditions (e.g. driving, wind, background chatter) or with short 1–2s opening phrases ("So, luister hier..."), the initial embedding extraction might temporarily score below the confidence threshold (e.g. 0.65 vs 0.72 target).
- **The Solution:** Rather than discarding speech immediately on sub-threshold score, `AudioCaptureEngine` holds active speech frames in a **10-second rolling circular verification buffer**.
- **Retroactive Commitment:** As the user continues speaking and confidence surpasses the verification threshold at second 2 or 3, the entire utterance from the true start of speech is retroactively committed to disk.
- **Definitive Discard:** If the speech burst completes and the cumulative window confidence never meets the threshold, the buffer is purged without writing to disk.

**Rationale:** Completely eliminates the risk of dropping the user's valuable ideas due to transient acoustic noise while maintaining strict zero-disk persistence for unconsented third parties.

---

## Deeper Integration Details

### Changes to Existing Classes

**SpeakerProfile** (add fields):
```kotlin
data class SpeakerProfile(
    ...
    val allowedToRecord: Boolean = false,
    val consentTimestamp: Long? = null,
    val consentNote: String? = null,
    val estimatedMinutes: Double = 0.0,      // new for priority
    val gateConfidenceOverride: Float? = null
)
```

**VadRecordingService** (high-level change):
- After VAD positive segment start:
  ```kotlin
  val window = audioCaptureEngine?.getRecentWindowForGate(2000) ?: return
  val decision = voiceGate.decide(window, languageHint)
  if (decision.allowed) {
      commitSegment()
      sidecarWriter.appendGateDecision(decision)
  } else {
      discardCurrentBuffers()
      logDiscard(decision)
  }
  ```

**Sidecar Metadata** (update schema — version bump):
```json
"gate": {
  "decision": "ALLOWED" | "DENIED",
  "profileId": "jan_jvr",
  "confidence": 0.81,
  "reason": "matched"
}
```

### Model Recommendations (with freshness note)

- Start with current `SpeakerEmbeddingEngine` (192-d).
- Strongly evaluate **sherpa-onnx** speaker models for the gate (see https://github.com/k2-fsa/sherpa-onnx for latest Android speaker verification examples — always verify current recommended model and Android build instructions as agent knowledge can be outdated).

---

## Error Handling & Graceful Degradation

- If embedding model fails → fall back to acoustic extractor (already in `SpeakerEmbeddingEngine`).
- If no profiles are "allowed" yet → treat all speech as unknown (or force "my voice only" mode).
- Provide clear user message in notification/Settings: "Voice Filter active but no allowed speakers enrolled."

---

## Next Deeper Layer (if needed)

Possible future Level 3 under this:
- `MOD-02b-Implementation-Tasks.md` (granular TODO list with owners)
- `MOD-02c-Pseudocode-and-Tests.md`
- Performance benchmarks for gate latency on target devices.

---

## Propagated Decisions (for higher levels)

These should be incorporated into the parent documents:

- Added configurable unknown-speaker fallback modes.
- Added temporary/session allow mechanisms.
- Clarified confidence policy.
- Added concrete stats surfacing plan.

**Action for parent:** Update "Open Questions" section in MOD-02 to "Resolved — see MOD-02a".

---

**This document is part of the deeper planning layer. Read parents first.**