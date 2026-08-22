package com.example.recme

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.recme.di.appModule
import com.example.recme.storage.StorageManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application entry point setting up notification channels, Koin DI, and startup storage integrity repairs.
 */
class RecMeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin Dependency Injection
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@RecMeApplication)
            modules(appModule)
        }

        createNotificationChannels()

        // Run crash recovery on background files on startup (ADR-0002)
        Thread {
            try {
                val storageManager = StorageManager(this)
                storageManager.repairCorruptRecordings()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Active Recording Channel
            val recordingChannel = NotificationChannel(
                CHANNEL_RECORDING_SERVICE,
                "VAD Recording Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status of background voice activity listening"
                setShowBadge(false)
            }

            // Boot & System Prompts Channel
            val promptChannel = NotificationChannel(
                CHANNEL_SYSTEM_PROMPTS,
                "System Prompts & Recovery",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts to resume recording after device reboot"
            }

            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(promptChannel)
        }
    }

    companion object {
        const val CHANNEL_RECORDING_SERVICE = "recme_recording_service_channel"
        const val CHANNEL_SYSTEM_PROMPTS = "recme_system_prompts_channel"
    }
}
