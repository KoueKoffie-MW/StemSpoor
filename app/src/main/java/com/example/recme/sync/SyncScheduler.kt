package com.example.recme.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.recme.service.VadRecordingService

/**
 * Schedules background Google Drive upload tasks with network constraints via Android WorkManager.
 */
object SyncScheduler {
    private const val TAG = "SyncScheduler"
    private const val UNIQUE_WORK_NAME = "recme_google_drive_sync"

    /**
     * Schedules an immediate one-time sync job respecting the user's Wi-Fi network constraint.
     */
    fun scheduleImmediateSync(context: Context) {
        val authManager = GoogleDriveAuthManager(context)
        if (!authManager.isSignedIn()) {
            Log.d(TAG, "Sync skipped: User is not signed in to Google Drive")
            return
        }

        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoSyncEnabled = prefs.getBoolean(GoogleDriveSyncWorker.KEY_AUTO_SYNC_ENABLED, true)
        if (!isAutoSyncEnabled) {
            Log.d(TAG, "Sync skipped: Auto-sync is disabled by user in Settings")
            return
        }

        val isWifiOnly = prefs.getBoolean(GoogleDriveSyncWorker.KEY_WIFI_ONLY, true)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
        Log.i(TAG, "Enqueued background Google Drive sync (Wi-Fi only: $isWifiOnly)")
    }

    /**
     * Triggers an immediate manual synchronization when the user taps 'Sync Now' in UI.
     */
    fun triggerManualSyncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.i(TAG, "Triggered manual Google Drive sync now")
    }
}
