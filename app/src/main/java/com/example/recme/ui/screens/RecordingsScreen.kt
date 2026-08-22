package com.example.recme.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.recme.ai.worker.TranscriptionRunner
import com.example.recme.ai.worker.TranscriptionStateTracker
import com.example.recme.ai.worker.TranscriptionStatus
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.StorageManager
import com.example.recme.storage.TranscriptExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordingsScreen(
    onNavigateBack: () -> Unit,
    onPlayRecording: (RecordingItem) -> Unit
) {
    val context = LocalContext.current
    val storageManager = remember { StorageManager(context) }
    val speakerProfileManager = remember { com.example.recme.ai.speaker.SpeakerProfileManager(context) }
    val transcriptionStates by TranscriptionStateTracker.statusFlow.collectAsState()

    var recordings by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var speakerProfiles by remember { mutableStateOf<List<com.example.recme.ai.speaker.SpeakerProfile>>(emptyList()) }
    var itemToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var itemToDeleteTranscript by remember { mutableStateOf<RecordingItem?>(null) }
    var itemToViewTranscript by remember { mutableStateOf<RecordingItem?>(null) }
    var itemToEditSpeakerSegment by remember { mutableStateOf<Pair<RecordingItem, com.example.recme.storage.SpeechSegmentData>?>(null) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun refreshList() {
        recordings = storageManager.listRecordings()
        scope.launch {
            speakerProfiles = speakerProfileManager.getProfiles()
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    // Auto-refresh when any transcription completes
    LaunchedEffect(transcriptionStates) {
        if (transcriptionStates.values.any { it is TranscriptionStatus.Completed }) {
            refreshList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorded Files", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val pending = recordings.filter { it.sidecarData == null || !it.sidecarData.isTranscribed }
                        if (pending.isNotEmpty()) {
                            com.example.recme.ai.worker.TranscriptionQueue.enqueueAll(context, pending.map { it.audioFile.name })
                            Toast.makeText(context, "Queued ${pending.size} pending recording(s)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "All recordings are already transcribed", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Psychology, contentDescription = "Transcribe All Pending")
                    }
                    IconButton(onClick = {
                        val remerged = storageManager.remergeAllRecordings(3000L)
                        Toast.makeText(context, "Re-merged segments across $remerged recordings (<3s)", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }) {
                        Icon(Icons.Default.Link, contentDescription = "Re-merge Segments for All (< 3s)")
                    }
                    IconButton(onClick = {
                        com.example.recme.sync.SyncScheduler.triggerManualSyncNow(context)
                        refreshList()
                    }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Sync All to Drive")
                    }
                    IconButton(onClick = { refreshList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No recordings found",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Saved files will appear in Documents/RecMe",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recordings, key = { it.wavFile.absolutePath }) { item ->
                    val status = transcriptionStates[item.audioFile.name] ?: TranscriptionStatus.Idle

                    RecordingCard(
                        item = item,
                        transcriptionStatus = status,
                        onPlay = { onPlayRecording(item) },
                        onShare = { shareRecording(context, item) },
                        onDelete = { itemToDelete = item },
                        onDeleteTranscript = { itemToDeleteTranscript = item },
                        onTranscribe = {
                            TranscriptionRunner.startTranscription(context, item.audioFile.name)
                            Toast.makeText(context, "Queued ${item.baseName} for transcription...", Toast.LENGTH_SHORT).show()
                        },
                        onCancelTranscription = {
                            com.example.recme.ai.worker.TranscriptionQueue.cancel(item.audioFile.name)
                            Toast.makeText(context, "Cancelled transcription for ${item.baseName}", Toast.LENGTH_SHORT).show()
                        },
                        onRemergeSegments = {
                            storageManager.remergeRecordingSegments(item, 3000L)
                            Toast.makeText(context, "Re-merged pauses < 3s in ${item.baseName}", Toast.LENGTH_SHORT).show()
                            refreshList()
                        },
                        onViewTranscript = { itemToViewTranscript = item }
                    )
                }
            }
        }

        // Delete Full Recording Confirmation Dialog
        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("Delete Recording?") },
                text = { Text("Are you sure you want to delete ${item.baseName}? Both audio and metadata will be removed.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            storageManager.deleteRecording(item)
                            itemToDelete = null
                            refreshList()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Transcript Only Confirmation Dialog
        itemToDeleteTranscript?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDeleteTranscript = null },
                title = { Text("Delete Transcript?") },
                text = { Text("Are you sure you want to delete the transcript and note for ${item.baseName}? The audio recording will be preserved.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val success = storageManager.deleteTranscript(item)
                            if (success) {
                                Toast.makeText(context, "Transcript deleted for ${item.baseName}", Toast.LENGTH_SHORT).show()
                            }
                            itemToDeleteTranscript = null
                            refreshList()
                        }
                    ) {
                        Text("Delete Transcript", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDeleteTranscript = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Transcript Viewer Dialog
        itemToViewTranscript?.let { item ->
            val mdFile = File(item.audioFile.parentFile, "${item.baseName}.md")
            val transcriptContent = if (mdFile.exists()) {
                mdFile.readText(Charsets.UTF_8)
            } else if (item.sidecarData?.isTranscribed == true) {
                TranscriptExporter.exportToObsidianMarkdown(item.audioFile, item.sidecarData).readText(Charsets.UTF_8)
            } else {
                "Transcript is not available yet."
            }

            val segments = item.sidecarData?.segments ?: emptyList()
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            AlertDialog(
                onDismissRequest = { itemToViewTranscript = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.baseName, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (segments.isEmpty()) {
                            Text(transcriptContent, fontSize = 13.sp, lineHeight = 18.sp)
                        } else {
                            for (seg in segments) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "[${timeFormat.format(Date(seg.speechStartEpochMs))}]",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(3.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        seg.detectedLanguage?.uppercase() ?: "AF",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            // Clickable speaker badge
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (!seg.speaker.isNullOrBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.clickable {
                                                    itemToEditSpeakerSegment = Pair(item, seg)
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = seg.speaker ?: "+ Assign Speaker",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (!seg.speaker.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = seg.polishedText ?: seg.rawText ?: "(Silence)",
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            shareTranscriptText(context, item.baseName, transcriptContent)
                        }
                    ) {
                        Text("Share Note")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToViewTranscript = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Speaker Assignment & Acoustic Adaptation Dialog
        itemToEditSpeakerSegment?.let { (recItem, segment) ->
            AlertDialog(
                onDismissRequest = { itemToEditSpeakerSegment = null },
                title = { Text("Assign Speaker for Segment", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Select speaker to tag segment and adapt voiceprint centroid:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        for (profile in speakerProfiles) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (segment.speaker == profile.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val currentSidecar = recItem.sidecarData ?: return@clickable
                                        val updatedSegs = currentSidecar.segments.map { s ->
                                            if (s.segmentIndex == segment.segmentIndex) {
                                                s.copy(speaker = profile.name, speakerConfidence = 1.0f)
                                            } else s
                                        }

                                        // 1. Instant Optimistic UI Update (0ms latency)
                                        val optimisticSidecar = currentSidecar.copy(segments = updatedSegs)
                                        val optimisticItem = recItem.copy(sidecarData = optimisticSidecar)
                                        itemToViewTranscript = optimisticItem
                                        itemToEditSpeakerSegment = null

                                        // 2. Heavy Neural & File I/O in Background
                                        scope.launch(Dispatchers.IO) {
                                            val pcmBytes = com.example.recme.audio.AudioChunkExtractor.extractPcmChunk(
                                                recItem.audioFile,
                                                segment.audioStartMs,
                                                segment.audioEndMs
                                            )
                                            if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                                                val floatSamples = FloatArray(pcmBytes.size / 2) { i ->
                                                    val low = pcmBytes[i * 2].toInt() and 0xFF
                                                    val high = pcmBytes[i * 2 + 1].toInt()
                                                    val sVal = (high shl 8) or low
                                                    sVal / 32768.0f
                                                }
                                                val engine = com.example.recme.ai.speaker.SpeakerEmbeddingEngine(context)
                                                try {
                                                    val emb = engine.extractEmbedding(floatSamples)
                                                    speakerProfileManager.adaptProfileCentroid(
                                                        speakerName = profile.name,
                                                        newEmbedding = emb,
                                                        spokenLanguage = segment.detectedLanguage,
                                                        alpha = 0.25f
                                                    )
                                                } catch (e: Exception) {
                                                    android.util.Log.e("RecordingsScreen", "Voiceprint adaptation failed", e)
                                                } finally {
                                                    engine.close()
                                                }
                                            }

                                            // Update JSON sidecar & export markdown
                                            val updatedSidecar = recItem.jsonFile?.let { jf ->
                                                TranscriptExporter.updateSidecarJson(jf, updatedSegs, currentSidecar.languagesDetected)
                                            }
                                            if (updatedSidecar != null) {
                                                val vaultManager = com.example.recme.vault.VaultManager(context)
                                                TranscriptExporter.exportToObsidianMarkdown(recItem.audioFile, updatedSidecar)
                                                if (vaultManager.isAutoSyncEnabled) {
                                                    TranscriptExporter.exportToObsidianMarkdown(recItem.audioFile, updatedSidecar, vaultManager.recordingsDir)
                                                    val finalItem = recItem.copy(sidecarData = updatedSidecar)
                                                    vaultManager.upsertRecordingToDailyNote(finalItem, updatedSegs)
                                                }
                                                withContext(Dispatchers.Main) {
                                                    itemToViewTranscript = recItem.copy(sidecarData = updatedSidecar)
                                                    refreshList()
                                                    Toast.makeText(context, "Assigned speaker '${profile.name}' & adapted voiceprint", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(profile.relationship, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (segment.speaker == profile.name) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Clear speaker option
                        if (!segment.speaker.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    val currentSidecar = recItem.sidecarData ?: return@TextButton
                                    val updatedSegs = currentSidecar.segments.map { s ->
                                        if (s.segmentIndex == segment.segmentIndex) {
                                            s.copy(speaker = null, speakerConfidence = null)
                                        } else s
                                    }

                                    // Instant Optimistic UI Update
                                    val optimisticSidecar = currentSidecar.copy(segments = updatedSegs)
                                    val optimisticItem = recItem.copy(sidecarData = optimisticSidecar)
                                    itemToViewTranscript = optimisticItem
                                    itemToEditSpeakerSegment = null

                                    scope.launch(Dispatchers.IO) {
                                        val updatedSidecar = recItem.jsonFile?.let { jf ->
                                            TranscriptExporter.updateSidecarJson(jf, updatedSegs, currentSidecar.languagesDetected)
                                        }
                                        if (updatedSidecar != null) {
                                            val vaultManager = com.example.recme.vault.VaultManager(context)
                                            TranscriptExporter.exportToObsidianMarkdown(recItem.audioFile, updatedSidecar)
                                            if (vaultManager.isAutoSyncEnabled) {
                                                TranscriptExporter.exportToObsidianMarkdown(recItem.audioFile, updatedSidecar, vaultManager.recordingsDir)
                                                val finalItem = recItem.copy(sidecarData = updatedSidecar)
                                                vaultManager.upsertRecordingToDailyNote(finalItem, updatedSegs)
                                            }
                                            withContext(Dispatchers.Main) {
                                                itemToViewTranscript = recItem.copy(sidecarData = updatedSidecar)
                                                refreshList()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear Speaker Tag", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { itemToEditSpeakerSegment = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingCard(
    item: RecordingItem,
    transcriptionStatus: TranscriptionStatus,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDeleteTranscript: () -> Unit,
    onTranscribe: () -> Unit,
    onCancelTranscription: () -> Unit,
    onRemergeSegments: () -> Unit,
    onViewTranscript: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(item.lastModifiedEpochMs) {
        dateFormat.format(Date(item.lastModifiedEpochMs))
    }

    val durSec = item.totalAudioDurationMs / 1000
    val durStr = String.format("%02d:%02d", durSec / 60, durSec % 60)
    val sizeMb = item.fileSizeBytes / (1024f * 1024f)
    val segmentCount = item.sidecarData?.segments?.size ?: 0
    val isTranscribed = item.sidecarData?.isTranscribed == true

    val isProcessing = transcriptionStatus is TranscriptionStatus.Transcribing ||
                       transcriptionStatus is TranscriptionStatus.Polishing ||
                       transcriptionStatus is TranscriptionStatus.Queued

    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Full un-truncated file name
                        Text(
                            text = item.baseName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            softWrap = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Badges Row
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (item.isCompressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (item.isCompressed) "OPUS" else "WAV",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isCompressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            if (item.isCloudSynced) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "DRIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            if (isTranscribed) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "NOTE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                            if (isTranscribed) {
                                val cardSpeakers = item.sidecarData?.segments?.mapNotNull { it.speaker }?.distinct() ?: emptyList()
                                for (spk in cardSpeakers) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = spk,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "$dateStr • $durStr • ${String.format("%.1f MB", sizeMb)} • $segmentCount segments",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val snippet = item.sidecarData?.segments?.firstOrNull()?.let { seg ->
                            val text = seg.polishedText ?: seg.rawText
                            val spk = if (!seg.speaker.isNullOrBlank()) "${seg.speaker}: " else ""
                            if (!text.isNullOrBlank()) "$spk$text" else null
                        }
                        if (!snippet.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = snippet,
                                fontSize = 11.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }

                // Action & Menu Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isTranscribed) {
                        IconButton(onClick = onViewTranscript) {
                            Icon(Icons.Default.Description, contentDescription = "View Note", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onTranscribe) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-transcribe AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = onTranscribe) {
                            Icon(Icons.Default.Psychology, contentDescription = "Transcribe AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", modifier = Modifier.size(20.dp))
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isProcessing) {
                                DropdownMenuItem(
                                    text = { Text("❌ Cancel Transcription Queue") },
                                    onClick = {
                                        showMenu = false
                                        onCancelTranscription()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(if (isTranscribed) "🔄 Re-transcribe Audio" else "🔄 Transcribe Audio") },
                                    onClick = {
                                        showMenu = false
                                        onTranscribe()
                                    }
                                )
                            }
                            if (isTranscribed) {
                                DropdownMenuItem(
                                    text = { Text("📝 View Markdown Note") },
                                    onClick = {
                                        showMenu = false
                                        onViewTranscript()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🗑️ Delete Transcript Only", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteTranscript()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("🔗 Re-merge Segments (< 3s)") },
                                onClick = {
                                    showMenu = false
                                    onRemergeSegments()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📤 Share Recording") },
                                onClick = {
                                    showMenu = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🗑️ Delete Full Recording", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Live Transcription Progress Bar & Status details
            when (transcriptionStatus) {
                is TranscriptionStatus.Queued -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⏳ In Queue (#${transcriptionStatus.queuePosition})...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = onCancelTranscription,
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Text("Cancel", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                is TranscriptionStatus.Transcribing -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { transcriptionStatus.percent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "⚡ Transcribing segment ${transcriptionStatus.currentSegment} of ${transcriptionStatus.totalSegments}...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${(transcriptionStatus.percent * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is TranscriptionStatus.Polishing -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "✨ Refining multilingual text with Gemma 4...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is TranscriptionStatus.Failed -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "⚠️ ${transcriptionStatus.error}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onTranscribe,
                            modifier = Modifier.height(28.dp),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Text("Retry", fontSize = 10.sp)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

private fun shareRecording(context: Context, item: RecordingItem) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", item.audioFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isCompressed) "audio/ogg" else "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareTranscriptText(context: Context, baseName: String, content: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Transcript: $baseName")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Markdown Note"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
