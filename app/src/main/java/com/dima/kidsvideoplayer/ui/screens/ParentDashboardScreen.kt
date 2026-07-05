package com.dima.kidsvideoplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.PinDialog
import com.dima.kidsvideoplayer.ui.components.VerticalScrollbar
import com.dima.kidsvideoplayer.ui.screens.dashboard.VideoListEntry
import com.dima.kidsvideoplayer.ui.screens.dashboard.areAllVideosInFolderSelected
import com.dima.kidsvideoplayer.ui.screens.dashboard.getSelectedCountInFolder
import com.dima.kidsvideoplayer.ui.screens.dashboard.getVideosInFolder
import com.dima.kidsvideoplayer.ui.screens.dashboard.groupVideosByFolder
import com.dima.kidsvideoplayer.ui.theme.CardSurface
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.ExitRed
import com.dima.kidsvideoplayer.ui.theme.FolderBlue
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val BUTTON_WIDTH = 88.dp
private val BUTTON_HEIGHT = 40.dp
private val BUTTON_FONT_SIZE = 12.sp

private enum class PinAction {
    EXIT,
    ADD_VIDEOS
}

/**
 * Parent Dashboard Screen — manage videos and settings.
 *
 * Features:
 * - Add videos via file picker (folder or individual files) — PIN protected
 * - List of added videos with ability to remove
 * - "Back to Kid Mode" button
 * - Exit button — PIN protected
 */
