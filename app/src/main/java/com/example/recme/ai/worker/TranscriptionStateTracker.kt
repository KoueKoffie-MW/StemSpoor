package com.example.recme.ai.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

sealed class TranscriptionStatus {
    data object Idle : TranscriptionStatus()
    data class Queued(val queuePosition: Int) : TranscriptionStatus()
    data class Transcribing(val currentSegment: Int, val totalSegments: Int, val percent: Float) : TranscriptionStatus()
    data class Polishing(val details: String = "Polishing with Gemma...") : TranscriptionStatus()
    data object Completed : TranscriptionStatus()
    data class Failed(val error: String) : TranscriptionStatus()
}

/**
 * Real-time state tracker for ongoing transcription jobs across recordings.
 */
object TranscriptionStateTracker {

    private val states = ConcurrentHashMap<String, TranscriptionStatus>()
    private val _statusFlow = MutableStateFlow<Map<String, TranscriptionStatus>>(emptyMap())
    val statusFlow: StateFlow<Map<String, TranscriptionStatus>> = _statusFlow.asStateFlow()

    fun updateStatus(fileName: String, status: TranscriptionStatus) {
        states[fileName] = status
        _statusFlow.value = HashMap(states)
    }

    fun clearStatus(fileName: String) {
        states.remove(fileName)
        _statusFlow.value = HashMap(states)
    }

    fun getStatus(fileName: String): TranscriptionStatus {
        return states[fileName] ?: TranscriptionStatus.Idle
    }
}
