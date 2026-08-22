package com.example.recme.data.repository

import com.example.recme.data.db.dao.GateAuditDao
import com.example.recme.data.db.entity.GateAuditEntity
import com.example.recme.domain.model.FilterStats
import com.example.recme.domain.model.GateDecision
import com.example.recme.domain.repository.GateAuditRepository

class GateAuditRepositoryImpl(
    private val gateAuditDao: GateAuditDao
) : GateAuditRepository {

    override suspend fun logDecision(decision: GateDecision) {
        gateAuditDao.insert(
            GateAuditEntity(
                timestamp = System.currentTimeMillis(),
                decision = decision.decisionType,
                profileId = decision.matchedProfileId,
                confidence = decision.confidence,
                reason = decision.reason,
                durationMs = decision.durationMs
            )
        )
    }

    override suspend fun getFilterStats(sinceTimestamp: Long): FilterStats {
        val total = gateAuditDao.getTotalDecisionsCount(sinceTimestamp)
        val allowed = gateAuditDao.getAllowedDecisionsCount(sinceTimestamp)
        val denied = gateAuditDao.getDeniedDecisionsCount(sinceTimestamp)
        return FilterStats(
            totalDecisions = total,
            allowedCount = allowed,
            deniedCount = denied
        )
    }
}
