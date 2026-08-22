package com.example.recme.ai.worker

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

/**
 * Singleton managing strict single-concurrency FIFO transcription execution.
 * Prevents multiple AI models (Whisper / Gemma) from loading concurrently and causing Out-Of-Memory aborts.
 */
object TranscriptionQueue {
    private const val TAG = "TranscriptionQueue"

    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    // Thread-safe ordered queue of waiting audio file names
    private val pendingQueue = Collections.synchronizedList(mutableListOf<String>())
    
    // Active processing state
    @Volatile
    private var activeFileName: String? = null
    private var activeJob: Job? = null

    private val _queueSizeFlow = MutableStateFlow(0)
    val queueSizeFlow: StateFlow<Int> = _queueSizeFlow.asStateFlow()

    /**
     * Enqueues a single audio file for transcription.
     */
    fun enqueue(context: Context, audioFileName: String) {
        Log.i(TAG, "enqueue() called for: $audioFileName")
        queueScope.launch {
            mutex.withLock {
                val isJobRunning = activeJob?.isActive == true
                if (!isJobRunning) {
                    activeFileName = null
                    activeJob = null
                }

                if (isJobRunning && activeFileName == audioFileName) {
                    Log.i(TAG, "File is currently active, restarting worker for: $audioFileName")
                    activeJob?.cancel()
                    activeFileName = audioFileName
                    startWorker(context, audioFileName)
                    return@withLock
                }

                if (!isJobRunning) {
                    // Start processing immediately
                    activeFileName = audioFileName
                    startWorker(context, audioFileName)
                } else {
                    // Place in queue
                    if (!pendingQueue.contains(audioFileName)) {
                        pendingQueue.add(audioFileName)
                    }
                    val pos = pendingQueue.size
                    _queueSizeFlow.value = pendingQueue.size
                    TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Queued(pos))
                    Log.i(TAG, "Enqueued $audioFileName at position #$pos (Total in queue: ${pendingQueue.size})")
                }
            }
        }
    }

    /**
     * Enqueues a batch of audio files sequentially.
     */
    fun enqueueAll(context: Context, audioFileNames: List<String>) {
        queueScope.launch {
            mutex.withLock {
                val isJobRunning = activeJob?.isActive == true
                if (!isJobRunning) {
                    activeFileName = null
                    activeJob = null
                }

                var triggeredInitial = false
                for (name in audioFileNames) {
                    if (isJobRunning && (activeFileName == name || pendingQueue.contains(name))) {
                        continue
                    }

                    if (!isJobRunning && !triggeredInitial) {
                        activeFileName = name
                        startWorker(context, name)
                        triggeredInitial = true
                    } else {
                        if (!pendingQueue.contains(name)) {
                            pendingQueue.add(name)
                        }
                        val pos = pendingQueue.size
                        TranscriptionStateTracker.updateStatus(name, TranscriptionStatus.Queued(pos))
                    }
                }
                _queueSizeFlow.value = pendingQueue.size
                Log.i(TAG, "Batch enqueued ${audioFileNames.size} files. Queue depth: ${pendingQueue.size}")
            }
        }
    }

    /**
     * Cancels an active or queued transcription.
     */
    fun cancel(audioFileName: String) {
        queueScope.launch {
            mutex.withLock {
                if (activeFileName == audioFileName) {
                    Log.i(TAG, "Cancelling actively running transcription: $audioFileName")
                    activeJob?.cancel()
                    activeJob = null
                    activeFileName = null
                    TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Idle)
                    
                    // Immediately trigger next waiting file if any
                    if (pendingQueue.isNotEmpty()) {
                        val next = pendingQueue.removeAt(0)
                        activeFileName = next
                        _queueSizeFlow.value = pendingQueue.size
                        recalculateQueuePositions()
                        startWorker(context = null ?: return@withLock, next)
                    }
                } else {
                    val wasRemoved = pendingQueue.remove(audioFileName)
                    TranscriptionStateTracker.updateStatus(audioFileName, TranscriptionStatus.Idle)
                    if (wasRemoved) {
                        recalculateQueuePositions()
                        _queueSizeFlow.value = pendingQueue.size
                        Log.i(TAG, "Removed $audioFileName from pending queue. Remaining: ${pendingQueue.size}")
                    }
                }
            }
        }
    }

    /**
     * Starts the sequential queue worker coroutine.
     */
    private fun startWorker(context: Context, firstFile: String) {
        activeJob = queueScope.launch {
            var currentFile: String? = firstFile
            try {
                while (currentFile != null) {
                    try {
                        Log.i(TAG, "Processing next file from queue: $currentFile")
                        TranscriptionRunner.executeTranscriptionDirect(context.applicationContext, currentFile!!)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.i(TAG, "Worker job cancelled for: $currentFile")
                        TranscriptionStateTracker.updateStatus(currentFile!!, TranscriptionStatus.Idle)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed execution for file: $currentFile", e)
                        TranscriptionStateTracker.updateStatus(
                            currentFile!!,
                            TranscriptionStatus.Failed(e.message ?: "Transcription error")
                        )
                    }

                    // Move to next in queue
                    mutex.withLock {
                        if (pendingQueue.isNotEmpty()) {
                            currentFile = pendingQueue.removeAt(0)
                            activeFileName = currentFile
                            _queueSizeFlow.value = pendingQueue.size
                            recalculateQueuePositions()
                        } else {
                            currentFile = null
                            activeFileName = null
                            activeJob = null
                            _queueSizeFlow.value = 0
                            Log.i(TAG, "Transcription queue is now empty. Worker idle.")
                        }
                    }
                }
            } finally {
                mutex.withLock {
                    activeFileName = null
                    activeJob = null
                    _queueSizeFlow.value = pendingQueue.size
                }
            }
        }
    }

    /**
     * Recalculates and broadcasts updated queue positions to all waiting files.
     */
    private fun recalculateQueuePositions() {
        for (i in 0 until pendingQueue.size) {
            val fileName = pendingQueue[i]
            val pos = i + 1
            TranscriptionStateTracker.updateStatus(fileName, TranscriptionStatus.Queued(pos))
        }
    }
}
