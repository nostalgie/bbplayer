package com.dima.kidsvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.kidsvideoplayer.data.VideoEntry
import com.dima.kidsvideoplayer.data.VideoLibraryService
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.PinDialog
import com.dima.kidsvideoplayer.ui.components.VerticalScrollbar
import com.dima.kidsvideoplayer.ui.screens.dashboard.buildLibraryIndexByPath
import com.dima.kidsvideoplayer.ui.screens.dashboard.buildVideosByParentPath
import com.dima.kidsvideoplayer.ui.screens.dashboard.isPathWithinWatchedFolders
import com.dima.kidsvideoplayer.ui.screens.dashboard.parentBrowsePath
import com.dima.kidsvideoplayer.ui.screens.dashboard.videoCountForFolder
import com.dima.kidsvideoplayer.ui.screens.filepicker.listSubdirectories
import com.dima.kidsvideoplayer.ui.theme.CardSurface
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.ExitRed
import com.dima.kidsvideoplayer.ui.theme.FolderBlue
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton
import com.dima.kidsvideoplayer.utils.abbreviateFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BUTTON_WIDTH = 88.dp
private val BUTTON_HEIGHT = 40.dp
private val BUTTON_FONT_SIZE = 12.sp

private enum class PinAction {
    EXIT,
    ADD_VIDEOS
}

