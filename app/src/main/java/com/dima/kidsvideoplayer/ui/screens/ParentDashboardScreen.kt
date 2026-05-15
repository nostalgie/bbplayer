package com.dima.kidsvideoplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoCompatibilityChecker
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.VerticalScrollbar
import com.dima.kidsvideoplayer.ui.theme.CardSurface
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.ExitRed
import com.dima.kidsvideoplayer.ui.theme.FolderBlue
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton
import kotlinx.coroutines.launch

private val BUTTON_SIZE = 120.dp
private val BUTTON_HEIGHT = 60.dp

/**
 * Parent Dashboard Screen — manage videos and settings.
 *
 * Features:
 * - Add videos via file picker (folder or individual files)
 * - List of added videos with ability to remove
 * - "Back to Kid Mode" button
 * - Exit button with confirmation
 */
@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    @Suppress("UNUSED_PARAMETER") videoCompatibilityChecker: VideoCompatibilityChecker,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onPlayVideo: (Int) -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())

    // Exit confirmation dialog state
    var showExitDialog by remember { mutableStateOf(false) }
    // Clear all confirmation dialog state
    var showClearAllDialog by remember { mutableStateOf(false) }
    // Play video confirmation dialog state
    var pendingPlayIndex by remember { mutableStateOf(-1) }

    // Video list scroll state
    val videoListState = rememberLazyListState()

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(text = "Выход")
            },
            text = {
                Text(text = "Вы уверены?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                    }
                ) {
                    Text("Да", color = ExitRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Нет")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(text = "Удалить все")
            },
            text = {
                Text(text = "Вы уверены? Все видео будут удалены.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        coroutineScope.launch {
                            videoRepository.clearAll()
                        }
                    }
                ) {
                    Text("Да", color = RedButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Нет")
                }
            }
        )
    }

    // Play video confirmation dialog
    if (pendingPlayIndex >= 0) {
        AlertDialog(
            onDismissRequest = { pendingPlayIndex = -1 },
            title = {
                Text(text = "Воспроизвести")
            },
            text = {
                Text(text = "Включить это видео?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val index = pendingPlayIndex
                        pendingPlayIndex = -1
                        onPlayVideo(index)
                    }
                ) {
                    Text("Да", color = GreenPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayIndex = -1 }) {
                    Text("Нет")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        // ==============================
        // Main content: Video list on the left, buttons on the right
        // ==============================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left side: Video list
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (videoUris.isNotEmpty()) {
                    Text(
                        text = "Видео (${videoUris.size}):",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (videoUris.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нажмите, чтобы выбрать видео",
                            color = FolderBlue,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { onNavigateToFilePicker() }
                                .padding(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = videoListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = videoUris,
                                key = { _, uri -> uri }
                            ) { index, uriString ->
                                VideoListItem(
                                    index = index,
                                    uriString = uriString,
                                    onClick = { pendingPlayIndex = index },
                                    onRemove = {
                                        coroutineScope.launch {
                                            videoRepository.removeVideoUri(uriString)
                                        }
                                    }
                                )
                            }
                        }
                        VerticalScrollbar(state = videoListState)
                    }
                }
            }

            // Right side: buttons stacked vertically, all same size, no icons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BounceButton(
                    text = "Назад",
                    onClick = onBackToKidMode,
                    backgroundColor = GreenPrimary,
                    textColor = Color.White,
                    width = BUTTON_SIZE,
                    height = BUTTON_HEIGHT,
                    fontSize = 14.sp
                )

                BounceButton(
                    text = "Добавить",
                    onClick = onNavigateToFilePicker,
                    backgroundColor = FolderBlue,
                    textColor = Color.White,
                    width = BUTTON_SIZE,
                    height = BUTTON_HEIGHT,
                    fontSize = 14.sp
                )

                // "Удалить все" — active when videos exist, greyed out otherwise
                BounceButton(
                    text = "Удалить все",
                    onClick = { if (videoUris.isNotEmpty()) showClearAllDialog = true },
                    backgroundColor = if (videoUris.isNotEmpty()) RedButton else Color.Gray,
                    textColor = if (videoUris.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                    width = BUTTON_SIZE,
                    height = BUTTON_HEIGHT,
                    fontSize = 14.sp
                )

                BounceButton(
                    text = "Выйти",
                    onClick = { showExitDialog = true },
                    backgroundColor = ExitRed,
                    textColor = Color.White,
                    width = BUTTON_SIZE,
                    height = BUTTON_HEIGHT,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Single video item in the list.
 */
@Composable
private fun VideoListItem(
    index: Int,
    uriString: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val fileName = remember(uriString) {
        try {
            val uri = Uri.parse(uriString)
            uri.lastPathSegment ?: "Видео ${index + 1}"
        } catch (e: Exception) {
            "Видео ${index + 1}"
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Remove button
            Surface(
                onClick = onRemove,
                shape = RoundedCornerShape(8.dp),
                color = RedButton.copy(alpha = 0.2f)
            ) {
                Text(
                    text = " ✕ ",
                    fontSize = 18.sp,
                    color = RedButton,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
