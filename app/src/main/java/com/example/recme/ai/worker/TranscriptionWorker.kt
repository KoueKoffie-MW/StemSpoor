package com.example.recme.ai.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.recme.ai.gemma.GemmaPostProcessor
import com.example.recme.ai.models.AIModelType
import com.example.recme.ai.models.ModelDownloadManager
import com.example.recme.ai.whisper.WhisperEngine
import com.example.recme.ai.whisper.WhisperLanguageConfig
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.StorageManager
import com.example.recme.storage.TranscriptExporter
import com.example.recme.ai.speaker.SpeakerDiarizationEngine
import com.example.recme.ai.transcription.TranscriptionManager
import com.example.recme.sync.SyncScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background WorkManager task that transcribes pending recordings using dual local and cloud engines.
 */
class TranscriptionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val diarizationEngine: SpeakerDiarizationEngine by inject()
    private val transcriptionManager: TranscriptionManager by inject()

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting transcription job (${transcriptionManager.engineMode.displayName})...")

        val storageManager = StorageManager(applicationContext)
        val recordings = storageManager.listRecordings()
        val targetFileName = inputData.getString(EXTRA_FILE_NAME)

        val pendingRecordings = recordings.filter { item ->
            if (targetFileName != null) {
                item.audioFile.name == targetFileName
            } else {
                item.sidecarData != null && !item.sidecarData.isTranscribed
            }
        }

        if (pendingRecordings.isEmpty()) {
            Log.i(TAG, "No pending recordings to transcribe.")
            return Result.success()
        }

        try {
            for (item in pendingRecordings) {
                val sidecar = item.sidecarData ?: continue
                val jsonFile = item.jsonFile ?: continue
                val audioFileName = item.audioFile.name

                // 0. Run Speaker Diarization to attribute speakers and refine voiceprints
                try {
                    diarizationEngine.diarizeRecording(item.audioFile, jsonFile)
                } catch (e: Exception) {
                    Log.w(TAG, "Speaker diarization encountered non-fatal error on $audioFileName", e)
                }

                Log.i(TAG, "Transcribing: $audioFileName (${sidecar.segments.size} segments)...")
                TranscriptionStateTracker.updateStatus(
                    audioFileName,
                    TranscriptionStatus.Transcribing(0, sidecar.segments.size, 0.05f)
                )

                // 1. Run Dual-Engine Transcription (Local SenseVoice/Whisper or Cloud Gemini)
                val polishedSegments = transcriptionManager.transcribeRecording(
                    item.audioFile,
                    sidecar.segments
                ) { cur, total ->
                    val pct = if (total > 0) cur.toFloat() / total.toFloat() else 0.5f
                    TranscriptionStateTracker.updateStatus(
                        audioFileName,
                        TranscriptionStatus.Transcribing(cur, total, pct)
                    )
                }

                val languagesDetected = polishedSegments.mapNotNull { it.detectedLanguage }.distinct()

                // 2. Update sidecar JSON
                val updatedSidecar = TranscriptExporter.updateSidecarJson(jsonFile, polishedSegments, languagesDetected)

                // 3. Export Obsidian Markdown Note (.md) and update Vault
                if (updatedSidecar != null) {
                    val vaultManager = com.example.recme.vault.VaultManager(applicationContext)
                    TranscriptExporter.exportToObsidianMarkdown(item.audioFile, updatedSidecar)
                    TranscriptExporter.exportToObsidianMarkdown(item.audioFile, updatedSidecar, vaultManager.recordingsDir)
                    vaultManager.upsertRecordingToDailyNote(item, polishedSegments)
                }

                TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Completed)

                // 4. Trigger Google Drive Cloud Sync to upload .md and updated .json
                SyncScheduler.scheduleImmediateSync(applicationContext)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed with exception", e)
            val errFile = targetFileName ?: pendingRecordings.firstOrNull()?.audioFile?.name
            if (errFile != null) {
                TranscriptionStateTracker.updateStatus(
                    errFile,
                    TranscriptionStatus.Failed(e.message ?: "Transcription failed")
                )
            }
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "TranscriptionWorker"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val KEY_ACTIVE_LANGUAGES = "key_active_languages"
        const val KEY_AUTO_TRANSCRIBE_CHARGING = "key_auto_transcribe_charging"
    }
}
