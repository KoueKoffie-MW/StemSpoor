package com.example.recme.domain.repository

import com.example.recme.domain.model.FilterStats
import com.example.recme.domain.model.GateDecision

interface GateAuditRepository {
    suspend fun logDecision(decision: GateDecision)
    suspend fun getFilterStats(sinceTimestamp: Long): FilterStats
}
