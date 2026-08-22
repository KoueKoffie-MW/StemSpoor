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
import com.example.recme.sync.SyncScheduler

/**
 * Background WorkManager task that transcribes pending recordings using on-device Whisper & Gemma.
 */
class TranscriptionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting local multilingual transcription job...")

        val downloadManager = ModelDownloadManager(applicationContext)
        val whisperModel = if (downloadManager.isModelReady(AIModelType.WHISPER_LARGE_V3_TURBO)) {
            AIModelType.WHISPER_LARGE_V3_TURBO
        } else if (downloadManager.isModelReady(AIModelType.WHISPER_SMALL_INT8)) {
            AIModelType.WHISPER_SMALL_INT8
        } else {
            Log.w(TAG, "No Whisper ASR model downloaded yet. Skipping transcription.")
            val targetFileName = inputData.getString(EXTRA_FILE_NAME)
            if (targetFileName != null) {
                TranscriptionStateTracker.updateStatus(
                    targetFileName,
                    TranscriptionStatus.Failed("No Whisper model downloaded. Please download in Settings.")
                )
            }
            return Result.success()
        }

        val encoderFile = downloadManager.getEncoderFile(whisperModel)
        val decoderFile = downloadManager.getDecoderFile(whisperModel)
        val vocabFile = downloadManager.getVocabFile(whisperModel)
        val prefs = applicationContext.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val languageSet = prefs.getStringSet(KEY_ACTIVE_LANGUAGES, WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet())?.toList()
            ?: WhisperLanguageConfig.DEFAULT_LANGUAGES

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

        var whisperEngine: WhisperEngine? = null
        try {
            whisperEngine = WhisperEngine(
                encoderFile = encoderFile,
                decoderFile = decoderFile,
                vocabFile = vocabFile,
                activeLanguages = languageSet
            )

            for (item in pendingRecordings) {
                val sidecar = item.sidecarData ?: continue
                val jsonFile = item.jsonFile ?: continue
                val audioFileName = item.audioFile.name

                Log.i(TAG, "Transcribing: $audioFileName (${sidecar.segments.size} segments)...")
                TranscriptionStateTracker.updateStatus(
                    audioFileName,
                    TranscriptionStatus.Transcribing(0, sidecar.segments.size, 0.05f)
                )

                val transcribedSegments = whisperEngine.transcribeSegments(
                    item.audioFile,
                    sidecar.segments
                ) { cur, total ->
                    val pct = if (total > 0) cur.toFloat() / total.toFloat() else 0.5f
                    TranscriptionStateTracker.updateStatus(
                        audioFileName,
                        TranscriptionStatus.Transcribing(cur, total, pct)
                    )
                }

                TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Polishing())
                val polishedSegments = GemmaPostProcessor.polishSegments(transcribedSegments)

                val languagesDetected = polishedSegments.mapNotNull { it.detectedLanguage }.distinct()

                // 1. Update sidecar JSON
                val updatedSidecar = TranscriptExporter.updateSidecarJson(jsonFile, polishedSegments, languagesDetected)

                // 2. Export Obsidian Markdown Note (.md) and update Vault
                if (updatedSidecar != null) {
                    val vaultManager = com.example.recme.vault.VaultManager(applicationContext)
                    TranscriptExporter.exportToObsidianMarkdown(item.audioFile, updatedSidecar)
                    TranscriptExporter.exportToObsidianMarkdown(item.audioFile, updatedSidecar, vaultManager.recordingsDir)
                    vaultManager.upsertRecordingToDailyNote(item, polishedSegments)
                }

                TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Completed)

                // 3. Trigger Google Drive Cloud Sync to upload .md and updated .json
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
        } finally {
            whisperEngine?.close()
        }
    }

    companion object {
        private const val TAG = "TranscriptionWorker"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val KEY_ACTIVE_LANGUAGES = "key_active_languages"
        const val KEY_AUTO_TRANSCRIBE_CHARGING = "key_auto_transcribe_charging"
    }
}
