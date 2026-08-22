package com.example.recme.vault

import android.content.Context
import android.util.Log
import com.example.recme.ai.embedding.TextEmbeddingEngine
import com.example.recme.data.db.dao.VaultIndexDao
import com.example.recme.data.db.entity.VaultIndexEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Search hit item returned by hybrid search.
 */
data class VaultSearchResult(
    val entityId: String,
    val type: String,
    val textSnippet: String,
    val date: String?,
    val speaker: String?,
    val recordingId: String?,
    val relevanceScore: Float, // 0.0 to 1.0 (100%)
    val matchType: SearchMatchType
)

enum class SearchMatchType {
    EXACT_KEYWORD,
    SEMANTIC_CONCEPT,
    HYBRID_MATCH
}

/**
 * Hybrid Semantic + Lexical Search Engine for StemSpoor Vault (MOD-05).
 * Combines SQLite keyword search with dense 384-dimensional vector cosine similarity.
 */
class VaultSearchEngine(
    private val context: Context,
    private val vaultIndexDao: VaultIndexDao,
    private val textEmbeddingEngine: TextEmbeddingEngine
) {

    /**
     * Executes hybrid search across all indexed transcripts and vault notes.
     */
    suspend fun search(
        query: String,
        limit: Int = 30
    ): List<VaultSearchResult> = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Lexical search via SQLite keyword matching
        val lexicalHits = try {
            vaultIndexDao.searchKeyword(cleanQuery, limit = 50)
        } catch (e: Exception) {
            Log.w(TAG, "Lexical search query failed", e)
            emptyList()
        }

        // 2. Semantic vector search via 384-d Cosine Similarity
        val queryEmbedding = textEmbeddingEngine.extractEmbedding(cleanQuery)
        val allVectorEntries = try {
            vaultIndexDao.getAllWithEmbeddings()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load vector entries from DB", e)
            emptyList()
        }

        val semanticScores = mutableMapOf<String, Float>()
        for (entry in allVectorEntries) {
            val bytes = entry.embedding ?: continue
            val vec = textEmbeddingEngine.byteArrayToFloatArray(bytes)
            if (vec.isNotEmpty()) {
                val sim = textEmbeddingEngine.computeCosineSimilarity(queryEmbedding, vec)
                if (sim >= 0.40f) { // Relevance threshold
                    semanticScores[entry.id] = sim
                }
            }
        }

        // 3. Score Fusion: RRF + Linear blend (0.6 * Semantic + 0.4 * Lexical)
        val allHitIds = (lexicalHits.map { it.id } + semanticScores.keys).distinct()
        val allEntitiesMap = (lexicalHits + allVectorEntries).associateBy { it.id }

        val results = mutableListOf<VaultSearchResult>()

        for (id in allHitIds) {
            val entity = allEntitiesMap[id] ?: continue
            val isLexical = lexicalHits.any { it.id == id }
            val semScore = semanticScores[id] ?: 0.0f

            val finalScore = when {
                isLexical && semScore > 0.40f -> (0.5f * 1.0f + 0.5f * semScore).coerceIn(0.0f, 1.0f)
                isLexical -> 0.75f
                else -> semScore
            }

            val matchType = when {
                isLexical && semScore > 0.40f -> SearchMatchType.HYBRID_MATCH
                isLexical -> SearchMatchType.EXACT_KEYWORD
                else -> SearchMatchType.SEMANTIC_CONCEPT
            }

            results.add(
                VaultSearchResult(
                    entityId = entity.id,
                    type = entity.type,
                    textSnippet = entity.textSnippet,
                    date = entity.date,
                    speaker = entity.speakerIds.firstOrNull(),
                    recordingId = entity.recordingId,
                    relevanceScore = finalScore,
                    matchType = matchType
                )
            )
        }

        return@withContext results.sortedByDescending { it.relevanceScore }.take(limit)
    }

    companion object {
        private const val TAG = "VaultSearchEngine"
    }
}
