package com.example.recme.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.recme.MainActivity
import com.example.recme.R
import com.example.recme.RecMeApplication
import com.example.recme.audio.AudioCaptureEngine
import com.example.recme.audio.AudioConstants
import com.example.recme.audio.AudioEngineState
import com.example.recme.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service running 24/7 background audio monitoring with Silero VAD.
 */
class VadRecordingService : Service() {

    private var audioCaptureEngine: AudioCaptureEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    private var lastRecordedDurationMs: Long = 0L
    private var lastIsSpeech: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RecMe::VadRecordingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val threshold = intent.getFloatExtra(EXTRA_THRESHOLD, AudioConstants.DEFAULT_VAD_THRESHOLD)
                startRecordingService(threshold)
            }
            ACTION_STOP -> {
                stopRecordingService()
            }
            ACTION_UPDATE_SENSITIVITY -> {
                val threshold = intent.getFloatExtra(EXTRA_THRESHOLD, AudioConstants.DEFAULT_VAD_THRESHOLD)
                audioCaptureEngine?.setSensitivity(threshold)
            }
            else -> {
                startRecordingService(AudioConstants.DEFAULT_VAD_THRESHOLD)
            }
        }
        return START_STICKY
    }

    private fun startRecordingService(threshold: Float) {
        if (_isServiceRunning.value) return

        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h safety timeout

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isOpusEnabled = prefs.getBoolean(KEY_OPUS_COMPRESSION, true)
        val splitSizeMb = prefs.getFloat(KEY_SPLIT_SIZE_MB, AudioConstants.DEFAULT_MAX_FILE_SIZE_MB)
        val splitSizeBytes = (splitSizeMb * 1024L * 1024L).toLong()

        val storageManager = StorageManager(this)
        val engine = AudioCaptureEngine(this, storageManager.getRecordingsDirectory())
        audioCaptureEngine = engine

        val initialNotification = buildNotification(isSpeech = false, durationMs = 0L, fileName = "")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            engine.startCapture(
                threshold = threshold,
                maxFileSizeBytes = splitSizeBytes,
                isOpusCompressionEnabled = isOpusEnabled
            )
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        _isServiceRunning.value = true
        saveRecordingState(true)

        // Observe engine state for notifications & UI
        stateObserverJob = scope.launch {
            engine.engineState.collect { state ->
                _engineState.value = state
                if (state.isSpeechDetected != lastIsSpeech || (state.recordedDurationMs - lastRecordedDurationMs) >= 5000L) {
                    lastIsSpeech = state.isSpeechDetected
                    lastRecordedDurationMs = state.recordedDurationMs
                    updateNotification(state.isSpeechDetected, state.recordedDurationMs, state.activeFileName)
                }
            }
        }
    }

    private fun stopRecordingService() {
        stateObserverJob?.cancel()
        stateObserverJob = null

        audioCaptureEngine?.stopCapture()
        audioCaptureEngine?.close()
        audioCaptureEngine = null

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        _isServiceRunning.value = false
        _engineState.value = AudioEngineState()
        saveRecordingState(false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(isSpeech: Boolean, durationMs: Long, fileName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VadRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusTitle = if (isSpeech) "🔴 RecMe: Recording Speech" else "🟢 RecMe: Listening (Silent)"
        val durationSec = durationMs / 1000
        val durationText = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
        val contentText = if (isSpeech) "Active: $fileName ($durationText)" else "Silero VAD active • Discarding silence"

        return NotificationCompat.Builder(this, RecMeApplication.CHANNEL_RECORDING_SERVICE)
            .setContentTitle(statusTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(isSpeech: Boolean, durationMs: Long, fileName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(isSpeech, durationMs, fileName)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun saveRecordingState(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
    }

    override fun onDestroy() {
        stopRecordingService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.recme.action.START"
        const val ACTION_STOP = "com.example.recme.action.STOP"
        const val ACTION_UPDATE_SENSITIVITY = "com.example.recme.action.UPDATE_SENSITIVITY"
        const val EXTRA_THRESHOLD = "extra_threshold"

        const val PREFS_NAME = "recme_prefs"
        const val KEY_RECORDING_ENABLED = "key_recording_enabled"
        const val KEY_SENSITIVITY = "key_sensitivity"
        const val KEY_OPUS_COMPRESSION = "key_opus_compression"
        const val KEY_SPLIT_SIZE_MB = "key_split_size_mb"

        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _engineState = MutableStateFlow(AudioEngineState())
        val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

        fun start(context: Context, threshold: Float = AudioConstants.DEFAULT_VAD_THRESHOLD) {
            val intent = Intent(context, VadRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_THRESHOLD, threshold)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VadRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
