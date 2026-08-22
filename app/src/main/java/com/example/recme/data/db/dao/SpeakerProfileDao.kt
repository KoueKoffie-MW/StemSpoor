package com.example.recme.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recme.data.db.entity.SpeakerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerProfileDao {

    @Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
    fun getAllProfilesFlow(): Flow<List<SpeakerProfileEntity>>

    @Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
    suspend fun getAllProfiles(): List<SpeakerProfileEntity>

    @Query("SELECT * FROM speaker_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): SpeakerProfileEntity?

    @Query("SELECT * FROM speaker_profiles WHERE allowedToRecord = 1 ORDER BY estimatedMinutes DESC")
    suspend fun getAllowedProfilesSortedByPriority(): List<SpeakerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: SpeakerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<SpeakerProfileEntity>)

    @Query("UPDATE speaker_profiles SET allowedToRecord = :allowed, consentTimestamp = :timestamp, consentNote = :note WHERE id = :id")
    suspend fun updateConsent(id: String, allowed: Boolean, timestamp: Long?, note: String?)

    @Query("DELETE FROM speaker_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
