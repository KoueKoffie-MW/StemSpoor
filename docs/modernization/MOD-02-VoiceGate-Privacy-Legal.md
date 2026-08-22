# MOD-02 — Voice Gate + Privacy & Legal Compliance

**Status:** Draft  
**Priority:** Highest (Legal + Compliance)  
**Phase:** Phase 2 + Cross-cutting  
**Owner:** Jan / Eben  
**Related:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Voice Gate section + Privacy section)  
**Date:** 2026-08-22

---

## Objective

Implement a **post-VAD speaker verification gate** that only persists audio when it matches an enrolled and explicitly allowed speaker.

This feature is designed to materially reduce legal risk (especially in Germany) while remaining optional and non-breaking.

---

## Legal & Privacy Context

### Germany (§ 201 StGB)
- It is a criminal offence to record the "non-publicly spoken word of another person" without authorization.
- Recording **only your own voice** is generally **not** covered.
- Any other person's voice requires consent.

### GDPR
- Raw voice = personal data.
- Creating **voiceprints / embeddings** for identification = biometric data (Article 9 special category).
- Requires **explicit consent**.

### Other Jurisdictions
- Varies (one-party vs all-party consent).
- The gate gives the user a strong technical control regardless of jurisdiction.

**Design Principle**: Make it easy to record *only* voices you have explicit permission to record.

---

## Current State

The app already has good building blocks:
- `SpeakerEmbeddingEngine` (192-d, ONNX + acoustic fallback)
- `SpeakerProfileManager` (centroids, language-aware, continuous learning, thresholds)
- `HybridSpeakerIdentifier`
- Speaker profiles with enable/disable for recognition
- VAD + pre/post-roll already working well

**Gaps**
- No early filter before writing audio
- No "allowed to record" concept
- No consent tracking
- Speaker matching happens *after* recording

---

## Detailed Requirements

### Functional
- Global toggle: **"Voice Filter – Only record enrolled & allowed voices"** (default = OFF)
- Per-speaker toggle: **"Allow recording this person"**
- Explicit consent acknowledgment when enabling a profile for recording
- Priority ordering: Check speakers sorted by recorded minutes (user first, then highest-data family, etc.)
- Only commit audio (including pre-roll) if the gate passes
- Log every gate decision (kept / discarded + matched profile + confidence) in sidecar metadata
- Unknown speakers are discarded when the filter is on (configurable fallback?)

### Non-Functional
- The gate must be fast and low-power (run only on VAD-positive speech)
- Must not increase battery impact noticeably
- Must be optional and reversible
- Should gracefully degrade if embedding model is unavailable

---

## Proposed Design

### Pipeline Position
```
Microphone
   ↓
VAD (Silero) → Speech Start
   ↓
Short Embedding Window (1–3s)
   ↓
**Voice Gate**  ← NEW
   ↓
   ├── Pass → Keep pre-roll + continue recording → Sidecar + Vault
   └── Fail → Discard segment (log reason)
```

### Key Components
- New class: `VoiceGate` (or `SpeakerRecordingGate`)
- Inputs: Short PCM + current enabled/allowed profiles + priority list
- Output: `GateResult` (allowed, matchedProfile, confidence, reason)
- Integration point: `VadRecordingService` (before committing buffers)
- Persistence:
  - Extend `SpeakerProfile` (or new Room entity) with `allowedToRecord`, `consentTimestamp`, `consentNote`
  - Add `gateDecision`, `gateProfileId`, `gateConfidence` to speech segment / sidecar metadata

### Model Strategy
- Start with existing `SpeakerEmbeddingEngine`
- Evaluate upgrade to **sherpa-onnx** speaker models (recommended for better accuracy + Android support)
- Keep fallback acoustic filterbank behavior

---

## Implementation Steps

1. **UI Changes (Quick Win)**
   - Add global Voice Filter toggle in Settings
   - Add "Allowed to record" switch per speaker profile
   - Add consent reminder text ("I confirm I have explicit consent from this person")

2. **Data Model**
   - Add fields to speaker profiles (JSON + future Room)
   - Update sidecar schema (versioned)

3. **Core Gate Logic**
   - Create `VoiceGate` class
   - Implement priority sorting by recorded minutes
   - Wire into recording service (behind the global flag)

4. **Audit & Logging**
   - Record all gate decisions
   - Add "Filter Health" debug view

5. **Model Evaluation**
   - Test current embedding engine accuracy on real home data
   - Prototype sherpa-onnx speaker verification model

6. **Room Integration** (tie into MOD-01)
   - Store consent metadata in Room

7. **Testing**
   - Unit tests for gate logic
   - On-device false negative rate measurement (especially own voice)
   - Edge cases: overlapping speech, background noise, children

---

## Dependencies

- Benefits strongly from MOD-01 (Room + better data model)
- Can start in parallel with limited scope (using current file-based profiles)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| False negatives (dropping own speech) | Good enrollment flow + adaptive threshold + optional "always record my voice" |
| Imperfect filtering in noisy/overlapping situations | Honest UX language + "unknown speaker" handling options |
| User enables filter but forgets to enroll family | Clear onboarding + warnings |
| Legal over-confidence | Never claim "100% legal safety". Document that this reduces (but does not eliminate) risk. |
| Performance cost | Only run on VAD-positive windows + use small/quantized model |

---

## Acceptance Criteria

- Global + per-profile toggles exist and are persisted
- When Voice Filter is ON, only audio from allowed profiles is written to disk
- Gate decisions are logged in sidecars
- Consent acknowledgment UI exists
- Feature is completely optional (off by default)
- On-device validation shows acceptable false-negative rate for the primary user
- Documentation updated (this sub-plan + main plan + relevant ADR)

---

## Open Questions (Resolved in Level 2)

All original open questions have been resolved with concrete decisions and technical details. See the deeper design document:

**→ [MOD-02a-VoiceGate-Design-Decisions.md](MOD-02/MOD-02a-VoiceGate-Design-Decisions.md)**

**Summary of Resolutions (now authoritative):**
- **Unknown speakers fallback**: Configurable. Default = strict discard when filter is on. Optional "Record as temporary Visitor (auto-expire)".
- **Visitors / one-time guests**: Supported via "Allow this session" (notification/quick action) + temporary guest profiles with expiration date.
- **Temporary "session allow"**: Yes — "Allow for next X hours" with automatic expiry.
- **Confidence threshold**: Global `gateConfidenceThreshold` (default ~0.72) + per-profile override. Uses the existing language-aware matching from `SpeakerProfileManager`.
- **Filter statistics**: Daily vault journal entry + "Voice Filter Health" section in Settings (kept %, top discard reasons). Optional debug mode for reviewing discards.

These decisions have been fed back from Level 2. The parent plan (this document) and main MODERNIZATION_PLAN.md should reflect the chosen behaviors going forward.

---

## References

- Main Modernization Plan – Voice Gate and Privacy sections
- Existing speaker code: `SpeakerEmbeddingEngine.kt`, `SpeakerProfileManager.kt`, `HybridSpeakerIdentifier.kt`
- Legal research: §201 StGB, GDPR Article 9 biometric guidance

---

**This sub-plan is intended for specification-driven review before implementation.**