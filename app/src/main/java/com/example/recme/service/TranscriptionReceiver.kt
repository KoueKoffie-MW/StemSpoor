package com.example.recme.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.recme.ai.gemini.GeminiAudioTranscriber
import com.example.recme.ai.worker.TranscriptionRunner

class TranscriptionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRANSCRIBE = "com.example.recme.TRANSCRIBE"
        const val EXTRA_FILE = "file"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_MODEL = "model"
        private const val TAG = "TranscriptionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TRANSCRIBE) {
            val fileName = intent.getStringExtra(EXTRA_FILE) ?: intent.getStringExtra("file_name")
            val engine = intent.getStringExtra(EXTRA_ENGINE)
            val model = intent.getStringExtra(EXTRA_MODEL)

            val apiKey = intent.getStringExtra("api_key")

            Log.i(TAG, "Received TRANSCRIBE broadcast: file=$fileName, engine=$engine, model=$model")

            val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (!engine.isNullOrBlank()) {
                editor.putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, engine)
            }
            if (!model.isNullOrBlank()) {
                editor.putString(GeminiAudioTranscriber.KEY_GEMINI_MODEL_ID, model)
            }
            if (!apiKey.isNullOrBlank()) {
                editor.putString(GeminiAudioTranscriber.KEY_GEMINI_API_KEY, apiKey)
            }
            editor.commit()

            if (!fileName.isNullOrBlank()) {
                TranscriptionRunner.startTranscription(context, fileName)
            }
        }
    }
}
