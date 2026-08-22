package com.example.recme.ui.screens.chat

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recme.ai.chat.AudioCitation
import com.example.recme.ai.chat.ChatMessage
import com.example.recme.ai.chat.MessageSender
import com.example.recme.ai.chat.VaultChatManager
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.StorageManager
import com.example.recme.vault.MarkdownElement
import com.example.recme.vault.MarkdownParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(
    initialContext: String? = null,
    onPlayAudioCitation: (RecordingItem, Long) -> Unit
) {
    val context = LocalContext.current
    val chatManager = remember { VaultChatManager(context) }
    val storageManager = remember { StorageManager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val defaultGreeting = remember {
        ChatMessage(
            sender = MessageSender.GEMMA_AI,
            text = "👋 Hello Jan! I am your local Gemma second-brain assistant. Ask me anything about your voice recordings, daily journal notes, or action items in **English**, **Afrikaans**, or **German**."
        )
    }

    val messages = remember {
        mutableStateListOf(defaultGreeting)
    }

    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Load persisted chat history on start
    LaunchedEffect(Unit) {
        val history = chatManager.loadHistory()
        if (history.isNotEmpty()) {
            messages.clear()
            messages.addAll(history)
        }
    }

    fun sendMessage(promptText: String, customContext: String? = null) {
        if (promptText.isBlank() || isGenerating) return

        val userMsg = ChatMessage(sender = MessageSender.USER, text = promptText)
        messages.add(userMsg)
        inputText = ""
        isGenerating = true

        scope.launch {
            try {
                chatManager.saveHistory(messages.toList())
                val response = chatManager.askAssistant(promptText, customContext ?: initialContext)
                messages.add(response)
                chatManager.saveHistory(messages.toList())
            } catch (e: Exception) {
                val errorMsg = ChatMessage(sender = MessageSender.GEMMA_AI, text = "⚠️ Error: ${e.message}")
                messages.add(errorMsg)
                chatManager.saveHistory(messages.toList())
            } finally {
                isGenerating = false
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Ask RecMe AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Persistent Chat • Local & Private", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    // Export to Obsidian Note
                    IconButton(
                        onClick = {
                            if (messages.isNotEmpty()) {
                                val file = chatManager.exportHistoryToMarkdown(messages.toList())
                                Toast.makeText(context, "Saved chat note to ${file.name}", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Export Chat Note", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Clear Chat
                    IconButton(
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Chat History?") },
                text = { Text("This will delete the persisted chat log from your device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDialog = false
                            scope.launch {
                                chatManager.clearHistory()
                                messages.clear()
                                messages.add(defaultGreeting)
                                Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Action Suggestion Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SuggestionChip(
                        onClick = {
                            scope.launch {
                                messages.add(ChatMessage(sender = MessageSender.USER, text = "Summarize Today's Notes"))
                                isGenerating = true
                                val res = chatManager.summarizeToday()
                                messages.add(res)
                                isGenerating = false
                            }
                        },
                        label = { Text("📝 Summarize Today", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = {
                            scope.launch {
                                messages.add(ChatMessage(sender = MessageSender.USER, text = "Extract Action Items"))
                                isGenerating = true
                                val res = chatManager.extractActionItems()
                                messages.add(res)
                                isGenerating = false
                            }
                        },
                        label = { Text("✅ Action Items", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = {
                            sendMessage("Gee vir my 'n Afrikaanse opsomming van vandag se notas en gesprekke.")
                        },
                        label = { Text("🇿🇦 Afrikaans", fontSize = 12.sp) }
                    )
                }
                item {
                    SuggestionChip(
                        onClick = {
                            sendMessage("Fasse die heutigen Notizen und Gespräche auf Deutsch zusammen.")
                        },
                        label = { Text("🇩🇪 Deutsch", fontSize = 12.sp) }
                    )
                }
            }

            // Message History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onCitationClick = { citation ->
                            val recordings = storageManager.listRecordings()
                            val match = recordings.firstOrNull()
                            if (match != null) {
                                onPlayAudioCitation(match, citation.seekMs)
                            } else {
                                Toast.makeText(context, "Playing audio at ${citation.timestampStr}...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemma is reasoning...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask anything about your vault...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isGenerating
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCitationClick: (AudioCitation) -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gemma 4", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Render Markdown Elements in Chat Bubble
                val elements = remember(message.text) { MarkdownParser.parse(message.text) }
                for (element in elements) {
                    when (element) {
                        is MarkdownElement.Header -> {
                            Text(
                                text = element.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (element.level == 1) 16.sp else 14.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        is MarkdownElement.TaskItem -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Text(if (element.isChecked) "☑ " else "☐ ", color = MaterialTheme.colorScheme.primary)
                                Text(element.text, fontSize = 13.sp)
                            }
                        }
                        is MarkdownElement.Bullet -> {
                            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                Text("• ", color = MaterialTheme.colorScheme.primary)
                                Text(element.text, fontSize = 13.sp)
                            }
                        }
                        else -> {
                            Text(
                                text = (element as? MarkdownElement.Paragraph)?.text ?: "",
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Clickable Audio Jump Citations
                if (message.citedAudioTimestamps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (citation in message.citedAudioTimestamps) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onCitationClick(citation) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        "Jump to ${citation.timestampStr}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
