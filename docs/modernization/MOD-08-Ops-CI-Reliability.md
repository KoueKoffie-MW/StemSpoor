# MOD-08 — Storage Integrity Watchdog & Reliability

**Status:** Approved  
**Priority:** Medium  
**Phase:** Phase 5  
**Related:** [MOD-01-PHASE1-Foundations.md](MOD-01-PHASE1-Foundations.md), [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md)

---

## Objective
Implement a robust **Storage Integrity Watchdog** that monitors WAV files and JSON sidecars, detects corruption, repairs incomplete WAV headers, and automatically synchronizes/re-indexes disk files with Room SQLite.

---

## Sub-Plans
- [MOD-08a-Storage-Watchdog-AutoRepair-RoomReindex.md](MOD-08/MOD-08a-Storage-Watchdog-AutoRepair-RoomReindex.md) — File watcher, header fixer & projection sync
