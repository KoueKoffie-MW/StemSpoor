package com.example.recme.audio

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Watchdog that monitors phone call interruptions and coordinates microphone pause / auto-resume (MOD-07).
 */
class CallInterruptionWatchdog(
    private val context: Context,
    private val onCallStarted: () -> Unit,
    private val onCallEnded: () -> Unit
) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var isCallInProgress = false

    private val telephonyCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallState(state)
            }
        }
    } else null

    @Suppress("DEPRECATION")
    private val phoneStateListener = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallState(state)
            }
        }
    } else null

    /**
     * Starts listening for phone call interruptions.
     */
    fun startWatching() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager?.registerTelephonyCallback(context.mainExecutor, it)
                }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.i(TAG, "CallInterruptionWatchdog registered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register CallInterruptionWatchdog (permissions or telephony unavailable)", e)
        }
    }

    /**
     * Stops listening for phone call interruptions.
     */
    fun stopWatching() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
            Log.i(TAG, "CallInterruptionWatchdog unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister CallInterruptionWatchdog", e)
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isCallInProgress) {
                    isCallInProgress = true
                    Log.i(TAG, "Phone call detected (state: $state). Pausing audio capture.")
                    onCallStarted()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isCallInProgress) {
                    isCallInProgress = false
                    Log.i(TAG, "Phone call ended (state: $state). Resuming audio capture.")
                    onCallEnded()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CallInterruptionWatchdog"
    }
}
