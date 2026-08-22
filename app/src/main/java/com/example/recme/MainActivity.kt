package com.example.recme

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.recme.audio.AudioConstants
import com.example.recme.service.BootReceiver
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.StorageManager
import com.example.recme.theme.RecMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Repair any corrupt unfinalized headers & compress pending WAV files from previous sessions
        val storageManager = StorageManager(this)
        storageManager.repairCorruptRecordings()
        storageManager.compressPendingWavFiles(lifecycleScope)

        // Handle auto-start intent triggered by BootReceiver prompt
        if (intent?.getBooleanExtra(BootReceiver.EXTRA_AUTO_START, false) == true) {
            val prefs = getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
            val threshold = prefs.getFloat(VadRecordingService.KEY_SENSITIVITY, AudioConstants.DEFAULT_VAD_THRESHOLD)
            VadRecordingService.start(this, threshold)
        }

        handleTranscriptionIntent(intent)

        setContent {
            RecMeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTranscriptionIntent(intent)
    }

    private fun handleTranscriptionIntent(intent: android.content.Intent?) {
        val targetFile = intent?.getStringExtra("transcribe_file") ?: return
        android.util.Log.i("MainActivity", "Received transcribe_file intent: $targetFile")
        com.example.recme.ai.worker.TranscriptionRunner.startTranscription(this, targetFile)
    }
}
