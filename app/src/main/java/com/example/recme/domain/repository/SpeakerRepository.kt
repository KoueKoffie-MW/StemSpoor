package com.example.recme.domain.repository

import com.example.recme.domain.model.SpeakerDomain
import kotlinx.coroutines.flow.Flow

interface SpeakerRepository {
    fun getAllProfilesFlow(): Flow<List<SpeakerDomain>>
    suspend fun getAllProfiles(): List<SpeakerDomain>
    suspend fun getProfileById(id: String): SpeakerDomain?
    suspend fun getAllowedProfilesSortedByPriority(): List<SpeakerDomain>
    suspend fun saveProfile(profile: SpeakerDomain)
    suspend fun updateConsent(id: String, allowed: Boolean, consentNote: String?)
    suspend fun updateStats(id: String, additionalMinutes: Double)
    suspend fun deleteProfile(id: String)
}
