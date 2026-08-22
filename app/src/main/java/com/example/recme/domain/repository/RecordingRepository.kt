package com.example.recme.domain.repository

import com.example.recme.domain.model.Recording
import com.example.recme.domain.model.SpeechSegment
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    fun getAllRecordingsFlow(): Flow<List<Recording>>
    suspend fun getAllRecordings(): List<Recording>
    suspend fun getRecordingById(id: String): Recording?
    suspend fun getUnprocessedRecordings(): List<Recording>
    suspend fun saveRecording(recording: Recording)
    suspend fun markRecordingProcessed(id: String, isProcessed: Boolean = true)
    suspend fun deleteRecording(id: String)

    // Segment operations
    fun getSegmentsFlow(recordingId: String): Flow<List<SpeechSegment>>
    suspend fun getSegments(recordingId: String): List<SpeechSegment>
    suspend fun saveSegments(segments: List<SpeechSegment>)
    suspend fun getRecentDiscards(limit: Int = 50): List<SpeechSegment>
}