@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onPlayVideo: (Int) -> Unit = {},
    onExit: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()

    var pendingPinAction by remember { mutableStateOf<PinAction?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingPlayIndex by remember { mutableStateOf(-1) }
    var pendingRemoveFolder by remember { mutableStateOf<String?>(null) }
    var showUnsupported by remember { mutableStateOf(false) }
    var browsePath by remember { mutableStateOf<String?>(null) }

    val videoListState = rememberLazyListState()
    val allVideos = libraryState.videos

    LaunchedEffect(watchedFolders, browsePath) {
        if (browsePath != null && !isPathWithinWatchedFolders(browsePath!!, watchedFolders)) {
            browsePath = null
        }
    }

    fun requestPin(action: PinAction) {
        pendingPinAction = action
    }

    pendingPinAction?.let { action ->
        PinDialog(
            title = when (action) {
                PinAction.EXIT -> "Введите ПИН для выхода"
                PinAction.ADD_VIDEOS -> "Введите ПИН для добавления папок"
            },
            onDismiss = { pendingPinAction = null },
            onPinCorrect = {
                when (action) {
                    PinAction.EXIT -> onExit()
                    PinAction.ADD_VIDEOS -> onNavigateToFilePicker()
                }
                pendingPinAction = null
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "Удалить все") },
            text = { Text(text = "Вы уверены? Все папки будут удалены из библиотеки.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        browsePath = null
                        coroutineScope.launch { videoRepository.clearAll() }
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

    pendingRemoveFolder?.let { folderPath ->
        AlertDialog(
            onDismissRequest = { pendingRemoveFolder = null },
            title = { Text(text = "Удалить папку") },
            text = { Text(text = "Убрать папку из библиотеки?\n$folderPath") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (browsePath != null &&
                            (browsePath == folderPath || browsePath!!.startsWith("$folderPath/"))
                        ) {
                            browsePath = null
                        }
                        coroutineScope.launch {
                            videoRepository.removeWatchedFolder(folderPath)
                        }
                        pendingRemoveFolder = null
                    }
                ) {
                    Text("Да", color = RedButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveFolder = null }) {
                    Text("Нет")
                }
            }
        )
    }

    if (pendingPlayIndex >= 0) {
        AlertDialog(
            onDismissRequest = { pendingPlayIndex = -1 },
            title = { Text(text = "Воспроизвести") },
            text = { Text(text = "Включить это видео?") },
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
        ScanStatusBar(
            libraryState = libraryState,
            onRefresh = { videoLibraryService.scanNow() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isPortrait) {
            DashboardActionButtons(
                folderCount = watchedFolders.size,
                horizontal = true,
                onBackToKidMode = onBackToKidMode,
                onAddFolders = { requestPin(PinAction.ADD_VIDEOS) },
                onClearAll = { showClearAllDialog = true },
                onExit = { requestPin(PinAction.EXIT) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            DashboardVideoList(
                watchedFolders = watchedFolders,
                allVideos = allVideos,
                browsePath = browsePath,
                onBrowsePathChange = { browsePath = it },
                unsupportedFiles = libraryState.unsupportedFiles,
                inaccessibleFolders = libraryState.inaccessibleFolders,
                showUnsupported = showUnsupported,
                onToggleUnsupported = { showUnsupported = !showUnsupported },
                videoListState = videoListState,
                onRequestAddFolders = { requestPin(PinAction.ADD_VIDEOS) },
                onPlayVideo = { pendingPlayIndex = it },
                onRemoveFolder = { pendingRemoveFolder = it },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardVideoList(
                    watchedFolders = watchedFolders,
                    allVideos = allVideos,
                    browsePath = browsePath,
                    onBrowsePathChange = { browsePath = it },
                    unsupportedFiles = libraryState.unsupportedFiles,
                    inaccessibleFolders = libraryState.inaccessibleFolders,
                    showUnsupported = showUnsupported,
                    onToggleUnsupported = { showUnsupported = !showUnsupported },
                    videoListState = videoListState,
                    onRequestAddFolders = { requestPin(PinAction.ADD_VIDEOS) },
                    onPlayVideo = { pendingPlayIndex = it },
                    onRemoveFolder = { pendingRemoveFolder = it },
                    modifier = Modifier.weight(1f)
                )
                DashboardActionButtons(
                    folderCount = watchedFolders.size,
                    horizontal = false,
                    onBackToKidMode = onBackToKidMode,
                    onAddFolders = { requestPin(PinAction.ADD_VIDEOS) },
                    onClearAll = { showClearAllDialog = true },
                    onExit = { requestPin(PinAction.EXIT) }
                )
            }
        }
    }
}

@Composable
private fun ScanStatusBar(
    libraryState: com.dima.kidsvideoplayer.data.LibraryState,
    onRefresh: () -> Unit
) {
    val timeText = libraryState.lastScanTime?.let { ts ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                libraryState.isScanning -> "Обновление библиотеки..."
                timeText != null -> "Обновлено: $timeText"
                else -> "Библиотека не сканировалась"
            },
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        TextButton(onClick = onRefresh, enabled = !libraryState.isScanning) {
            Text("Обновить", color = FolderBlue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DashboardActionButtons(
    folderCount: Int,
    horizontal: Boolean,
    onBackToKidMode: () -> Unit,
    onAddFolders: () -> Unit,
    onClearAll: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (horizontal) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashboardActionButton("Назад", onBackToKidMode, GreenPrimary, fillWidth = true, modifier = Modifier.weight(1f))
            DashboardActionButton("Добавить", onAddFolders, FolderBlue, fillWidth = true, modifier = Modifier.weight(1f))
            DashboardActionButton(
                "Удалить все",
                { if (folderCount > 0) onClearAll() },
                if (folderCount > 0) RedButton else Color.Gray,
                if (folderCount > 0) Color.White else Color.White.copy(alpha = 0.4f),
                fillWidth = true,
                modifier = Modifier.weight(1f)
            )
            DashboardActionButton("Выход", onExit, ExitRed, fillWidth = true, modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardActionButton("Назад", onBackToKidMode, GreenPrimary)
            DashboardActionButton("Добавить", onAddFolders, FolderBlue)
            DashboardActionButton(
                "Удалить все",
                { if (folderCount > 0) onClearAll() },
                if (folderCount > 0) RedButton else Color.Gray,
                if (folderCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
            )
            DashboardActionButton("Выход", onExit, ExitRed)
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
    watchedFolders: List<String>,
    allVideos: List<VideoEntry>,
    browsePath: String?,
    onBrowsePathChange: (String?) -> Unit,
    unsupportedFiles: List<String>,
    inaccessibleFolders: List<String>,
    showUnsupported: Boolean,
    onToggleUnsupported: () -> Unit,
    videoListState: LazyListState,
    onRequestAddFolders: () -> Unit,
    onPlayVideo: (Int) -> Unit,
    onRemoveFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val videosByParentPath = remember(allVideos) { buildVideosByParentPath(allVideos) }
    val libraryIndexByPath = remember(allVideos) { buildLibraryIndexByPath(allVideos) }

    var subdirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingSubdirs by remember { mutableStateOf(false) }

    LaunchedEffect(browsePath) {
        val path = browsePath ?: run {
            subdirectories = emptyList()
            loadingSubdirs = false
            return@LaunchedEffect
        }
        loadingSubdirs = true
        subdirectories = withContext(Dispatchers.IO) { listSubdirectories(path) }
        loadingSubdirs = false
    }

    Column(modifier = modifier) {
        if (watchedFolders.isNotEmpty()) {
            Text(
                text = "Папки (${watchedFolders.size}), видео (${allVideos.size}):",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (inaccessibleFolders.isNotEmpty() && browsePath == null) {
            Text(
                text = "⚠️ Недоступно папок: ${inaccessibleFolders.size}",
                fontSize = 13.sp,
                color = RedButton,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (watchedFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нажмите, чтобы добавить папки",
                    color = FolderBlue,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onRequestAddFolders() }.padding(16.dp)
                )
            }
        } else {
            if (browsePath != null) {
                FolderBrowseBar(
                    currentPath = browsePath,
                    onNavigateUp = {
                        onBrowsePathChange(parentBrowsePath(browsePath, watchedFolders))
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = videoListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (browsePath == null) {
                        items(
                            items = watchedFolders.sorted(),
                            key = { it }
                        ) { folderPath ->
                            RootFolderRow(
                                folderName = abbreviateFolderPath(folderPath),
                                videoCount = videoCountForFolder(allVideos, folderPath),
                                onEnter = { onBrowsePathChange(folderPath) },
                                onRemove = { onRemoveFolder(folderPath) }
                            )
                        }
                    } else {
                        if (loadingSubdirs) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.ProgressBar(ctx).apply {
                                                indeterminateTintList =
                                                    android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        items(
                            items = subdirectories,
                            key = { it }
                        ) { subdirPath ->
                            SubfolderRow(
                                folderName = File(subdirPath).name,
                                onEnter = { onBrowsePathChange(subdirPath) }
                            )
                        }

                        val videosHere = videosByParentPath[browsePath].orEmpty()
                            .sortedBy { it.fileName.lowercase() }

                        items(
                            items = videosHere,
                            key = { it.filePath }
                        ) { video ->
                            VideoListItem(
                                fileName = video.fileName,
                                onClick = {
                                    libraryIndexByPath[video.filePath]?.let { onPlayVideo(it) }
                                }
                            )
                        }

                        if (!loadingSubdirs && subdirectories.isEmpty() && videosHere.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = "Папка пуста",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    if (browsePath == null && unsupportedFiles.isNotEmpty()) {
                        item(key = "unsupported-toggle") {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onToggleUnsupported) {
                                Text(
                                    text = if (showUnsupported) {
                                        "Скрыть неподдерживаемые (${unsupportedFiles.size})"
                                    } else {
                                        "Неподдерживаемые (${unsupportedFiles.size})"
                                    },
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (showUnsupported) {
                            items(
                                items = unsupportedFiles,
                                key = { it }
                            ) { path ->
                                Text(
                                    text = "⚠️ ${path.substringAfterLast('/')}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(state = videoListState)
            }
        }
    }
}

@Composable
private fun FolderBrowseBar(
    currentPath: String,
    onNavigateUp: () -> Unit
) {
    Surface(
        onClick = onNavigateUp,
        shape = RoundedCornerShape(8.dp),
        color = CardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = File(currentPath).name.ifEmpty { abbreviateFolderPath(currentPath) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FolderBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RootFolderRow(
    folderName: String,
    videoCount: Int,
    onEnter: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = folderName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FolderBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$videoCount",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 8.dp)
            )
            Surface(
                onClick = onRemove,
                shape = RoundedCornerShape(8.dp),
                color = RedButton.copy(alpha = 0.2f)
            ) {
                Text(
                    text = " ✕ ",
                    fontSize = 16.sp,
                    color = RedButton,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SubfolderRow(
    folderName: String,
    onEnter: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📁", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = folderName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                fontSize = 20.sp,
                color = FolderBlue
            )
        }
    }
}

@Composable
private fun VideoListItem(
    fileName: String,
    onClick: () -> Unit
) {
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🎬", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
