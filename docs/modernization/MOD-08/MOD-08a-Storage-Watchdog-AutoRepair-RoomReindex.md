# MOD-08a — Storage Watchdog & Auto-Repair Spec

**Parent:** [MOD-08-Ops-CI-Reliability.md](../MOD-08-Ops-CI-Reliability.md)  
**Status:** Approved

---

## Watchdog Details
1. **Header Repair:** If app is killed abnormally, inspects WAV file size on next boot, computes actual PCM length, and rewrites valid 44-byte RIFF header.
2. **Room Projection Sync:** Compares disk MD5 / modified timestamps against Room database to ensure sidecars remain the single source of truth.
