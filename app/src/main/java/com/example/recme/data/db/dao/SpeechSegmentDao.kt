package com.example.recme.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recme.data.db.entity.SpeechSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeechSegmentDao {

    @Query("SELECT * FROM speech_segments WHERE recordingId = :recordingId ORDER BY startTimeWallMs ASC")
    fun getSegmentsForRecordingFlow(recordingId: String): Flow<List<SpeechSegmentEntity>>

    @Query("SELECT * FROM speech_segments WHERE recordingId = :recordingId ORDER BY startTimeWallMs ASC")
    suspend fun getSegmentsForRecording(recordingId: String): List<SpeechSegmentEntity>

    @Query("SELECT * FROM speech_segments WHERE speakerId = :speakerId ORDER BY startTimeWallMs DESC")
    suspend fun getSegmentsForSpeaker(speakerId: String): List<SpeechSegmentEntity>

    @Query("SELECT * FROM speech_segments WHERE dailyNoteDate = :date ORDER BY startTimeWallMs ASC")
    suspend fun getSegmentsForDate(date: String): List<SpeechSegmentEntity>

    @Query("SELECT * FROM speech_segments WHERE gateDecision LIKE 'DENIED%' ORDER BY startTimeWallMs DESC LIMIT :limit")
    suspend fun getRecentDiscards(limit: Int = 50): List<SpeechSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(segment: SpeechSegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SpeechSegmentEntity>)

    @Query("DELETE FROM speech_segments WHERE recordingId = :recordingId")
    suspend fun deleteByRecordingId(recordingId: String)
}
