# MOD-01a — Room Schema & Consent Model (Level 2)

**Parent:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Driven by:** MOD-02 Voice Gate requirements  
**Level:** 2 (Detailed Data Model)

## Objective

Define the minimal Room entities needed to support the Voice Gate (MOD-02) while laying the foundation for the rest of the data layer.

## Key Entities (Start Small)

### SpeakerProfileEntity (or extend existing JSON + add Room mirror)
- id (primary key, stable slug)
- name
- allowedToRecord: Boolean
- consentTimestamp: Long?
- consentNote: String?
- estimatedMinutes: Double (for gate priority)
- gateConfidenceOverride: Float?
- lastUpdated

**Rationale:** Voice Gate needs fast filtering of "allowed" profiles + priority by data volume. Room gives us this without scanning JSON every time.

### SpeechSegmentEntity
- id
- recordingId (FK)
- startTimeWall, endTimeWall
- speakerId (nullable)
- gateDecision: String ("ALLOWED", "DENIED_UNKNOWN", "DENIED_LOW_CONF", ...)
- gateProfileId
- gateConfidence
- language
- ...

This enables the filter statistics and audit logging required by MOD-02a.

## Migration & Consistency Strategy

- Sidecars remain the **authoritative** source for audio and full transcript data.
- Room is a **query-optimized projection** + consent/allowed state.
- On first run after MOD-01, do a one-time scan of existing sidecars to populate the initial Room tables.
- Use versioned sidecar schema + Room migrations.

## Next Steps When Implementing

1. Define entities + DAOs in the new `:data` or core module (coordinate with MOD-06 modularization).
2. Create a `SpeakerConsentRepository` and `GateAuditRepository`.
3. Ensure `SpeakerProfileManager` (or new repository) can read/write the `allowedToRecord` flags from Room (with JSON fallback during transition).

## References
- Parent MOD-01
- MOD-02a for exact fields needed by the gate
- Existing `SpeakerProfile` data class for the JSON side

**Update parent MOD-01 once this model is reviewed.**