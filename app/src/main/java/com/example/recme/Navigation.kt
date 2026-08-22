package com.example.recme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.recme.storage.RecordingItem
import com.example.recme.ui.screens.MainRecorderScreen
import com.example.recme.ui.screens.PlayerScreen
import com.example.recme.ui.screens.RecordingsScreen
import com.example.recme.ui.screens.SettingsScreen
import com.example.recme.ui.screens.chat.AskAiScreen
import com.example.recme.ui.screens.vault.VaultScreen

/**
 * Screen destinations in RecMe.
 */
sealed interface Screen {
    data object Recorder : Screen
    data object Vault : Screen
    data class AskAi(val initialContext: String? = null) : Screen
    data object Recordings : Screen
    data class Player(val item: RecordingItem, val initialSeekMs: Long = 0L) : Screen
    data object Settings : Screen
}

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Recorder) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Recorder

    val isTopLevelScreen = currentScreen is Screen.Recorder ||
                          currentScreen is Screen.Vault ||
                          currentScreen is Screen.AskAi

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.size - 1)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        bottomBar = {
            if (isTopLevelScreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Recorder,
                        onClick = {
                            if (currentScreen !is Screen.Recorder) {
                                backStack.clear()
                                backStack.add(Screen.Recorder)
                            }
                        },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Recorder") },
                        label = { Text("Recorder") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Vault,
                        onClick = {
                            if (currentScreen !is Screen.Vault) {
                                backStack.clear()
                                backStack.add(Screen.Vault)
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Vault") },
                        label = { Text("Vault") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.AskAi,
                        onClick = {
                            if (currentScreen !is Screen.AskAi) {
                                backStack.clear()
                                backStack.add(Screen.AskAi())
                            }
                        },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = "Ask AI") },
                        label = { Text("Ask AI") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                when (screen) {
                    is Screen.Recorder -> {
                        MainRecorderScreen(
                            onNavigateToRecordings = { backStack.add(Screen.Recordings) },
                            onNavigateToSettings = { backStack.add(Screen.Settings) }
                        )
                    }
                    is Screen.Vault -> {
                        VaultScreen(
                            onPlayAudioSegment = { item, seekMs ->
                                backStack.add(Screen.Player(item, seekMs))
                            },
                            onNavigateToAskAiWithContext = { ctx ->
                                backStack.add(Screen.AskAi(initialContext = ctx))
                            }
                        )
                    }
                    is Screen.AskAi -> {
                        AskAiScreen(
                            initialContext = screen.initialContext,
                            onPlayAudioCitation = { item, seekMs ->
                                backStack.add(Screen.Player(item, seekMs))
                            }
                        )
                    }
                    is Screen.Recordings -> {
                        RecordingsScreen(
                            onNavigateBack = { backStack.removeAt(backStack.size - 1) },
                            onPlayRecording = { item -> backStack.add(Screen.Player(item)) }
                        )
                    }
                    is Screen.Player -> {
                        PlayerScreen(
                            recordingItem = screen.item,
                            initialSeekMs = screen.initialSeekMs,
                            onNavigateBack = { backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            onNavigateBack = { backStack.removeAt(backStack.size - 1) }
                        )
                    }
                }
            }
        }
    }
}
