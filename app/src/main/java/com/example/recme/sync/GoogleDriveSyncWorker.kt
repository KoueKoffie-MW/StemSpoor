package com.example.recme.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.StorageManager
import com.example.recme.vault.VaultManager
import java.io.File

/**
 * Background WorkManager worker that uploads pending recordings and the entire Obsidian Vault to Google Drive.
 */
class GoogleDriveSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting comprehensive Google Drive Vault sync job...")

        val authManager = GoogleDriveAuthManager(applicationContext)
        val account = authManager.getSignedInAccount()
        if (account == null) {
            Log.w(TAG, "Sync aborted: No Google Account signed in with drive.file permissions")
            return Result.failure()
        }

        val prefs = applicationContext.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val deleteAfterUpload = prefs.getBoolean(KEY_DELETE_AFTER_UPLOAD, false)

        val storageManager = StorageManager(applicationContext)
        val recordings = storageManager.listRecordings()
        val vaultManager = VaultManager(applicationContext)

        val driveService = GoogleDriveService(applicationContext, account)
        var uploadedCount = 0
        var failedCount = 0

        // 1. Sync Audio Recordings, Sidecars, and Companion Notes
        for (item in recordings) {
            if (item.audioFile.exists() && item.audioFile.length() > 44) {
                try {
                    val mdFile = File(item.audioFile.parentFile, "${item.baseName}.md").takeIf { it.exists() }
                    Log.i(TAG, "Syncing recording group: ${item.audioFile.name} (mdExists=${mdFile != null})...")

                    val (audioDriveId, _) = driveService.uploadRecordingGroup(
                        audioFile = item.audioFile,
                        jsonFile = item.jsonFile,
                        mdFile = mdFile
                    )

                    if (audioDriveId.isNotEmpty()) {
                        uploadedCount++
                        if (deleteAfterUpload) {
                            item.audioFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync recording ${item.audioFile.name}", e)
                    failedCount++
                }
            }
        }

        // 2. Sync Daily Notes in Vault (vault/daily/)
        if (vaultManager.dailyDir.exists()) {
            val dailyNotes = vaultManager.dailyDir.listFiles { _, name -> name.endsWith(".md", ignoreCase = true) } ?: emptyArray()
            for (note in dailyNotes) {
                try {
                    driveService.uploadVaultNote(note, "daily")
                    uploadedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync daily note ${note.name}", e)
                }
            }
        }

        // 3. Sync Topic Notes in Vault (vault/topics/)
        if (vaultManager.topicsDir.exists()) {
            val topicNotes = vaultManager.topicsDir.listFiles { _, name -> name.endsWith(".md", ignoreCase = true) } ?: emptyArray()
            for (note in topicNotes) {
                try {
                    driveService.uploadVaultNote(note, "topics")
                    uploadedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync topic note ${note.name}", e)
                }
            }
        }

        Log.i(TAG, "Google Drive sync completed: $uploadedCount synced items, $failedCount failed")
        return if (failedCount == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "GoogleDriveSyncWorker"
        const val KEY_DELETE_AFTER_UPLOAD = "key_delete_after_upload"
        const val KEY_WIFI_ONLY = "key_wifi_only"
        const val KEY_AUTO_SYNC_ENABLED = "key_auto_sync_enabled"
    }
}