@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onPlayVideo: (Int) -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())
    val expandedFolders by videoRepository.expandedFolders.collectAsStateWithLifecycle(initialValue = emptySet())
    val selectedVideos by videoRepository.selectedVideos.collectAsStateWithLifecycle(initialValue = emptySet())

    // PIN-protected action state
    var pendingPinAction by remember { mutableStateOf<PinAction?>(null) }
    // Clear all confirmation dialog state
    var showClearAllDialog by remember { mutableStateOf(false) }
    // Play video confirmation dialog state
    var pendingPlayIndex by remember { mutableStateOf(-1) }

    // Video list scroll state
    val videoListState = rememberLazyListState()

    fun requestPin(action: PinAction) {
        pendingPinAction = action
    }

    pendingPinAction?.let { action ->
        PinDialog(
            title = when (action) {
                PinAction.EXIT -> "Введите ПИН для снятия киоска"
                PinAction.ADD_VIDEOS -> "Введите ПИН для добавления видео"
            },
            onDismiss = { pendingPinAction = null },
            onPinCorrect = {
                when (action) {
                    PinAction.EXIT -> onExitApp()
                    PinAction.ADD_VIDEOS -> onNavigateToFilePicker()
                }
                pendingPinAction = null
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

    val isPortrait = LocalConfiguration.current.screenHeightDp > LocalConfiguration.current.screenWidthDp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(12.dp)
    ) {
        if (isPortrait) {
            DashboardActionButtons(
                videoCount = videoUris.size,
                horizontal = true,
                onBackToKidMode = onBackToKidMode,
                onAddVideos = { requestPin(PinAction.ADD_VIDEOS) },
                onClearAll = { showClearAllDialog = true },
                onExitKiosk = { requestPin(PinAction.EXIT) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            DashboardVideoList(
                videoUris = videoUris,
                expandedFolders = expandedFolders,
                selectedVideos = selectedVideos,
                videoListState = videoListState,
                videoRepository = videoRepository,
                coroutineScope = coroutineScope,
                onRequestAddVideos = { requestPin(PinAction.ADD_VIDEOS) },
                onPlayVideo = { pendingPlayIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardVideoList(
                    videoUris = videoUris,
                    expandedFolders = expandedFolders,
                    selectedVideos = selectedVideos,
                    videoListState = videoListState,
                    videoRepository = videoRepository,
                    coroutineScope = coroutineScope,
                    onRequestAddVideos = { requestPin(PinAction.ADD_VIDEOS) },
                    onPlayVideo = { pendingPlayIndex = it },
                    modifier = Modifier.weight(1f)
                )
                DashboardActionButtons(
                    videoCount = videoUris.size,
                    horizontal = false,
                    onBackToKidMode = onBackToKidMode,
                    onAddVideos = { requestPin(PinAction.ADD_VIDEOS) },
                    onClearAll = { showClearAllDialog = true },
                    onExitKiosk = { requestPin(PinAction.EXIT) }
                )
            }
        }
    }
}

@Composable
private fun DashboardActionButtons(
    videoCount: Int,
    horizontal: Boolean,
    onBackToKidMode: () -> Unit,
    onAddVideos: () -> Unit,
    onClearAll: () -> Unit,
    onExitKiosk: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (horizontal) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardActionButton(
                text = "Назад",
                onClick = onBackToKidMode,
                backgroundColor = GreenPrimary,
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
            DashboardActionButton(
                text = "Добавить",
                onClick = onAddVideos,
                backgroundColor = FolderBlue,
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
            DashboardActionButton(
                text = "Удалить все",
                onClick = { if (videoCount > 0) onClearAll() },
                backgroundColor = if (videoCount > 0) RedButton else Color.Gray,
                textColor = if (videoCount > 0) Color.White else Color.White.copy(alpha = 0.4f),
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
            DashboardActionButton(
                text = "Снять киоск",
                onClick = onExitKiosk,
                backgroundColor = ExitRed,
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardActionButton(
                text = "Назад",
                onClick = onBackToKidMode,
                backgroundColor = GreenPrimary
            )
            DashboardActionButton(
                text = "Добавить",
                onClick = onAddVideos,
                backgroundColor = FolderBlue
            )
            DashboardActionButton(
                text = "Удалить все",
                onClick = { if (videoCount > 0) onClearAll() },
                backgroundColor = if (videoCount > 0) RedButton else Color.Gray,
                textColor = if (videoCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
            )
            DashboardActionButton(
                text = "Снять киоск",
                onClick = onExitKiosk,
                backgroundColor = ExitRed
            )
        }
    }
}

@Composable
private fun DashboardActionButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color = Color.White,
    fillWidth: Boolean = false,
    modifier: Modifier = Modifier
) {
    BounceButton(
        text = text,
        onClick = onClick,
        backgroundColor = backgroundColor,
        textColor = textColor,
        width = if (fillWidth) Dp.Unspecified else BUTTON_WIDTH,
        height = BUTTON_HEIGHT,
        fontSize = BUTTON_FONT_SIZE,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier
    )
}

@Composable
private fun DashboardVideoList(
    videoUris: List<String>,
    expandedFolders: Set<String>,
    selectedVideos: Set<String>,
    videoListState: LazyListState,
    videoRepository: VideoRepository,
    coroutineScope: CoroutineScope,
    onRequestAddVideos: () -> Unit,
    onPlayVideo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (videoUris.isNotEmpty()) {
            Text(
                text = "Видео (${videoUris.size}):",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            val selectedCount = selectedVideos.size
            Text(
                text = "Выбрано: $selectedCount",
                fontSize = 14.sp,
                color = if (selectedCount > 0) GreenPrimary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp)
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
                        .clickable { onRequestAddVideos() }
                        .padding(16.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val groupedEntries = remember(videoUris, expandedFolders) {
                    groupVideosByFolder(videoUris, expandedFolders)
                }

                LazyColumn(
                    state = videoListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(groupedEntries) { _, entry ->
                        when (entry) {
                            is VideoListEntry.FolderHeader -> {
                                val (selectedCount, totalCount) = getSelectedCountInFolder(
                                    entry.folderPath,
                                    videoUris,
                                    selectedVideos
                                )
                                FolderHeaderItem(
                                    folderName = entry.folderName,
                                    isExpanded = entry.isExpanded,
                                    isSelected = areAllVideosInFolderSelected(
                                        entry.folderPath,
                                        videoUris,
                                        selectedVideos
                                    ),
                                    selectedCount = selectedCount,
                                    totalCount = totalCount,
                                    onToggle = {
                                        val updatedFolders = if (entry.isExpanded) {
                                            expandedFolders - entry.folderPath
                                        } else {
                                            expandedFolders + entry.folderPath
                                        }
                                        coroutineScope.launch {
                                            videoRepository.saveExpandedFolders(updatedFolders)
                                        }
                                    },
                                    onToggleSelection = {
                                        coroutineScope.launch {
                                            toggleAllVideosInFolder(
                                                entry.folderPath,
                                                videoRepository,
                                                videoUris,
                                                selectedVideos
                                            )
                                        }
                                    }
                                )
                            }
                            is VideoListEntry.VideoEntry -> VideoListItem(
                                index = entry.originalIndex,
                                uriString = entry.uriString,
                                isSelected = selectedVideos.contains(entry.uriString),
                                onClick = { onPlayVideo(entry.originalIndex) },
                                onToggleSelection = {
                                    coroutineScope.launch {
                                        videoRepository.toggleVideoSelection(entry.uriString)
                                    }
                                },
                                onRemove = {
                                    coroutineScope.launch {
                                        videoRepository.removeVideoUri(entry.uriString)
                                    }
                                }
                            )
                        }
                    }
                }
                VerticalScrollbar(state = videoListState)
            }
        }
    }
}

/**
 * Folder header item in the grouped list.
 */
@Composable
private fun FolderHeaderItem(
    folderName: String,
    isExpanded: Boolean,
    isSelected: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onToggle: () -> Unit,
    onToggleSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Surface(
            onClick = onToggleSelection,
            shape = RoundedCornerShape(4.dp),
            color = if (isSelected) GreenPrimary else Color.Gray.copy(alpha = 0.3f)
        ) {
            Text(
                text = if (isSelected) "✓" else "○",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Toggle icon
        Text(
            text = if (isExpanded) "▼" else "▶",
            fontSize = 12.sp,
            color = FolderBlue,
            modifier = Modifier.padding(end = 4.dp)
        )
        
        // Folder icon and name
        Text(
            text = "📁 $folderName",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = FolderBlue,
            modifier = Modifier
                .weight(1f)
                .padding(end = 60.dp) // Reserve space for the counter
        )
        
        // Selection count
        if (totalCount > 0) {
            Text(
                text = "$selectedCount/$totalCount",
                fontSize = 11.sp,
                color = if (selectedCount > 0) GreenPrimary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 12.dp) // Keep some margin from the right edge
            )
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
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
        color = if (isSelected) CardSurface.copy(alpha = 0.8f) else CardSurface,
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
            // Checkbox
            Surface(
                onClick = onToggleSelection,
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) GreenPrimary else Color.Gray.copy(alpha = 0.3f)
            ) {
                Text(
                    text = if (isSelected) "✓" else "○",
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Text(
                text = fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
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

private suspend fun toggleAllVideosInFolder(
    folderPath: String,
    videoRepository: VideoRepository,
    videoUris: List<String>,
    selectedVideos: Set<String>
) {
    val videosInFolder = getVideosInFolder(folderPath, videoUris)
    if (videosInFolder.isEmpty()) return

    val allSelected = videosInFolder.all { it in selectedVideos }
    if (allSelected) {
        videoRepository.saveSelectedVideos(selectedVideos - videosInFolder.toSet())
    } else {
        videoRepository.saveSelectedVideos(selectedVideos + videosInFolder.toSet())
    }
}
