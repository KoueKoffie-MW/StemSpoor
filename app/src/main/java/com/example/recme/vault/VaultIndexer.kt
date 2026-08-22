package com.example.recme.vault

import android.content.Context
import android.util.Log
import com.example.recme.ai.embedding.TextEmbeddingEngine
import com.example.recme.data.db.dao.VaultIndexDao
import com.example.recme.data.db.entity.VaultIndexEntity
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.SpeechSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Indexes recordings, Markdown daily notes, and topics into the Room SQLite vector store (MOD-05).
 */
class VaultIndexer(
    private val context: Context,
    private val vaultIndexDao: VaultIndexDao,
    private val textEmbeddingEngine: TextEmbeddingEngine
) {

    /**
     * Indexes all segments and transcripts of a finalized recording.
     */
    suspend fun indexRecording(
        recordingItem: RecordingItem,
        segments: List<SpeechSegmentData>
    ) = withContext(Dispatchers.IO) {
        val recordingId = recordingItem.audioFile.nameWithoutExtension
        val entries = mutableListOf<VaultIndexEntity>()

        for (seg in segments) {
            val text = seg.polishedText ?: seg.rawText ?: continue
            if (text.isBlank()) continue

            val embedding = textEmbeddingEngine.extractEmbedding(text)
            val embeddingBytes = textEmbeddingEngine.floatArrayToByteArray(embedding)
            val contentHash = md5(text)
            val entryId = "${recordingId}_seg_${seg.segmentIndex}"

            entries.add(
                VaultIndexEntity(
                    id = entryId,
                    type = "segment",
                    contentHash = contentHash,
                    textSnippet = text,
                    embedding = embeddingBytes,
                    date = recordingItem.sidecarData?.startedAtEpochMs?.let { formatDate(it) },
                    speakerIds = listOfNotNull(seg.speaker),
                    recordingId = recordingId,
                    segmentId = entryId,
                    lastIndexed = System.currentTimeMillis()
                )
            )
        }

        if (entries.isNotEmpty()) {
            vaultIndexDao.insertAll(entries)
            Log.i(TAG, "Indexed ${entries.size} segments for recording: $recordingId")
        }
    }

    /**
     * Indexes a Markdown note from the Obsidian vault.
     */
    suspend fun indexVaultNote(noteFile: File) = withContext(Dispatchers.IO) {
        if (!noteFile.exists() || !noteFile.isFile) return@withContext

        val text = noteFile.readText(Charsets.UTF_8)
        if (text.isBlank()) return@withContext

        val embedding = textEmbeddingEngine.extractEmbedding(text)
        val embeddingBytes = textEmbeddingEngine.floatArrayToByteArray(embedding)
        val contentHash = md5(text)
        val noteId = "note_${noteFile.nameWithoutExtension}"

        val entry = VaultIndexEntity(
            id = noteId,
            type = if (noteFile.parentFile?.name == "daily") "daily_summary" else "topic",
            contentHash = contentHash,
            textSnippet = text.take(500),
            embedding = embeddingBytes,
            date = if (noteFile.parentFile?.name == "daily") noteFile.nameWithoutExtension else null,
            speakerIds = emptyList(),
            recordingId = null,
            segmentId = null,
            lastIndexed = System.currentTimeMillis()
        )

        vaultIndexDao.insertOrUpdate(entry)
        Log.i(TAG, "Indexed vault note: ${noteFile.name}")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun formatDate(epochMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date(epochMs))
    }

    companion object {
        private const val TAG = "VaultIndexer"
    }
}
