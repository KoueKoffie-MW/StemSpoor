package com.example.recme.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.recme.R

/**
 * Quick Settings Tile Service enabling 1-tap toggle of StemSpoor VAD recording directly from Android status shade (MOD-07).
 */
class StemSpoorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = VadRecordingService.isServiceRunning.value

        if (isRunning) {
            val stopIntent = Intent(this, VadRecordingService::class.java).apply {
                action = VadRecordingService.ACTION_STOP
            }
            startService(stopIntent)
            Log.i(TAG, "Quick Settings Tile: Stopping recording service")
        } else {
            val startIntent = Intent(this, VadRecordingService::class.java).apply {
                action = VadRecordingService.ACTION_START
            }
            ContextCompat.startForegroundService(this, startIntent)
            Log.i(TAG, "Quick Settings Tile: Starting recording service")
        }

        // Slight delay to allow service lifecycle transition
        qsTile?.let { tile ->
            tile.state = if (isRunning) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = VadRecordingService.isServiceRunning.value

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRunning) "StemSpoor Active" else "StemSpoor Mic"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_mic)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "Recording active" else "Tap to record"
        }

        tile.updateTile()
    }

    companion object {
        private const val TAG = "StemSpoorTileService"
    }
}
