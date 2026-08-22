package com.example.recme.ai.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.recme.R
import com.example.recme.ai.gemma.GemmaLlamaEngine
import com.example.recme.ai.gemma.GemmaPostProcessor
import com.example.recme.ai.models.AIModelType
import com.example.recme.ai.models.ModelDownloadManager
import com.example.recme.ai.whisper.WhisperEngine
import com.example.recme.ai.whisper.WhisperLanguageConfig
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.RecordingItem
import java.util.Date
import com.example.recme.storage.StorageManager
import com.example.recme.storage.TranscriptExporter
import com.example.recme.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-priority on-demand transcription runner with wake lock and real-time state reporting.
 */
object TranscriptionRunner {
    private const val TAG = "TranscriptionRunner"
    private const val NOTIFICATION_CHANNEL_ID = "recme_transcription_channel"
    private const val NOTIFICATION_ID = 2002

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "RecMe AI Transcription",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live on-device speech transcription progress"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun startTranscription(context: Context, audioFileName: String) {
        TranscriptionQueue.enqueue(context, audioFileName)
    }

    suspend fun executeTranscriptionDirect(context: Context, audioFileName: String) {
        createNotificationChannel(context)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RecMe:TranscriptionWakeLock").apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }

        try {
            processTranscription(context, audioFileName, notifManager)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "Transcription cancelled for $audioFileName")
            TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Idle)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing $audioFileName", e)
            TranscriptionStateTracker.updateStatus(
                audioFileName,
                TranscriptionStatus.Failed(e.message ?: "Transcription failed")
            )
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            notifManager.cancel(NOTIFICATION_ID)
        }
    }

    private suspend fun processTranscription(
        context: Context,
        audioFileName: String,
        notifManager: NotificationManager
    ) = withContext(Dispatchers.Default) {
        val downloadManager = ModelDownloadManager(context)
        val geminiTranscriber = com.example.recme.ai.gemini.GeminiAudioTranscriber(context)
        val isGeminiActive = geminiTranscriber.isEnabled()

        var modelType: AIModelType? = null
        var encoderFile: java.io.File? = null
        var decoderFile: java.io.File? = null
        var vocabFile: java.io.File? = null

        if (!isGeminiActive) {
            // Select available on-device model
            modelType = if (downloadManager.isModelReady(AIModelType.WHISPER_LARGE_V3_TURBO)) {
                AIModelType.WHISPER_LARGE_V3_TURBO
            } else if (downloadManager.isModelReady(AIModelType.WHISPER_SMALL_INT8)) {
                AIModelType.WHISPER_SMALL_INT8
            } else if (downloadManager.isModelReady(AIModelType.GEMMA_4_E2B_INT4)) {
                AIModelType.GEMMA_4_E2B_INT4
            } else {
                TranscriptionStateTracker.updateStatus(
                    audioFileName,
                    TranscriptionStatus.Failed("No on-device ASR model ready. Please download in Settings or enable Gemini Cloud.")
                )
                return@withContext
            }

            encoderFile = downloadManager.getEncoderFile(modelType)
            decoderFile = downloadManager.getDecoderFile(modelType)
            vocabFile = downloadManager.getVocabFile(modelType)
        }

        val storageManager = StorageManager(context)
        val recordings = storageManager.listRecordings()
        val item = recordings.firstOrNull {
            it.audioFile.name.equals(audioFileName, ignoreCase = true) ||
            it.baseName.equals(java.io.File(audioFileName).nameWithoutExtension, ignoreCase = true)
        }

        val targetAudioFile = item?.audioFile ?: java.io.File(storageManager.getRecordingsDirectory(), audioFileName)
        if (!targetAudioFile.exists()) {
            TranscriptionStateTracker.updateStatus(
                audioFileName,
                TranscriptionStatus.Failed("Audio file not found: $audioFileName")
            )
            return@withContext
        }

        val (jsonFile, sidecar) = if (item?.sidecarData != null && item.jsonFile != null) {
            Pair(item.jsonFile, item.sidecarData)
        } else {
            storageManager.getOrCreateSidecar(targetAudioFile)
        }

        // Pre-merge segments if legacy sidecar has single-word pauses (< 3000ms)
        val mergedSegments = storageManager.mergeAdjacentSegments(sidecar.segments, gapThresholdMs = 3000L)
        val totalSegments = mergedSegments.size

        if (totalSegments == 0) {
            // No speech recorded -> export empty note immediately
            val updated = TranscriptExporter.updateSidecarJson(jsonFile, emptyList(), emptyList())
            if (updated != null) TranscriptExporter.exportToObsidianMarkdown(targetAudioFile, updated)
            TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Completed)
            return@withContext
        }

        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val languageSet = prefs.getStringSet(
            TranscriptionWorker.KEY_ACTIVE_LANGUAGES,
            WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet()
        )?.toList() ?: WhisperLanguageConfig.DEFAULT_LANGUAGES

        // Always execute transcription fresh for both initial transcribe and re-transcribe
        val transcribedSegments = run {
            val geminiTranscriber = com.example.recme.ai.gemini.GeminiAudioTranscriber(context)
            if (geminiTranscriber.isEnabled()) {
                updateNotification(context, notifManager, "Transcribing ${targetAudioFile.name}", "Transcribing with Gemini Cloud AI...", 0, totalSegments)
                TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Transcribing(0, totalSegments, 0.1f))

                geminiTranscriber.transcribeSegments(
                    targetAudioFile,
                    mergedSegments
                ) { current, total ->
                    val progressPct = if (total > 0) current.toFloat() / total.toFloat() else 0.5f
                    updateNotification(
                        context,
                        notifManager,
                        "Transcribing ${targetAudioFile.name}",
                        "Segment $current of $total (${(progressPct * 100).toInt()}%)",
                        current,
                        total
                    )
                    TranscriptionStateTracker.updateStatus(
                        audioFileName,
                        TranscriptionStatus.Transcribing(current, total, progressPct)
                    )
                }
            } else {
                var whisperEngine: WhisperEngine? = null
                try {
                    whisperEngine = WhisperEngine(
                        encoderFile = encoderFile!!,
                        decoderFile = decoderFile!!,
                        vocabFile = vocabFile!!,
                        activeLanguages = languageSet
                    )

                    whisperEngine.transcribeSegments(
                        targetAudioFile,
                        mergedSegments
                    ) { current, total ->
                        val progressPct = if (total > 0) current.toFloat() / total.toFloat() else 0.5f
                        updateNotification(
                            context,
                            notifManager,
                            "Transcribing $audioFileName",
                            "Segment $current of $total (${(progressPct * 100).toInt()}%)",
                            current,
                            total
                        )
                        TranscriptionStateTracker.updateStatus(
                            audioFileName,
                            TranscriptionStatus.Transcribing(current, total, progressPct)
                        )
                    }
                } finally {
                    whisperEngine?.close()
                }
            }
        }

        // 2. Stage 2: Post-Processing & Multilingual Refinement
        var gemmaEngine: GemmaLlamaEngine? = null
        val polishedSegments = if (isGeminiActive) {
            // Gemini Cloud already performed end-to-end verbatim transcription and polishing
            transcribedSegments
        } else {
            TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Polishing())
            updateNotification(context, notifManager, "Polishing Transcript", "Refining multilingual text with Gemma LLM...", 90, 100)

            val gemmaFile = downloadManager.getModelFile(AIModelType.GEMMA_4_E2B_INT4).takeIf { it.exists() && it.length() > 100_000_000L }
                ?: downloadManager.getModelFile(AIModelType.GEMMA_4_E4B_INT4).takeIf { it.exists() && it.length() > 100_000_000L }

            if (gemmaFile != null) {
                val engine = GemmaLlamaEngine(context)
                val loaded = engine.loadModel(gemmaFile)
                if (loaded) {
                    gemmaEngine = engine
                } else {
                    engine.release()
                }
            }

            val promptConfig = com.example.recme.ai.config.PromptConfigManager(context)
            try {
                GemmaPostProcessor.polishSegments(transcribedSegments, gemmaEngine, promptConfig)
            } catch (e: Exception) {
                Log.e(TAG, "Gemma post-processing failed, fallback to raw", e)
                transcribedSegments
            }
        }

        // 3. Stage 3: Hybrid Speaker Identification & Continuous Learning
        val speakerIdentifier = com.example.recme.ai.speaker.HybridSpeakerIdentifier(context)
        val finalSegmentsWithSpeakers = try {
            polishedSegments.map { seg ->
                val pcmBytes = com.example.recme.audio.AudioChunkExtractor.extractPcmChunk(targetAudioFile, seg.audioStartMs, seg.audioEndMs)
                val floatSamples = if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                    val sampleCount = pcmBytes.size / 2
                    FloatArray(sampleCount) { i ->
                        val low = pcmBytes[i * 2].toInt() and 0xFF
                        val high = pcmBytes[i * 2 + 1].toInt()
                        val shortVal = (high shl 8) or low
                        shortVal / 32768.0f
                    }
                } else null

                if (!seg.speaker.isNullOrBlank()) {
                    // Speaker pre-identified by Gemini -> adapt on-device acoustic voiceprint
                    if (floatSamples != null && floatSamples.size >= 16000) {
                        val profileManager = com.example.recme.ai.speaker.SpeakerProfileManager(context)
                        val embeddingEngine = com.example.recme.ai.speaker.SpeakerEmbeddingEngine(context)
                        try {
                            val emb = embeddingEngine.extractEmbedding(floatSamples)
                            profileManager.adaptProfileCentroid(seg.speaker, emb, seg.detectedLanguage, alpha = 0.20f)
                            Log.i(TAG, "Adapted acoustic centroid from Gemini speaker tag '${seg.speaker}' (Lang: ${seg.detectedLanguage})")
                        } finally {
                            embeddingEngine.close()
                        }
                    }
                    seg
                } else if (floatSamples != null) {
                    val result = speakerIdentifier.identifySpeaker(
                        pcmSamples = floatSamples,
                        segmentText = seg.polishedText ?: seg.rawText,
                        spokenLanguage = seg.detectedLanguage
                    )

                    if (result.speaker != null) {
                        Log.i(TAG, "Identified speaker for segment ${seg.segmentIndex}: '${result.speaker}' (Confidence: ${(result.confidence * 100).toInt()}%)")
                        seg.copy(speaker = result.speaker, speakerConfidence = result.confidence)
                    } else {
                        seg
                    }
                } else {
                    seg
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Speaker identification failed, retaining segments", e)
            polishedSegments
        } finally {
            speakerIdentifier.close()
        }

        val languagesDetected = finalSegmentsWithSpeakers.mapNotNull { it.detectedLanguage }.distinct()

        // 4. Update sidecar JSON
        val updatedSidecar = TranscriptExporter.updateSidecarJson(jsonFile, finalSegmentsWithSpeakers, languagesDetected)

        // 5. Export Obsidian Markdown Note (.md) and optionally update Daily Journal in Vault
        if (updatedSidecar != null) {
            val vaultManager = com.example.recme.vault.VaultManager(context)
            TranscriptExporter.exportToObsidianMarkdown(targetAudioFile, updatedSidecar)
            
            if (vaultManager.isAutoSyncEnabled) {
                TranscriptExporter.exportToObsidianMarkdown(targetAudioFile, updatedSidecar, vaultManager.recordingsDir)
                
                val activeItem = item ?: RecordingItem(
                    baseName = targetAudioFile.nameWithoutExtension,
                    audioFile = targetAudioFile,
                    jsonFile = jsonFile,
                    fileSizeBytes = targetAudioFile.length(),
                    totalAudioDurationMs = updatedSidecar.segments.lastOrNull()?.audioEndMs ?: 0L,
                    lastModifiedEpochMs = targetAudioFile.lastModified(),
                    sidecarData = updatedSidecar
                )
                vaultManager.upsertRecordingToDailyNote(activeItem, finalSegmentsWithSpeakers)

                val texts = polishedSegments.mapNotNull { it.polishedText ?: it.rawText }
                if (texts.isNotEmpty()) {
                    if (isGeminiActive) {
                        try {
                            val geminiTranscriber = com.example.recme.ai.gemini.GeminiAudioTranscriber(context)
                            val (summary, actions) = geminiTranscriber.generateSummaryAndActions(texts)
                            if (summary.isNotBlank()) {
                                vaultManager.updateDailySummaryAndActions(vaultManager.getRecordingDate(activeItem), summary, actions)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to generate daily summary with Gemini", e)
                        }
                    } else if (gemmaEngine != null) {
                        try {
                            val (summary, actions) = gemmaEngine.generateDailySummary(texts)
                            vaultManager.updateDailySummaryAndActions(vaultManager.getRecordingDate(activeItem), summary, actions)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to generate daily summary with Gemma", e)
                        }
                    }
                }
            }
        }

        gemmaEngine?.release()

        TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Completed)
        Log.i(TAG, "Completed transcription & Gemma refinement for $audioFileName")

        // 5. Trigger Google Drive Cloud Sync to upload .md and updated .json
        SyncScheduler.scheduleImmediateSync(context)
    }

    private fun updateNotification(
        context: Context,
        notifManager: NotificationManager,
        title: String,
        text: String,
        progress: Int,
        max: Int
    ) {
        val notif = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(max, progress, max == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notifManager.notify(NOTIFICATION_ID, notif)
    }
}
