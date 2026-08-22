package com.example.recme.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.recme.storage.SidecarData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * State model representing the active playback session and dual-timeline synchronization.
 */
data class PlaybackState(
    val isLoaded: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val realWorldEpochMs: Long = 0L,
    val activeSegmentIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val fileName: String = ""
)

/**
 * Audio playback engine using Media3 ExoPlayer with sub-millisecond timeline interpolation.
 * Connects the condensed audio playhead to real-world timestamps (ADR-0005).
 */
class DualTimelinePlayer(context: Context) : AutoCloseable {

    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private var sidecarData: SidecarData? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(
                        totalDurationMs = dur,
                        isLoaded = true
                    )
                } else if (state == Player.STATE_ENDED) {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = exoPlayer.duration.coerceAtLeast(0L)
                    )
                    stopProgressTracker()
                }
            }
        })
    }

    /**
     * Loads a target WAV file and its companion sidecar metadata into the player.
     */
    fun loadRecording(wavFile: File, sidecar: SidecarData?) {
        sidecarData = sidecar
        val mediaItem = MediaItem.fromUri(wavFile.toURI().toString())
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        _playbackState.value = PlaybackState(
            isLoaded = true,
            isPlaying = false,
            currentPositionMs = 0L,
            totalDurationMs = 0L,
            realWorldEpochMs = getRealWorldTimestampAt(0L),
            activeSegmentIndex = getActiveSegmentIndexAt(0L),
            playbackSpeed = exoPlayer.playbackParameters.speed,
            fileName = wavFile.name
        )
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(clamped)
        updatePositionMetrics(clamped)
    }

    fun seekRelative(offsetMs: Long) {
        val target = (exoPlayer.currentPosition + offsetMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
    }

    /**
     * Interpolates the real-world wall-clock Epoch timestamp corresponding to the audio playhead.
     */
    fun getRealWorldTimestampAt(playheadMs: Long): Long {
        val data = sidecarData ?: return 0L
        if (data.segments.isEmpty()) return data.startedAtEpochMs + playheadMs

        for (seg in data.segments) {
            if (playheadMs in seg.audioStartMs..seg.audioEndMs) {
                val offsetInSegment = playheadMs - seg.audioStartMs
                return (seg.speechStartEpochMs - seg.preRollMs) + offsetInSegment
            }
        }

        // If outside segments, approximate from closest segment or session start
        val firstSeg = data.segments.first()
        if (playheadMs < firstSeg.audioStartMs) {
            return data.startedAtEpochMs + playheadMs
        }
        val lastSeg = data.segments.last()
        val offsetFromLast = playheadMs - lastSeg.audioEndMs
        return lastSeg.speechEndEpochMs + lastSeg.postRollMs + offsetFromLast
    }

    /**
     * Finds the index of the speech segment active at the current playhead position.
     */
    fun getActiveSegmentIndexAt(playheadMs: Long): Int {
        val data = sidecarData ?: return -1
        return data.segments.indexOfFirst { playheadMs in it.audioStartMs..it.audioEndMs }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updatePositionMetrics(exoPlayer.currentPosition)
                delay(50) // 20 FPS UI refresh for smooth scrubbing
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
        updatePositionMetrics(exoPlayer.currentPosition)
    }

    private fun updatePositionMetrics(positionMs: Long) {
        val epochMs = getRealWorldTimestampAt(positionMs)
        val segIdx = getActiveSegmentIndexAt(positionMs)
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = positionMs,
            realWorldEpochMs = epochMs,
            activeSegmentIndex = segIdx
        )
    }

    override fun close() {
        stopProgressTracker()
        exoPlayer.release()
    }
}
