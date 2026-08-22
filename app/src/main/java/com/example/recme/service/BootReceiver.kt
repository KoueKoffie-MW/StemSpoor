package com.example.recme.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.recme.MainActivity
import com.example.recme.RecMeApplication

/**
 * Handles device boot completion and posts an interactive notification to resume background VAD recording.
 * Compliant with Android 14+ background microphone initiation limits (ADR-0004).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
            val wasRecordingEnabled = prefs.getBoolean(VadRecordingService.KEY_RECORDING_ENABLED, false)

            if (wasRecordingEnabled) {
                postResumePromptNotification(context)
            }
        }
    }

    private fun postResumePromptNotification(context: Context) {
        val resumeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RecMeApplication.CHANNEL_SYSTEM_PROMPTS)
            .setContentTitle("RecMe: Device Restarted")
            .setContentText("Tap here to resume background speech recording")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_PROMPT_ID, notification)
    }

    companion object {
        const val EXTRA_AUTO_START = "extra_auto_start"
        private const val NOTIFICATION_PROMPT_ID = 2001
    }
}
