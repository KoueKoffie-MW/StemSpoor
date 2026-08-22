package com.example.recme.storage

import android.content.Context
import android.util.Log
import com.example.recme.data.bootstrap.DatabaseBootstrapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result metrics returned by StorageIntegrityWatchdog scan and repair.
 */
data class IntegrityReport(
    val totalFilesScanned: Int,
    val headersRepaired: Int,
    val sidecarsRecovered: Int,
    val orphansCleaned: Int,
    val roomRecordsSynced: Int
)

/**
 * Storage Integrity Watchdog & WAV Auto-Repair Service conforming to MOD-08.
 * Inspects all audio recordings and sidecars on disk, detects abnormal termination damage,
 * repairs corrupt WAV RIFF headers in-place, regenerates missing JSON sidecars,
 * and verifies Room SQLite database consistency against authoritative disk files.
 */
class StorageIntegrityWatchdog(
    private val context: Context,
    private val storageManager: StorageManager,
    private val bootstrapManager: DatabaseBootstrapManager
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Executes full storage integrity audit, repairs damaged headers, and resyncs Room database.
     */
    suspend fun auditAndRepairStorage(): IntegrityReport = withContext(Dispatchers.IO) {
        val recordingsDir = storageManager.getRecordingsDirectory()
        if (!recordingsDir.exists()) {
            return@withContext IntegrityReport(0, 0, 0, 0, 0)
        }

        val allFiles = recordingsDir.listFiles() ?: emptyArray()
        var totalScanned = 0
        var headersRepaired = 0
        var sidecarsRecovered = 0
        var orphansCleaned = 0

        // 1. Inspect and repair WAV files
        val wavFiles = allFiles.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
        for (wavFile in wavFiles) {
            totalScanned++
            if (repairWavHeaderIfNeeded(wavFile)) {
                headersRepaired++
            }

            // Check if companion JSON sidecar exists
            val sidecarFile = File(recordingsDir, "${wavFile.nameWithoutExtension}.json")
            if (!sidecarFile.exists() && wavFile.length() > 44) {
                if (generateBaselineSidecar(wavFile, sidecarFile)) {
                    sidecarsRecovered++
                }
            }
        }

        // 2. Clean up abandoned temporary files (> 2 hours old)
        val now = System.currentTimeMillis()
        val tempFiles = allFiles.filter {
            it.isFile && (it.extension.equals("tmp", ignoreCase = true) || it.name.endsWith(".part"))
        }
        for (tempFile in tempFiles) {
            if (now - tempFile.lastModified() > 2 * 60 * 60 * 1000L) {
                if (tempFile.delete()) {
                    orphansCleaned++
                    Log.i(TAG, "Cleaned abandoned orphan file: ${tempFile.name}")
                }
            }
        }

        // 3. Resync Room SQLite projection against authoritative disk files
        val syncedCount = try {
            bootstrapManager.bootstrapFromDisk()
        } catch (e: Exception) {
            Log.w(TAG, "Room sync during integrity audit failed", e)
            0
        }

        Log.i(TAG, "Storage integrity audit completed: Scanned=$totalScanned, HeadersRepaired=$headersRepaired, SidecarsRecovered=$sidecarsRecovered, OrphansCleaned=$orphansCleaned, RoomSynced=$syncedCount")

        return@withContext IntegrityReport(
            totalFilesScanned = totalScanned,
            headersRepaired = headersRepaired,
            sidecarsRecovered = sidecarsRecovered,
            orphansCleaned = orphansCleaned,
            roomRecordsSynced = syncedCount
        )
    }

    /**
     * Inspects WAV RIFF header and repairs ChunkSize & Subchunk2Size in-place if mismatched.
     */
    private fun repairWavHeaderIfNeeded(wavFile: File): Boolean {
        val fileLength = wavFile.length()
        if (fileLength < 44) return false

        try {
            RandomAccessFile(wavFile, "rw").use { raf ->
                val headerBytes = ByteArray(44)
                raf.readFully(headerBytes)

                val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

                // Validate RIFF and WAVE signatures
                val riffTag = String(headerBytes, 0, 4, Charsets.US_ASCII)
                val waveTag = String(headerBytes, 8, 4, Charsets.US_ASCII)
                val fmtTag  = String(headerBytes, 12, 4, Charsets.US_ASCII)

                var needsRepair = false

                if (riffTag != "RIFF" || waveTag != "WAVE" || fmtTag != "fmt ") {
                    Log.w(TAG, "Reconstructing missing/corrupt RIFF header for: ${wavFile.name}")
                    // Write standard 16kHz mono 16-bit PCM header
                    raf.seek(0)
                    val correctedHeader = createPcmWavHeader(fileLength - 44)
                    raf.write(correctedHeader)
                    return true
                }

                // Check ChunkSize (bytes 4..7) and DataSize (bytes 40..43)
                buffer.position(4)
                val declaredChunkSize = buffer.int.toLong() and 0xFFFFFFFFL

                buffer.position(40)
                val declaredDataSize = buffer.int.toLong() and 0xFFFFFFFFL

                val expectedDataSize = fileLength - 44
                val expectedChunkSize = fileLength - 8

                if (declaredDataSize != expectedDataSize || declaredChunkSize != expectedChunkSize) {
                    Log.i(TAG, "Repairing WAV header sizes in ${wavFile.name}: DeclaredData=$declaredDataSize, ExpectedData=$expectedDataSize")

                    // Rewrite ChunkSize
                    raf.seek(4)
                    val sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    sizeBuffer.putInt(expectedChunkSize.toInt())
                    raf.write(sizeBuffer.array())

                    // Rewrite DataSize
                    raf.seek(40)
                    sizeBuffer.clear()
                    sizeBuffer.putInt(expectedDataSize.toInt())
                    raf.write(sizeBuffer.array())

                    needsRepair = true
                }

                return needsRepair
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting/repairing WAV file: ${wavFile.name}", e)
            return false
        }
    }

    /**
     * Generates a baseline SidecarData companion JSON for a recovered WAV file.
     */
    private fun generateBaselineSidecar(wavFile: File, sidecarFile: File): Boolean {
        try {
            val audioLengthBytes = wavFile.length() - 44
            val sampleRate = 16000
            val bytesPerSec = sampleRate * 2 // 16-bit mono = 2 bytes/sample
            val durationMs = if (bytesPerSec > 0) (audioLengthBytes * 1000L) / bytesPerSec else 0L

            val startTimeMs = wavFile.lastModified() - durationMs

            val sidecar = SidecarData(
                version = 1,
                fileName = wavFile.name,
                sampleRateHz = 16000,
                channels = 1,
                bitDepth = 16,
                recordingSessionId = "recovered_${wavFile.nameWithoutExtension}",
                startedAtEpochMs = startTimeMs,
                isTranscribed = false,
                languagesDetected = emptyList(),
                segments = listOf(
                    SpeechSegmentData(
                        segmentIndex = 0,
                        audioStartMs = 0L,
                        audioEndMs = durationMs,
                        speechStartEpochMs = startTimeMs,
                        speechEndEpochMs = startTimeMs + durationMs,
                        preRollMs = 0L,
                        postRollMs = 0L,
                        detectedLanguage = null,
                        rawText = null,
                        polishedText = null,
                        speaker = "Speaker 1"
                    )
                )
            )

            sidecarFile.writeText(json.encodeToString(sidecar), Charsets.UTF_8)
            Log.i(TAG, "Generated baseline sidecar for: ${wavFile.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate baseline sidecar for ${wavFile.name}", e)
            return false
        }
    }

    private fun createPcmWavHeader(dataLength: Long): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = 16000
        val channels = 1
        val byteRate = sampleRate * channels * 2

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((dataLength + 36).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1.toShort()) // AudioFormat (1 = PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataLength.toInt())

        return header.array()
    }

    companion object {
        private const val TAG = "StorageWatchdog"
    }
}
