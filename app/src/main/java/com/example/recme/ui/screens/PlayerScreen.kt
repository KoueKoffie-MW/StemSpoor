package com.example.recme.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recme.player.DualTimelinePlayer
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.SpeechSegmentData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    recordingItem: RecordingItem,
    initialSeekMs: Long = 0L,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember { DualTimelinePlayer(context) }
    val playbackState by player.playbackState.collectAsState()

    DisposableEffect(recordingItem) {
        player.loadRecording(recordingItem.wavFile, recordingItem.sidecarData)
        if (initialSeekMs > 0) {
            player.seekTo(initialSeekMs)
            player.play()
        }
        onDispose {
            player.close()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(recordingItem.baseName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Dual-Timeline Synchronized Playback", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    PlayerControlsSection(player, playbackState)
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SegmentsListSection(
                        segments = recordingItem.sidecarData?.segments ?: emptyList(),
                        activeSegmentIndex = playbackState.activeSegmentIndex,
                        onSelectSegment = { seg -> player.seekTo(seg.audioStartMs) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                PlayerControlsSection(player, playbackState)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Speech Segments (${recordingItem.sidecarData?.segments?.size ?: 0})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SegmentsListSection(
                        segments = recordingItem.sidecarData?.segments ?: emptyList(),
                        activeSegmentIndex = playbackState.activeSegmentIndex,
                        onSelectSegment = { seg -> player.seekTo(seg.audioStartMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsSection(
    player: DualTimelinePlayer,
    playbackState: com.example.recme.player.PlaybackState
) {
    val wallClockFormat = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm:ss", Locale.getDefault()) }
    val currentWallClockStr = remember(playbackState.realWorldEpochMs) {
        if (playbackState.realWorldEpochMs > 0) {
            wallClockFormat.format(Date(playbackState.realWorldEpochMs))
        } else {
            "--:--:--"
        }
    }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wall-Clock Timecode Display Card (ADR-0005)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "REAL-WORLD TIME WHEN SPOKEN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentWallClockStr,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Condensed Audio Slider
        val totalDur = playbackState.totalDurationMs.coerceAtLeast(1L)
        val currentPos = if (isUserScrubbing) scrubPositionMs.toLong() else playbackState.currentPositionMs

        Slider(
            value = currentPos.toFloat().coerceIn(0f, totalDur.toFloat()),
            onValueChange = {
                isUserScrubbing = true
                scrubPositionMs = it
            },
            onValueChangeFinished = {
                player.seekTo(scrubPositionMs.toLong())
                isUserScrubbing = false
            },
            valueRange = 0f..totalDur.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val curSec = currentPos / 1000
            val totSec = totalDur / 1000
            Text(String.format("%02d:%02d", curSec / 60, curSec % 60), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format("%02d:%02d", totSec / 60, totSec % 60), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Playback Transport Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { player.seekRelative(-5000L) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 5s", modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .clickable { player.togglePlayPause() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(onClick = { player.seekRelative(5000L) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.FastForward, contentDescription = "Forward 5s", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Speed Selector Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            speeds.forEach { speed ->
                FilterChip(
                    selected = playbackState.playbackSpeed == speed,
                    onClick = { player.setSpeed(speed) },
                    label = { Text("${speed}x", fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun SegmentsListSection(
    segments: List<SpeechSegmentData>,
    activeSegmentIndex: Int,
    onSelectSegment: (SpeechSegmentData) -> Unit
) {
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(activeSegmentIndex) {
        if (activeSegmentIndex in segments.indices) {
            listState.animateScrollToItem(activeSegmentIndex)
        }
    }

    if (segments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No segment metadata available", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(segments, key = { _, seg -> seg.segmentIndex }) { index, seg ->
                val isActive = index == activeSegmentIndex
                val startWall = timeFormat.format(Date(seg.speechStartEpochMs))
                val endWall = timeFormat.format(Date(seg.speechEndEpochMs))
                val durSec = (seg.audioEndMs - seg.audioStartMs) / 1000

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSegment(seg) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$startWall  →  $endWall",
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "Audio: ${String.format("%02d:%02d", (seg.audioStartMs/1000)/60, (seg.audioStartMs/1000)%60)} (${durSec}s)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isActive) {
                            Text(
                                "PLAYING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
