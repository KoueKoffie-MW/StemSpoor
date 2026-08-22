package com.example.recme.ai.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.recme.service.VadRecordingService
import java.util.concurrent.TimeUnit

/**
 * Schedules background batch and manual transcription tasks via WorkManager.
 */
object TranscriptionScheduler {
    private const val TAG = "TranscriptionScheduler"
    private const val PERIODIC_WORK_NAME = "recme_periodic_transcription"

    /**
     * Schedules periodic charging-constrained batch transcription.
     */
    fun schedulePeriodicChargingTranscription(context: Context) {
        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(TranscriptionWorker.KEY_AUTO_TRANSCRIBE_CHARGING, true)
        if (!isEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(false)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<TranscriptionWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Log.i(TAG, "Enqueued periodic charging transcription worker (every 6h when charging)")
    }

    /**
     * Triggers an immediate one-off transcription for a specific recording file.
     */
    fun triggerImmediateTranscription(context: Context, audioFileName: String) {
        TranscriptionQueue.enqueue(context, audioFileName)
        Log.i(TAG, "Enqueued immediate transcription for: $audioFileName")
    }
}
