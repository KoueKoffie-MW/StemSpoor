package com.example.recme.ui.screens.vault

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.StorageManager
import com.example.recme.vault.MarkdownElement
import com.example.recme.vault.MarkdownParser
import com.example.recme.vault.VaultManager
import com.example.recme.vault.VaultNote
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(
    onPlayAudioSegment: (RecordingItem, Long) -> Unit,
    onNavigateToAskAiWithContext: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val vaultManager = remember { VaultManager(context) }
    val storageManager = remember { StorageManager(context) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var isSyncingBatch by remember { mutableStateOf(false) }
    var syncProgressText by remember { mutableStateOf("") }

    var selectedNoteToView by remember { mutableStateOf<VaultNote?>(null) }
    var showNewNoteDialog by remember { mutableStateOf(false) }

    fun refreshNotes() {
        notes = vaultManager.listNotes()
    }

    LaunchedEffect(Unit) {
        vaultManager.getOrCreateDailyNote() // Ensure today's note exists
        refreshNotes()
    }

    val filteredNotes = remember(notes, searchQuery, selectedTabIndex) {
        val baseList = when (selectedTabIndex) {
            0 -> notes.filter { it.isDailyNote }
            1 -> notes.filter { !it.isDailyNote }
            else -> notes
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    if (selectedNoteToView != null) {
        NoteEditorViewer(
            note = selectedNoteToView!!,
            vaultManager = vaultManager,
            storageManager = storageManager,
            onClose = {
                selectedNoteToView = null
                refreshNotes()
            },
            onPlayAudio = onPlayAudioSegment,
            onOpenWikiLink = { targetTopic ->
                val targetNote = notes.firstOrNull { it.title.equals(targetTopic, ignoreCase = true) }
                if (targetNote != null) {
                    selectedNoteToView = targetNote
                } else {
                    // Create topic note on demand
                    val newFile = vaultManager.saveTopicNote(targetTopic, "# [[$targetTopic]]\n\nCreated from reference.\n")
                    refreshNotes()
                    selectedNoteToView = VaultNote(
                        file = newFile,
                        title = targetTopic,
                        relativePath = "topics/$targetTopic.md",
                        lastModifiedMs = System.currentTimeMillis(),
                        isDailyNote = false,
                        content = newFile.readText()
                    )
                }
            },
            onAskAiAboutNote = { content ->
                onNavigateToAskAiWithContext?.invoke(content)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search vault notes & tags...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obsidian Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    if (isSyncingBatch) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                isSyncingBatch = true
                                scope.launch {
                                    try {
                                        val result = vaultManager.syncVaultBatch { current, total, item ->
                                            syncProgressText = "Syncing $current/$total: $item"
                                        }
                                        refreshNotes()
                                        Toast.makeText(
                                            context,
                                            "Batch sync complete: ${result.recordingsExported} recordings across ${result.dailyNotesUpdated} days",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Batch sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSyncingBatch = false
                                        syncProgressText = ""
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Vault Batch")
                        }
                    }
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { refreshNotes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewNoteDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Topic Note")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Daily Journal") },
                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Topics & Wiki") },
                    icon = { Icon(Icons.Default.Tag, contentDescription = null) }
                )
            }

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No notes found in this view", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Transcribed recordings will automatically appear in Daily Notes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredNotes, key = { it.file.absolutePath }) { note ->
                        VaultNoteCard(
                            note = note,
                            onClick = { selectedNoteToView = note }
                        )
                    }
                }
            }
        }

        // New Note Dialog
        if (showNewNoteDialog) {
            var newTitle by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNewNoteDialog = false },
                title = { Text("Create New Topic Note") },
                text = {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Topic / Title (e.g. Simscape, Ideas)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                val file = vaultManager.saveTopicNote(newTitle, "# [[$newTitle]]\n\n#notes #topic\n\n")
                                showNewNoteDialog = false
                                refreshNotes()
                                selectedNoteToView = VaultNote(
                                    file = file,
                                    title = newTitle,
                                    relativePath = "topics/$newTitle.md",
                                    lastModifiedMs = System.currentTimeMillis(),
                                    isDailyNote = false,
                                    content = file.readText()
                                )
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewNoteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultNoteCard(
    note: VaultNote,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val modStr = remember(note.lastModifiedMs) { dateFormat.format(Date(note.lastModifiedMs)) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = if (note.isDailyNote) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (note.isDailyNote) Icons.Default.CalendarToday else Icons.Default.Tag,
                                contentDescription = null,
                                tint = if (note.isDailyNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (note.isDailyNote) "📓 ${note.title}" else "[[${note.title}]]",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Modified: $modStr",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Preview snippet
            val previewText = remember(note.content) {
                note.content.lines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .take(2)
                    .joinToString(" ")
                    .take(140)
            }
            if (previewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = previewText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }

            // Tags and Wiki Links
            if (note.tags.isNotEmpty() || note.outLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (tag in note.tags.take(4)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    for (link in note.outLinks.take(3)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "[[$link]]",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorViewer(
    note: VaultNote,
    vaultManager: VaultManager,
    storageManager: StorageManager,
    onClose: () -> Unit,
    onPlayAudio: (RecordingItem, Long) -> Unit,
    onOpenWikiLink: (String) -> Unit,
    onAskAiAboutNote: (String) -> Unit
) {
    val context = LocalContext.current
    var isEditMode by remember { mutableStateOf(false) }
    var currentContent by remember { mutableStateOf(note.content) }
    var parsedElements by remember(currentContent) { mutableStateOf(MarkdownParser.parse(currentContent)) }

    fun saveContent(newContent: String) {
        currentContent = newContent
        note.file.writeText(newContent, Charsets.UTF_8)
        parsedElements = MarkdownParser.parse(newContent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note.title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Notes")
                    }
                },
                actions = {
                    IconButton(onClick = { onAskAiAboutNote(currentContent) }) {
                        Icon(Icons.Default.Psychology, contentDescription = "Ask Gemma AI", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "View Markdown" else "Edit Markdown"
                        )
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, note.title)
                            putExtra(Intent.EXTRA_TEXT, currentContent)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Markdown Note"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Note")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isEditMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = currentContent,
                    onValueChange = { saveContent(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        saveContent(currentContent)
                        isEditMode = false
                        Toast.makeText(context, "Saved note", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Switch to Reader")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(parsedElements) { element ->
                    when (element) {
                        is MarkdownElement.Header -> {
                            val (fontSize, weight, topPad) = when (element.level) {
                                1 -> Triple(22.sp, FontWeight.Bold, 12.dp)
                                2 -> Triple(18.sp, FontWeight.Bold, 8.dp)
                                else -> Triple(15.sp, FontWeight.SemiBold, 4.dp)
                            }
                            Spacer(modifier = Modifier.height(topPad))
                            Text(
                                text = element.text,
                                fontSize = fontSize,
                                fontWeight = weight,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        is MarkdownElement.TaskItem -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = MarkdownParser.toggleTaskCheckbox(currentContent, element.lineIndex)
                                        saveContent(updated)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = element.isChecked,
                                    onCheckedChange = {
                                        val updated = MarkdownParser.toggleTaskCheckbox(currentContent, element.lineIndex)
                                        saveContent(updated)
                                    }
                                )
                                Text(
                                    text = element.text,
                                    fontSize = 14.sp,
                                    textDecoration = if (element.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (element.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        is MarkdownElement.AudioTimestampLine -> {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Match audio file and trigger playback
                                        val recordings = storageManager.listRecordings()
                                        val matched = recordings.firstOrNull { note.content.contains(it.baseName) }
                                            ?: recordings.firstOrNull()
                                        if (matched != null) {
                                            onPlayAudio(matched, element.seekMs)
                                        } else {
                                            Toast.makeText(context, "Playing from ${element.timestampStr}...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(element.timestampStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    if (element.language != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                element.language,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = element.text,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        is MarkdownElement.Quote -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = element.text,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        is MarkdownElement.Bullet -> {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(element.text, fontSize = 13.sp)
                            }
                        }
                        is MarkdownElement.Divider -> {
                            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        is MarkdownElement.Paragraph -> {
                            Text(
                                text = element.text,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
