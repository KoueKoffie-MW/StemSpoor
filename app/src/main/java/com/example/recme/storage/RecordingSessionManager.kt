package com.example.recme.storage

import com.example.recme.audio.AudioConstants
import com.example.recme.audio.VadStateListener
import com.example.recme.audio.WavAudioWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Coordinates WAV streaming and JSON sidecar writes, implementing smart file splitting on silence
 * and midnight date rollover.
 */
class RecordingSessionManager(
    private val storageDir: File,
    private val maxFileSizeBytes: Long = AudioConstants.DEFAULT_MAX_FILE_SIZE_BYTES,
    private val onSessionRoll: (newWavFile: File) -> Unit = {},
    var onPartFinalized: ((wavFile: File, jsonFile: File?) -> Unit)? = null
) : VadStateListener, AutoCloseable {

    private val sessionId: String = UUID.randomUUID().toString()
    private val sessionDateFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
    private val dayDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    private var baseTimestampStr: String = ""
    private var activeDayStr: String = ""
    private var partIndex: Int = 1

    private var wavWriter: WavAudioWriter? = null
    private var sidecarWriter: SidecarMetadataWriter? = null

    private var currentSegmentIndex: Int = 0
    private var currentSegmentAudioStartMs: Long = 0L
    private var currentSpeechStartEpochMs: Long = 0L
    private var currentPreRollMs: Long = 0L

    private var isSplitPending: Boolean = false
    private var isInitialized: Boolean = false

    var currentWavFile: File? = null
        private set
    var currentJsonFile: File? = null
        private set

    /**
     * Lazy initialization of the first session part upon first speech trigger.
     */
    private fun ensureSessionStarted(startEpochMs: Long) {
        if (isInitialized) return
        val date = Date(startEpochMs)
        baseTimestampStr = sessionDateFormat.format(date)
        activeDayStr = dayDateFormat.format(date)
        openNewPart(startEpochMs)
        isInitialized = true
    }

    private fun openNewPart(startEpochMs: Long) {
        wavWriter?.close()

        val partStr = String.format(Locale.US, "Part%03d", partIndex)
        val wavName = "$baseTimestampStr-$partStr.wav"
        val jsonName = "$baseTimestampStr-$partStr.json"

        val wavFile = File(storageDir, wavName)
        val jsonFile = File(storageDir, jsonName)

        val writer = WavAudioWriter()
        writer.open(wavFile)

        val sidecar = SidecarMetadataWriter(
            targetJsonFile = jsonFile,
            wavFileName = wavName,
            sessionId = sessionId,
            startedAtEpochMs = startEpochMs
        )

        wavWriter = writer
        sidecarWriter = sidecar
        currentWavFile = wavFile
        currentJsonFile = jsonFile
        currentSegmentIndex = 0

        onSessionRoll(wavFile)
    }

    override fun onSegmentStarted(
        preRollFrames: List<ShortArray>,
        speechStartEpochMs: Long,
        preRollMs: Long
    ) {
        ensureSessionStarted(speechStartEpochMs)
        val writer = wavWriter ?: return

        val bytesPerMs = (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE) / 1000L
        currentSegmentAudioStartMs = writer.totalPcmBytesWritten / bytesPerMs
        currentSpeechStartEpochMs = speechStartEpochMs
        currentPreRollMs = preRollMs

        // Write pre-roll frames
        for (frame in preRollFrames) {
            writer.writeSamples(frame)
        }
    }

    override fun onFrameToRecord(frame: ShortArray) {
        val writer = wavWriter ?: return
        writer.writeSamples(frame)

        // Check configurable soft limit threshold
        if (writer.totalPcmBytesWritten >= maxFileSizeBytes) {
            isSplitPending = true
        }
    }

    override fun onSegmentEnded(speechEndEpochMs: Long, postRollMs: Long) {
        val writer = wavWriter ?: return
        val sidecar = sidecarWriter ?: return

        val bytesPerMs = (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE) / 1000L
        val audioEndMs = writer.totalPcmBytesWritten / bytesPerMs

        val segment = SpeechSegmentData(
            segmentIndex = currentSegmentIndex++,
            audioStartMs = currentSegmentAudioStartMs,
            audioEndMs = audioEndMs,
            speechStartEpochMs = currentSpeechStartEpochMs,
            speechEndEpochMs = speechEndEpochMs,
            preRollMs = currentPreRollMs,
            postRollMs = postRollMs
        )

        sidecar.addSegment(segment)
        writer.flushHeader()

        // Check for Midnight Day Rollover
        val todayStr = dayDateFormat.format(Date(speechEndEpochMs))
        val isMidnightRollover = activeDayStr.isNotEmpty() && todayStr != activeDayStr

        // Smart Splitting or Midnight Rollover on silence
        if (isSplitPending || isMidnightRollover) {
            val completedWav = currentWavFile
            val completedJson = currentJsonFile

            writer.close()
            wavWriter = null

            if (isMidnightRollover) {
                // New calendar day: reset session base timestamp & part index to 1
                val newDate = Date(speechEndEpochMs)
                baseTimestampStr = sessionDateFormat.format(newDate)
                activeDayStr = todayStr
                partIndex = 1
            } else {
                partIndex++
            }

            isSplitPending = false
            openNewPart(System.currentTimeMillis())

            if (completedWav != null && completedWav.length() > 44) {
                onPartFinalized?.invoke(completedWav, completedJson)
            }
        }
    }

    override fun onSilenceTimeout() {
        // Handled by audio engine to reset VAD state tensors
    }

    fun getTotalPcmBytes(): Long = wavWriter?.totalPcmBytesWritten ?: 0L

    override fun close() {
        val completedWav = currentWavFile
        val completedJson = currentJsonFile

        wavWriter?.close()
        wavWriter = null
        sidecarWriter = null

        if (completedWav != null && completedWav.length() > 44) {
            onPartFinalized?.invoke(completedWav, completedJson)
        }
    }
}
