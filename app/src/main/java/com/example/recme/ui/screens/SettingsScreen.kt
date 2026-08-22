package com.example.recme.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recme.ai.models.AIModelType
import com.example.recme.ai.models.ModelDownloadManager
import com.example.recme.ai.models.ModelDownloadState
import com.example.recme.ai.whisper.WhisperLanguageConfig
import com.example.recme.ai.worker.TranscriptionScheduler
import com.example.recme.ai.worker.TranscriptionWorker
import com.example.recme.audio.AudioConstants
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.StorageManager
import com.example.recme.sync.GoogleDriveAuthManager
import com.example.recme.sync.GoogleDriveSyncWorker
import com.example.recme.sync.SyncScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.recme.ai.gemini.GeminiAudioTranscriber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE) }
    val storageManager = remember { StorageManager(context) }
    val authManager = remember { GoogleDriveAuthManager(context) }
    val downloadManager = remember { ModelDownloadManager(context) }
    val geminiTranscriber = remember { GeminiAudioTranscriber(context) }

    val downloadStates by downloadManager.downloadStates.collectAsState()

    var sensitivity by remember {
        mutableFloatStateOf(prefs.getFloat(VadRecordingService.KEY_SENSITIVITY, AudioConstants.DEFAULT_VAD_THRESHOLD))
    }
    var isOpusEnabled by remember {
        mutableStateOf(prefs.getBoolean(VadRecordingService.KEY_OPUS_COMPRESSION, true))
    }
    var splitSizeMb by remember {
        mutableFloatStateOf(prefs.getFloat(VadRecordingService.KEY_SPLIT_SIZE_MB, AudioConstants.DEFAULT_MAX_FILE_SIZE_MB))
    }
    var segmentMergeGapMs by remember {
        mutableLongStateOf(
            prefs.getLong(
                VadRecordingService.KEY_SEGMENT_MERGE_GAP_MS,
                VadRecordingService.DEFAULT_SEGMENT_MERGE_GAP_MS
            )
        )
    }

    // Google Drive Sync State
    var isSignedInToDrive by remember { mutableStateOf(authManager.isSignedIn()) }
    var userEmail by remember { mutableStateOf(authManager.getAccountEmail()) }
    var isAutoSyncEnabled by remember {
        mutableStateOf(prefs.getBoolean(GoogleDriveSyncWorker.KEY_AUTO_SYNC_ENABLED, true))
    }
    var isWifiOnly by remember {
        mutableStateOf(prefs.getBoolean(GoogleDriveSyncWorker.KEY_WIFI_ONLY, true))
    }
    var isDeleteAfterUpload by remember {
        mutableStateOf(prefs.getBoolean(GoogleDriveSyncWorker.KEY_DELETE_AFTER_UPLOAD, false))
    }

    // AI Transcription Engine Selection
    var selectedEngine by remember {
        mutableStateOf(
            prefs.getString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_ON_DEVICE)
                ?: GeminiAudioTranscriber.ENGINE_ON_DEVICE
        )
    }
    var geminiApiKey by remember {
        mutableStateOf(prefs.getString(GeminiAudioTranscriber.KEY_GEMINI_API_KEY, "") ?: "")
    }
    var selectedModelId by remember {
        mutableStateOf(
            prefs.getString(GeminiAudioTranscriber.KEY_GEMINI_MODEL_ID, GeminiAudioTranscriber.DEFAULT_MODEL_ID)
                ?: GeminiAudioTranscriber.DEFAULT_MODEL_ID
        )
    }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isTestingApiKey by remember { mutableStateOf(false) }
    var apiKeyTestResult by remember { mutableStateOf<Result<String>?>(null) }

    // AI Language Whitelist State
    var activeLanguages by remember {
        mutableStateOf(
            prefs.getStringSet(TranscriptionWorker.KEY_ACTIVE_LANGUAGES, WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet()) ?: WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet()
        )
    }
    var isLanguagePickerOpen by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isAutoTranscribeCharging by remember {
        mutableStateOf(prefs.getBoolean(TranscriptionWorker.KEY_AUTO_TRANSCRIBE_CHARGING, true))
    }

    var isIgnoringBattery by remember {
        mutableStateOf(checkBatteryExemption(context))
    }
    var isAllFilesGranted by remember {
        mutableStateOf(checkAllFilesAccess())
    }

    // AI Prompts & Personal Context State
    val promptConfig = remember { com.example.recme.ai.config.PromptConfigManager(context) }
    var userName by remember { mutableStateOf(promptConfig.userName) }
    var userBio by remember { mutableStateOf(promptConfig.userBio) }
    var frequentSpeakers by remember { mutableStateOf(promptConfig.frequentSpeakers) }
    var customVocabulary by remember { mutableStateOf(promptConfig.customVocabulary) }
    var dialectRules by remember { mutableStateOf(promptConfig.dialectRules) }

    var geminiPrompt by remember { mutableStateOf(promptConfig.geminiTranscriptionInstruction) }
    var gemmaPrompt by remember { mutableStateOf(promptConfig.gemmaPolishingPrompt) }
    var summaryPrompt by remember { mutableStateOf(promptConfig.summaryAndActionsPrompt) }
    var askAiPrompt by remember { mutableStateOf(promptConfig.askAiSystemPrompt) }

    var isContextExpanded by remember { mutableStateOf(false) }
    var isPromptsExpanded by remember { mutableStateOf(false) }
    var isSpeakersExpanded by remember { mutableStateOf(true) }

    // Voiceprint & Speaker Profiles State
    val speakerProfileManager = remember { com.example.recme.ai.speaker.SpeakerProfileManager(context) }
    var isSpeakerRecEnabled by remember { mutableStateOf(speakerProfileManager.isSpeakerRecognitionEnabled) }
    var isContinuousLearning by remember { mutableStateOf(speakerProfileManager.isContinuousLearningEnabled) }
    var isVoiceGateEnabled by remember { mutableStateOf(speakerProfileManager.isVoiceGateEnabled) }
    var voiceGateThreshold by remember { mutableFloatStateOf(speakerProfileManager.voiceGateConfidenceThreshold) }
    var speakerThreshold by remember { mutableFloatStateOf(speakerProfileManager.recognitionThreshold) }
    var speakerProfiles by remember { mutableStateOf<List<com.example.recme.ai.speaker.SpeakerProfile>>(emptyList()) }
    var isAddSpeakerDialogOpen by remember { mutableStateOf(false) }
    var newSpeakerName by remember { mutableStateOf("") }
    var newSpeakerRelationship by remember { mutableStateOf("Family") }
    var newSpeakerColor by remember { mutableStateOf("#3B82F6") }
    var newSpeakerLanguage by remember { mutableStateOf("af") }

    // Obsidian Vault & Batch Sync State
    val vaultManager = remember { com.example.recme.vault.VaultManager(context) }
    var isVaultAutoSyncEnabled by remember { mutableStateOf(vaultManager.isAutoSyncEnabled) }
    var isVaultSyncing by remember { mutableStateOf(false) }
    var newSpeakerAliases by remember { mutableStateOf("") }
    var isRecordingSample by remember { mutableStateOf(false) }
    var recordingRemainingSec by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var recordedSampleEmbedding by remember { mutableStateOf<FloatArray?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        speakerProfiles = speakerProfileManager.getProfiles()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            if (account != null) {
                isSignedInToDrive = true
                userEmail = account.email
                Toast.makeText(context, "Connected to Google Drive: ${account.email}", Toast.LENGTH_SHORT).show()
                SyncScheduler.scheduleImmediateSync(context)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transcription Engine Selector Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcription Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Choose whether to transcribe speech using 100% private on-device models or Google Gemini Cloud API.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 1: On-Device Whisper + Gemma
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_ON_DEVICE
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_ON_DEVICE).apply()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEngine == GeminiAudioTranscriber.ENGINE_ON_DEVICE,
                            onClick = {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_ON_DEVICE
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_ON_DEVICE).apply()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("On-Device (Whisper + Gemma 4)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("100% offline, zero data leaves your device", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 2: Google Gemini Cloud ASR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD).apply()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEngine == GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD,
                            onClick = {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD).apply()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Google Gemini Cloud ASR", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Fast verbatim multimodal speech transcription (Requires API Key)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 3: Smart Hybrid (Cloud + Local Fallback)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_SMART_HYBRID
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_SMART_HYBRID).apply()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEngine == GeminiAudioTranscriber.ENGINE_SMART_HYBRID,
                            onClick = {
                                selectedEngine = GeminiAudioTranscriber.ENGINE_SMART_HYBRID
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_TRANSCRIPTION_ENGINE, GeminiAudioTranscriber.ENGINE_SMART_HYBRID).apply()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Smart Hybrid (Cloud + Local Fallback)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Uses Gemini Flash when online; seamlessly falls back to on-device when offline", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Gemini API Key Configuration Section
                    if (selectedEngine == GeminiAudioTranscriber.ENGINE_GEMINI_CLOUD || selectedEngine == GeminiAudioTranscriber.ENGINE_SMART_HYBRID) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Select Gemini Cloud Model:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for ((mId, mLabel) in GeminiAudioTranscriber.SUPPORTED_MODELS) {
                                FilterChip(
                                    selected = selectedModelId == mId,
                                    onClick = {
                                        selectedModelId = mId
                                        apiKeyTestResult = null
                                        prefs.edit().putString(GeminiAudioTranscriber.KEY_GEMINI_MODEL_ID, mId).apply()
                                    },
                                    label = { Text(mLabel, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                apiKeyTestResult = null
                                prefs.edit().putString(GeminiAudioTranscriber.KEY_GEMINI_API_KEY, it.trim()).apply()
                            },
                            label = { Text("Google AI Studio API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isApiKeyVisible) "Hide key" else "Show key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isTestingApiKey = true
                                        apiKeyTestResult = geminiTranscriber.testApiKey(geminiApiKey, selectedModelId)
                                        isTestingApiKey = false
                                    }
                                },
                                enabled = geminiApiKey.isNotBlank() && !isTestingApiKey,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingApiKey) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...")
                                } else {
                                    Text("Test Connection")
                                }
                            }

                            if (geminiApiKey.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        geminiApiKey = ""
                                        apiKeyTestResult = null
                                        prefs.edit().remove(GeminiAudioTranscriber.KEY_GEMINI_API_KEY).apply()
                                    }
                                ) {
                                    Text("Clear Key")
                                }
                            }
                        }

                        apiKeyTestResult?.let { res ->
                            Spacer(modifier = Modifier.height(8.dp))
                            if (res.isSuccess) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(res.getOrNull() ?: "API Key verified", fontSize = 12.sp, color = Color(0xFF43A047), fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(res.exceptionOrNull()?.message ?: "Validation failed", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Local AI Transcription & Multilingual Models Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("On-Device AI Transcription & LLM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "100% offline & private speech recognition via Whisper ASR and Gemma 4 reasoning. Models support resumable streaming download.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Downloadable Models List
                    for (model in AIModelType.entries) {
                        val state = downloadStates[model.id] ?: ModelDownloadState.NotDownloaded
                        val sizeMb = model.sizeBytes / (1024 * 1024)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(model.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    when (state) {
                                        is ModelDownloadState.Ready -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Ready", fontSize = 11.sp, color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { downloadManager.deleteModel(model) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        is ModelDownloadState.Downloading -> {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("${(state.progressPercent * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                if (state.speedMbPerSec > 0.05f) {
                                                    Text(String.format("%.1f MB/s", state.speedMbPerSec), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        is ModelDownloadState.Error -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Failed", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = {
                                                        downloadManager.startDownload(model)
                                                    },
                                                    contentPadding = ButtonDefaults.ContentPadding,
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Resume", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    downloadManager.startDownload(model)
                                                },
                                                contentPadding = ButtonDefaults.ContentPadding,
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Get (~${sizeMb}MB)", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                if (state is ModelDownloadState.Downloading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progressPercent },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val dlMb = state.bytesDownloaded / (1024 * 1024)
                                    val totalMb = state.totalBytes / (1024 * 1024)
                                    Text("$dlMb MB / $totalMb MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (state is ModelDownloadState.Error) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Error: ${state.message}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Configurable Multilingual Whitelist Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Active Code-Switching Languages", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        TextButton(onClick = { isLanguagePickerOpen = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Add More", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Selected languages are identified dynamically during speech:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (code in activeLanguages) {
                            val lang = WhisperLanguageConfig.getLanguageByCode(code)
                            val labelText = if (lang != null) "${lang.code.uppercase()} (${lang.name})" else code.uppercase()

                            FilterChip(
                                selected = true,
                                onClick = {
                                    if (activeLanguages.size > 1) {
                                        val updated = activeLanguages.toMutableSet()
                                        updated.remove(code)
                                        activeLanguages = updated
                                        prefs.edit().putStringSet(TranscriptionWorker.KEY_ACTIVE_LANGUAGES, updated).apply()
                                    } else {
                                        Toast.makeText(context, "At least one language must remain active", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(labelText, fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val count = storageManager.remergeAllRecordings(segmentMergeGapMs)
                                val secStr = String.format(Locale.US, "%.1f", segmentMergeGapMs / 1000f)
                                Toast.makeText(context, "Re-merged pauses < ${secStr}s across $count recordings", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-merge All (${String.format(Locale.US, "%.1f", segmentMergeGapMs / 1000f)}s)", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val list = storageManager.listRecordings()
                                for (item in list) {
                                    com.example.recme.ai.worker.TranscriptionRunner.startTranscription(context, item.audioFile.name)
                                }
                                Toast.makeText(context, "Started transcription for ${list.size} recordings", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-transcribe All", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Segment Merging & Speaker Separation Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Segment Merging & Speaker Separation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Controls the maximum silence pause allowed between speech bursts before keeping them as separate segments. Lower thresholds (0.5s – 1.0s) prevent rolling different speakers into one another during active conversations. Longer thresholds (2.0s – 3.0s) group sentences into larger paragraphs for monologues.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Timing Readout
                    val gapSec = segmentMergeGapMs / 1000f
                    val modeLabel = when {
                        segmentMergeGapMs == 0L -> "0.0s — Raw VAD Turns (Never Merge)"
                        segmentMergeGapMs <= 500L -> "${String.format(Locale.US, "%.1f", gapSec)}s — Fast Conversation"
                        segmentMergeGapMs <= 1000L -> "${String.format(Locale.US, "%.1f", gapSec)}s — Standard Dialogue (Default)"
                        segmentMergeGapMs <= 2000L -> "${String.format(Locale.US, "%.1f", gapSec)}s — Casual Discussion"
                        else -> "${String.format(Locale.US, "%.1f", gapSec)}s — Monologue / Dictation"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Merge Pause Threshold", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(modeLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = (segmentMergeGapMs / 250L).toFloat(),
                        onValueChange = {
                            val newGap = (it.roundToInt() * 250L)
                            segmentMergeGapMs = newGap
                            storageManager.setSegmentMergeGapMs(newGap)
                        },
                        valueRange = 0f..20f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick-Select Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val presets = listOf(
                            Pair(0L, "No Merge (0s)"),
                            Pair(500L, "Fast Dialogue (0.5s)"),
                            Pair(1000L, "Conversation (1.0s)"),
                            Pair(2000L, "Discussion (2.0s)"),
                            Pair(3000L, "Monologue (3.0s)")
                        )
                        for ((pMs, pLabel) in presets) {
                            FilterChip(
                                selected = segmentMergeGapMs == pMs,
                                onClick = {
                                    segmentMergeGapMs = pMs
                                    storageManager.setSegmentMergeGapMs(pMs)
                                },
                                label = { Text(pLabel, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            val count = storageManager.remergeAllRecordings(segmentMergeGapMs)
                            val secStr = String.format(Locale.US, "%.1f", segmentMergeGapMs / 1000f)
                            Toast.makeText(context, "Re-merged pauses < ${secStr}s across $count recordings", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply ${String.format(Locale.US, "%.1f", segmentMergeGapMs / 1000f)}s Merge to All Existing Recordings", fontSize = 11.sp)
                    }
                }
            }

            // 1. Personal Background & Custom Vocabulary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isContextExpanded = !isContextExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Personal Background & Vocabulary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(if (isContextExpanded) "Hide ▲" else "Edit ▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Custom context injected into Gemini & Gemma to recognize names, relationships, and specialized technical terms.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isContextExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = userName,
                            onValueChange = {
                                userName = it
                                promptConfig.userName = it
                            },
                            label = { Text("Primary User Name", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userBio,
                            onValueChange = {
                                userBio = it
                                promptConfig.userBio = it
                            },
                            label = { Text("Professional / Personal Bio", fontSize = 12.sp) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = frequentSpeakers,
                            onValueChange = {
                                frequentSpeakers = it
                                promptConfig.frequentSpeakers = it
                            },
                            label = { Text("Frequent Contacts & Household (Names/Roles)", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Angelique (wife), Ansunet (daughter), Johan-Henry (son)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = customVocabulary,
                            onValueChange = {
                                customVocabulary = it
                                promptConfig.customVocabulary = it
                            },
                            label = { Text("Key Vocabulary / Domain Terms", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Simscape, Multibody, DIL, VAD, Opus, Obsidian") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = dialectRules,
                            onValueChange = {
                                dialectRules = it
                                promptConfig.dialectRules = it
                            },
                            label = { Text("Dialect & Code-Switching Rules", fontSize = 12.sp) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    promptConfig.resetUserContext()
                                    userName = promptConfig.userName
                                    userBio = promptConfig.userBio
                                    frequentSpeakers = promptConfig.frequentSpeakers
                                    customVocabulary = promptConfig.customVocabulary
                                    dialectRules = promptConfig.dialectRules
                                    Toast.makeText(context, "Context reset to defaults", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Reset to Defaults", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. AI Prompt Templates Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPromptsExpanded = !isPromptsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Prompt Templates", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(if (isPromptsExpanded) "Hide ▲" else "Edit ▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Customize the exact prompts passed to Gemini Cloud and Gemma LLM. Use placeholders like {USER_NAME}, {USER_CONTEXT}, {VOCABULARY}.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isPromptsExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Gemini Audio Transcription Prompt
                        Text("☁️ Gemini Audio System Instruction", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = geminiPrompt,
                            onValueChange = {
                                geminiPrompt = it
                                promptConfig.geminiTranscriptionInstruction = it
                            },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                promptConfig.resetPrompt(com.example.recme.ai.config.PromptConfigManager.KEY_GEMINI_TRANSCRIPTION_PROMPT)
                                geminiPrompt = promptConfig.geminiTranscriptionInstruction
                            }) { Text("Reset", fontSize = 11.sp) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Gemma Polishing Prompt
                        Text("⚡ Gemma LLM Polishing Template", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = gemmaPrompt,
                            onValueChange = {
                                gemmaPrompt = it
                                promptConfig.gemmaPolishingPrompt = it
                            },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                promptConfig.resetPrompt(com.example.recme.ai.config.PromptConfigManager.KEY_GEMMA_POLISHING_PROMPT)
                                gemmaPrompt = promptConfig.gemmaPolishingPrompt
                            }) { Text("Reset", fontSize = 11.sp) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Daily Summary & Actions Prompt
                        Text("📋 Daily Summary & Actions Template", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = summaryPrompt,
                            onValueChange = {
                                summaryPrompt = it
                                promptConfig.summaryAndActionsPrompt = it
                            },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                promptConfig.resetPrompt(com.example.recme.ai.config.PromptConfigManager.KEY_SUMMARY_ACTIONS_PROMPT)
                                summaryPrompt = promptConfig.summaryAndActionsPrompt
                            }) { Text("Reset", fontSize = 11.sp) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Ask AI System Prompt
                        Text("💬 Ask AI / Vault Companion System Prompt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = askAiPrompt,
                            onValueChange = {
                                askAiPrompt = it
                                promptConfig.askAiSystemPrompt = it
                            },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                promptConfig.resetPrompt(com.example.recme.ai.config.PromptConfigManager.KEY_ASK_AI_PROMPT)
                                askAiPrompt = promptConfig.askAiSystemPrompt
                            }) { Text("Reset", fontSize = 11.sp) }
                        }
                    }
                }
            }

            // 3. Voiceprints & Speaker Profiles (Acoustic Learning) Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSpeakersExpanded = !isSpeakersExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voiceprints & Speaker Profiles", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(if (isSpeakersExpanded) "Hide ▲" else "View ▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Acoustically learns voices and combines voiceprints with vault context to automatically tag speakers on recordings.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isSpeakersExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Toggle Voice Gate (§201 StGB Privacy Filter)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Voice Gate (Authorized Voices Only)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    "Strictly filter audio and only persist speech matching authorized profiles (§201 StGB compliance)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isVoiceGateEnabled,
                                onCheckedChange = {
                                    isVoiceGateEnabled = it
                                    speakerProfileManager.isVoiceGateEnabled = it
                                }
                            )
                        }

                        if (isVoiceGateEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Voice Gate Confidence Threshold", fontSize = 12.sp)
                                Text(String.format("%.2f", voiceGateThreshold), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = voiceGateThreshold,
                                onValueChange = {
                                    voiceGateThreshold = it
                                    speakerProfileManager.voiceGateConfidenceThreshold = it
                                },
                                valueRange = 0.50f..0.90f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Toggle Speaker Recognition
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Acoustic Speaker Recognition", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Extract voice embeddings and label speakers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isSpeakerRecEnabled,
                                onCheckedChange = {
                                    isSpeakerRecEnabled = it
                                    speakerProfileManager.isSpeakerRecognitionEnabled = it
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Toggle Continuous Learning
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Continuous Online Learning", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Continuously update & sharpen voiceprints from high-confidence speech", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isContinuousLearning,
                                onCheckedChange = {
                                    isContinuousLearning = it
                                    speakerProfileManager.isContinuousLearningEnabled = it
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Similarity Threshold Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recognition Confidence Threshold", fontSize = 12.sp)
                            Text(String.format("%.2f", speakerThreshold), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Slider(
                            value = speakerThreshold,
                            onValueChange = {
                                speakerThreshold = it
                                speakerProfileManager.recognitionThreshold = it
                            },
                            valueRange = 0.40f..0.90f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Enrolled Profiles List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enrolled Voice Profiles (${speakerProfiles.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(
                                onClick = {
                                    newSpeakerName = ""
                                    newSpeakerRelationship = "Family"
                                    isAddSpeakerDialogOpen = true
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enroll Voice", fontSize = 12.sp)
                            }
                        }

                        if (speakerProfiles.isEmpty()) {
                            Text(
                                "No voiceprints enrolled yet. Tap 'Enroll Voice' or tag speakers in recordings to begin learning.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (profile in speakerProfiles) {
                                    val parsedColor = try {
                                        Color(android.graphics.Color.parseColor(profile.colorHex))
                                    } catch (e: Exception) {
                                        MaterialTheme.colorScheme.primary
                                    }

                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = parsedColor,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxSize(),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            profile.name.take(1).uppercase(),
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        "${profile.relationship} • ${profile.sampleCount} total sample(s)",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        if (profile.allowedToRecord) "✓ Authorized to Record" else "✗ Unconsented (Ignored by Gate)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (profile.allowedToRecord) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (profile.languageSampleCounts.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            for ((lang, count) in profile.languageSampleCounts) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                                ) {
                                                                    Text(
                                                                        "${lang.uppercase()}: $count",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.secondary,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = profile.allowedToRecord,
                                                    onCheckedChange = { allowed ->
                                                        scope.launch {
                                                            speakerProfileManager.updateConsent(profile.id, allowed)
                                                            speakerProfiles = speakerProfileManager.getProfiles()
                                                        }
                                                    }
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            speakerProfileManager.deleteProfile(profile.id)
                                                            speakerProfiles = speakerProfileManager.getProfiles()
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Google Drive Cloud Sync Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive Cloud Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isSignedInToDrive) {
                        Text(
                            "Connect your Google Account to automatically sync audio recordings, JSON sidecars, and Obsidian Markdown (.md) notes to a dedicated 'RecMe/' folder.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                signInLauncher.launch(authManager.getSignInIntent())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connected", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF43A047))
                                }
                                Text(
                                    userEmail ?: "Google Account",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    authManager.signOut {
                                        isSignedInToDrive = false
                                        userEmail = null
                                    }
                                }
                            ) {
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Auto-Sync Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Sync Recordings", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Uploads automatically when file finalizes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAutoSyncEnabled,
                                onCheckedChange = {
                                    isAutoSyncEnabled = it
                                    prefs.edit().putBoolean(GoogleDriveSyncWorker.KEY_AUTO_SYNC_ENABLED, it).apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Wi-Fi Only Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wi-Fi Only", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Prevents using cellular mobile data", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isWifiOnly,
                                onCheckedChange = {
                                    isWifiOnly = it
                                    prefs.edit().putBoolean(GoogleDriveSyncWorker.KEY_WIFI_ONLY, it).apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Delete Local Audio After Upload Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Delete Local Audio After Upload", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Frees up phone storage after cloud confirmation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isDeleteAfterUpload,
                                onCheckedChange = {
                                    isDeleteAfterUpload = it
                                    prefs.edit().putBoolean(GoogleDriveSyncWorker.KEY_DELETE_AFTER_UPLOAD, it).apply()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                SyncScheduler.triggerManualSyncNow(context)
                                Toast.makeText(context, "Google Drive sync started", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync All Recordings Now")
                        }
                    }
                }
            }

            // Obsidian Vault & Batch Sync Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Obsidian Vault & Batch Export", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Vault files reside in Documents/RecMe/vault/. Control whether recordings sync continuously or via clean review batches.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Auto-Sync to Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                if (isVaultAutoSyncEnabled) "Enabled: Immediately syncs daily notes on transcribe"
                                else "Disabled (Recommended): Changes stay in local sidecars until manual Batch Sync",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isVaultAutoSyncEnabled,
                            onCheckedChange = {
                                isVaultAutoSyncEnabled = it
                                vaultManager.isAutoSyncEnabled = it
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (!isVaultSyncing) {
                                isVaultSyncing = true
                                scope.launch {
                                    try {
                                        val result = vaultManager.syncVaultBatch()
                                        Toast.makeText(
                                            context,
                                            "Batch sync complete: ${result.recordingsExported} recordings across ${result.dailyNotesUpdated} days",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Batch sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isVaultSyncing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isVaultSyncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rebuilding Vault & Dailies...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Batch Vault Sync Now")
                        }
                    }
                }
            }

            // VAD AI Sensitivity Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Silero VAD Sensitivity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detection Threshold", fontSize = 13.sp)
                        Text(String.format("%.2f", sensitivity), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Slider(
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            prefs.edit().putFloat(VadRecordingService.KEY_SENSITIVITY, it).apply()
                            val intent = Intent(context, VadRecordingService::class.java).apply {
                                action = VadRecordingService.ACTION_UPDATE_SENSITIVITY
                                putExtra(VadRecordingService.EXTRA_THRESHOLD, it)
                            }
                            context.startService(intent)
                        },
                        valueRange = 0.1f..0.9f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("More Sensitive (0.1)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Recommended (0.5)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Less Sensitive (0.9)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // File Splitting & Compression Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("File Splitting & Compression", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opus Compression Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Opus Post-Processing", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isOpusEnabled) "Compresses completed files to 32 kbps Opus (~90% smaller)" else "Save only uncompressed 16-bit PCM WAV",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isOpusEnabled,
                            onCheckedChange = {
                                isOpusEnabled = it
                                prefs.edit().putBoolean(VadRecordingService.KEY_OPUS_COMPRESSION, it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // File Split Size Slider
                    val speechMinutes = ((splitSizeMb * 1024L * 1024L) / (32000L * 60L)).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max File Split Size", fontSize = 13.sp)
                        Text("${splitSizeMb.roundToInt()} MB (~$speechMinutes min speech)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Slider(
                        value = splitSizeMb,
                        onValueChange = {
                            val rounded = (it / 5.0f).roundToInt() * 5f
                            splitSizeMb = rounded
                            prefs.edit().putFloat(VadRecordingService.KEY_SPLIT_SIZE_MB, rounded).apply()
                        },
                        valueRange = AudioConstants.MIN_FILE_SIZE_MB..AudioConstants.MAX_FILE_SIZE_MB,
                        steps = ((AudioConstants.MAX_FILE_SIZE_MB - AudioConstants.MIN_FILE_SIZE_MB) / 25f).toInt(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("25 MB (~13m)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Default (95 MB)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("700 MB (~6h)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Audio & Buffering Specifications Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio & Engine Specs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("• Live Format: 16-bit PCM Linear WAV (16 kHz Mono, 32 KB/s)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• AI Engine: Silero VAD v5 ONNX via CPU Runtime", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Pre-Roll: 608 ms (~19 frames) circular buffer", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Post-Roll: 608 ms silence hangover", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Smart Split: ~${splitSizeMb.roundToInt()} MB target (deferred to silence)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Compression: ${if (isOpusEnabled) "32 kbps Opus (Post-Process)" else "Disabled (Raw WAV)"}", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Day Rollover: Automatic partition at midnight (00:00:00)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Cloud Sync: Google Drive 'RecMe/' folder (drive.file scope)", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Multilingual ASR: Whisper + Gemma 4 (99 languages supported)", fontSize = 13.sp)
                }
            }

            // Storage Location Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Storage & USB Access", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Path: ${storageManager.getRecordingsDirectory().absolutePath}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isAllFilesGranted) {
                        Button(
                            onClick = {
                                requestAllFilesAccess(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant All Files Access (for USB Visibility)")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Storage Access Configured", fontSize = 12.sp, color = Color(0xFF43A047))
                        }
                    }
                }
            }

            // Battery Optimization Exemption Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("24/7 Background Recording", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Android aggressively kills background audio after long sleep unless battery optimization is disabled.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isIgnoringBattery) {
                        Button(
                            onClick = {
                                requestBatteryExemption(context)
                                isIgnoringBattery = checkBatteryExemption(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disable Battery Optimization")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery Optimization Disabled (Unrestricted)", fontSize = 12.sp, color = Color(0xFF43A047))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App Version Footer
            Text(
                "RecMe v1.0 • Built for Pixel 10 (Android 14+)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // Searchable 99-Language Modal Bottom Sheet
    if (isLanguagePickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { isLanguagePickerOpen = false },
            sheetState = sheetState
        ) {
            val filteredLanguages = remember(languageSearchQuery) {
                if (languageSearchQuery.isBlank()) {
                    WhisperLanguageConfig.ALL_SUPPORTED_LANGUAGES
                } else {
                    val q = languageSearchQuery.trim().lowercase()
                    WhisperLanguageConfig.ALL_SUPPORTED_LANGUAGES.filter {
                        it.code.lowercase().contains(q) ||
                        it.name.lowercase().contains(q) ||
                        it.nativeName.lowercase().contains(q)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Active Languages", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = {
                        activeLanguages = WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet()
                        prefs.edit().putStringSet(TranscriptionWorker.KEY_ACTIVE_LANGUAGES, activeLanguages).apply()
                    }) {
                        Text("Reset Default [AF, EN, DE]", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = languageSearchQuery,
                    onValueChange = { languageSearchQuery = it },
                    placeholder = { Text("Search 99 languages (e.g. Dutch, nl, Español)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (languageSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { languageSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isSelected = activeLanguages.contains(lang.code)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = activeLanguages.toMutableSet()
                                    if (isSelected) {
                                        if (updated.size > 1) {
                                            updated.remove(lang.code)
                                        } else {
                                            Toast.makeText(context, "At least one language must remain active", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        updated.add(lang.code)
                                    }
                                    activeLanguages = updated
                                    prefs.edit().putStringSet(TranscriptionWorker.KEY_ACTIVE_LANGUAGES, updated).apply()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${lang.name} (${lang.nativeName})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Token code: <|${lang.code}|>",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        val updated = activeLanguages.toMutableSet()
                                        if (checked) {
                                            updated.add(lang.code)
                                        } else {
                                            if (updated.size > 1) {
                                                updated.remove(lang.code)
                                            }
                                        }
                                        activeLanguages = updated
                                        prefs.edit().putStringSet(TranscriptionWorker.KEY_ACTIVE_LANGUAGES, updated).apply()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { isLanguagePickerOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done (${activeLanguages.size} active)")
                }
            }
        }
    }

    if (isAddSpeakerDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { isAddSpeakerDialogOpen = false },
            title = { Text("Enroll Voice Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Add a speaker profile to enable acoustic recognition and language-aware multi-centroid voiceprint adaptation.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newSpeakerName,
                        onValueChange = { newSpeakerName = it },
                        label = { Text("Speaker Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Jan, Angelique, Ansunet, Johan-Henry") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newSpeakerRelationship,
                        onValueChange = { newSpeakerRelationship = it },
                        label = { Text("Relationship / Role", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Self, Wife, Daughter, Son, Colleague") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newSpeakerAliases,
                        onValueChange = { newSpeakerAliases = it },
                        label = { Text("Aliases / Nicknames (comma separated)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Boetie, Papa, Eben") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Primary Enrollment Language:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val langOptions = listOf("af" to "Afrikaans", "de" to "German", "en" to "English")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((code, label) in langOptions) {
                            FilterChip(
                                selected = newSpeakerLanguage == code,
                                onClick = { newSpeakerLanguage = code },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Avatar Color:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val colors = listOf("#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EF4444", "#EC4899")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in colors) {
                            val parsed = Color(android.graphics.Color.parseColor(c))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = parsed,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { newSpeakerColor = c }
                            ) {
                                if (newSpeakerColor == c) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (recordedSampleEmbedding != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Acoustic Sample Enrollment:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isRecordingSample) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recording voice sample... (${recordingRemainingSec}s remaining)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            } else if (recordedSampleEmbedding != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Acoustic embedding captured (192-d)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Text("Speak clearly for 4 seconds in the selected language to train acoustic recognition.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isRecordingSample = true
                                        val recorder = com.example.recme.ai.speaker.SpeakerVoiceRecorder(context)
                                        val res = recorder.recordSampleAndExtractEmbedding(durationMs = 4000L) { sec ->
                                            recordingRemainingSec = sec
                                        }
                                        isRecordingSample = false
                                        if (res.isSuccess) {
                                            recordedSampleEmbedding = res.getOrNull()
                                            Toast.makeText(context, "Voice sample captured!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isRecordingSample,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (recordedSampleEmbedding != null) "Re-record 4s Voice Sample" else "Record 4s Voice Sample")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSpeakerName.isNotBlank()) {
                            scope.launch {
                                val embedding = recordedSampleEmbedding ?: FloatArray(192) { i ->
                                    ((newSpeakerName.hashCode() * (i + 1)) % 1000) / 1000.0f
                                }
                                val aliasList = newSpeakerAliases.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }

                                speakerProfileManager.enrollOrUpdateProfile(
                                    name = newSpeakerName.trim(),
                                    relationship = newSpeakerRelationship.trim(),
                                    colorHex = newSpeakerColor,
                                    newEmbedding = embedding,
                                    spokenLanguage = newSpeakerLanguage,
                                    aliases = aliasList
                                )
                                speakerProfiles = speakerProfileManager.getProfiles()
                                isAddSpeakerDialogOpen = false
                                recordedSampleEmbedding = null
                                Toast.makeText(context, "Saved voice profile for '$newSpeakerName'", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = newSpeakerName.isNotBlank() && !isRecordingSample
                ) {
                    Text("Save Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddSpeakerDialogOpen = false
                    recordedSampleEmbedding = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun checkBatteryExemption(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    }
}

private fun checkAllFilesAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
}

private fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    }
}
