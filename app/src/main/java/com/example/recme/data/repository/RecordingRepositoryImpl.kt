package com.example.recme.data.repository

import com.example.recme.data.db.dao.RecordingDao
import com.example.recme.data.db.dao.SpeechSegmentDao
import com.example.recme.data.db.entity.RecordingEntity
import com.example.recme.data.db.entity.SpeechSegmentEntity
import com.example.recme.domain.model.Recording
import com.example.recme.domain.model.SpeechSegment
import com.example.recme.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordingRepositoryImpl(
    private val recordingDao: RecordingDao,
    private val speechSegmentDao: SpeechSegmentDao
) : RecordingRepository {

    override fun getAllRecordingsFlow(): Flow<List<Recording>> {
        return recordingDao.getAllRecordingsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllRecordings(): List<Recording> {
        return recordingDao.getAllRecordings().map { it.toDomain() }
    }

    override suspend fun getRecordingById(id: String): Recording? {
        return recordingDao.getRecordingById(id)?.toDomain()
    }

    override suspend fun getUnprocessedRecordings(): List<Recording> {
        return recordingDao.getUnprocessedRecordings().map { it.toDomain() }
    }

    override suspend fun saveRecording(recording: Recording) {
        recordingDao.insertOrUpdate(recording.toEntity())
    }

    override suspend fun markRecordingProcessed(id: String, isProcessed: Boolean) {
        val existing = recordingDao.getRecordingById(id) ?: return
        recordingDao.update(existing.copy(isProcessed = isProcessed))
    }

    override suspend fun deleteRecording(id: String) {
        recordingDao.deleteById(id)
        speechSegmentDao.deleteByRecordingId(id)
    }

    override fun getSegmentsFlow(recordingId: String): Flow<List<SpeechSegment>> {
        return speechSegmentDao.getSegmentsForRecordingFlow(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSegments(recordingId: String): List<SpeechSegment> {
        return speechSegmentDao.getSegmentsForRecording(recordingId).map { it.toDomain() }
    }

    override suspend fun saveSegments(segments: List<SpeechSegment>) {
        speechSegmentDao.insertAll(segments.map { it.toEntity() })
    }

    override suspend fun getRecentDiscards(limit: Int): List<SpeechSegment> {
        return speechSegmentDao.getRecentDiscards(limit).map { it.toDomain() }
    }

    private fun RecordingEntity.toDomain() = Recording(
        id = id,
        startTimeWallMs = startTimeWallMs,
        endTimeWallMs = endTimeWallMs,
        durationMs = durationMs,
        sidecarPath = sidecarPath,
        audioPath = audioPath,
        isProcessed = isProcessed,
        createdAt = createdAt
    )

    private fun Recording.toEntity() = RecordingEntity(
        id = id,
        startTimeWallMs = startTimeWallMs,
        endTimeWallMs = endTimeWallMs,
        durationMs = durationMs,
        sidecarPath = sidecarPath,
        audioPath = audioPath,
        isProcessed = isProcessed,
        createdAt = createdAt
    )

    private fun SpeechSegmentEntity.toDomain() = SpeechSegment(
        id = id,
        recordingId = recordingId,
        startTimeWallMs = startTimeWallMs,
        endTimeWallMs = endTimeWallMs,
        durationMs = durationMs,
        speakerId = speakerId,
        gateDecision = gateDecision,
        gateProfileId = gateProfileId,
        gateConfidence = gateConfidence,
        gateReason = gateReason,
        language = language,
        hasTranscript = hasTranscript,
        transcriptText = transcriptText,
        transcriptPath = transcriptPath,
        dailyNoteDate = dailyNoteDate,
        topicLinks = topicLinks,
        createdAt = createdAt
    )

    private fun SpeechSegment.toEntity() = SpeechSegmentEntity(
        id = id,
        recordingId = recordingId,
        startTimeWallMs = startTimeWallMs,
        endTimeWallMs = endTimeWallMs,
        durationMs = durationMs,
        speakerId = speakerId,
        gateDecision = gateDecision,
        gateProfileId = gateProfileId,
        gateConfidence = gateConfidence,
        gateReason = gateReason,
        language = language,
        hasTranscript = hasTranscript,
        transcriptText = transcriptText,
        transcriptPath = transcriptPath,
        dailyNoteDate = dailyNoteDate,
        topicLinks = topicLinks,
        createdAt = createdAt
    )
}
