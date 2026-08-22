package com.example.recme.data.repository

import com.example.recme.data.db.dao.SpeakerProfileDao
import com.example.recme.data.db.entity.SpeakerProfileEntity
import com.example.recme.domain.model.SpeakerDomain
import com.example.recme.domain.repository.SpeakerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SpeakerRepositoryImpl(
    private val speakerProfileDao: SpeakerProfileDao
) : SpeakerRepository {

    override fun getAllProfilesFlow(): Flow<List<SpeakerDomain>> {
        return speakerProfileDao.getAllProfilesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllProfiles(): List<SpeakerDomain> {
        return speakerProfileDao.getAllProfiles().map { it.toDomain() }
    }

    override suspend fun getProfileById(id: String): SpeakerDomain? {
        return speakerProfileDao.getProfileById(id)?.toDomain()
    }

    override suspend fun getAllowedProfilesSortedByPriority(): List<SpeakerDomain> {
        return speakerProfileDao.getAllowedProfilesSortedByPriority().map { it.toDomain() }
    }

    override suspend fun saveProfile(profile: SpeakerDomain) {
        speakerProfileDao.insertOrUpdate(profile.toEntity())
    }

    override suspend fun updateConsent(id: String, allowed: Boolean, consentNote: String?) {
        val timestamp = if (allowed) System.currentTimeMillis() else null
        speakerProfileDao.updateConsent(id, allowed, timestamp, consentNote)
    }

    override suspend fun updateStats(id: String, additionalMinutes: Double) {
        val existing = speakerProfileDao.getProfileById(id) ?: return
        speakerProfileDao.insertOrUpdate(
            existing.copy(
                estimatedMinutes = existing.estimatedMinutes + additionalMinutes,
                sampleCount = existing.sampleCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteProfile(id: String) {
        speakerProfileDao.deleteById(id)
    }

    private fun SpeakerProfileEntity.toDomain() = SpeakerDomain(
        id = id,
        name = name,
        relationship = relationship,
        colorHex = colorHex,
        allowedToRecord = allowedToRecord,
        consentTimestamp = consentTimestamp,
        consentNote = consentNote,
        gateConfidenceOverride = gateConfidenceOverride,
        expiresAt = expiresAt,
        estimatedMinutes = estimatedMinutes,
        sampleCount = sampleCount,
        lastUpdated = lastUpdated
    )

    private fun SpeakerDomain.toEntity() = SpeakerProfileEntity(
        id = id,
        name = name,
        relationship = relationship,
        colorHex = colorHex,
        allowedToRecord = allowedToRecord,
        consentTimestamp = consentTimestamp,
        consentNote = consentNote,
        gateConfidenceOverride = gateConfidenceOverride,
        expiresAt = expiresAt,
        estimatedMinutes = estimatedMinutes,
        sampleCount = sampleCount,
        lastUpdated = lastUpdated
    )
}
