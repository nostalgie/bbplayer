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
import java.net.URLDecoder

/**
 * Represents an entry in the grouped video list.
 */
sealed interface VideoListEntry {
    data class FolderHeader(
        val folderName: String,   // e.g. "Movies" or "Anime"
        val folderPath: String,   // full path for identification
        val depth: Int,           // 0 = top-level, 1 = subfolder, etc.
        val isExpanded: Boolean   // whether the folder is expanded or collapsed
    ) : VideoListEntry

    data class VideoEntry(
        val uriString: String,    // original URI string
        val fileName: String,     // display name
        val originalIndex: Int    // index in flat list for onPlayVideo callback
    ) : VideoListEntry
}

/**
 * Extract folder information from a URI string.
 * Returns Pair<displayPath, fileName> or null if parsing fails.
 * displayPath uses abbreviated storage names (SD1, SD2, VOL1, etc.)
 */
private fun extractFolderInfo(uriString: String): Pair<String, String>? {
    val uri = Uri.parse(uriString)
    return when (uri.scheme) {
        "file" -> {
            // file:///storage/emulated/0/Movies/video.mp4
            // → displayPath: SD1/Мультики
            val path = uri.path ?: return null
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) {
                val folderPath = path.substring(0, lastSlash)
                val fileName = uri.lastPathSegment.orEmpty()
                val displayPath = abbreviateFolderPath(folderPath)
                displayPath to fileName
            } else null
        }
        "content" -> {
            // content://.../document/primary%3AMovies%2Fvideo.mp4
            // lastPathSegment = "primary:Movies/video.mp4"
            val segment = uri.lastPathSegment ?: return null
            val colonIdx = segment.indexOf(':')
            val relative = if (colonIdx >= 0) segment.substring(colonIdx + 1) else segment
            val lastSlash = relative.lastIndexOf('/')
            if (lastSlash > 0) {
                val folder = relative.substring(0, lastSlash)
                val fileName = URLDecoder.decode(relative.substring(lastSlash + 1), "UTF-8")
                val displayPath = abbreviateFolderPath(folder)
                displayPath to fileName
            } else null
        }
        else -> null
    }
}

/**
 * Convert full folder path to abbreviated format with storage volume names.
 * Examples:
 * /storage/emulated/0/Movies/Animation → SD1/Мультики
 * /storage/XXXX-XXXX/Video → SD2/Видео
 * /mnt/media/0/Films → VOL1/Фильмы
 */
private fun abbreviateFolderPath(fullPath: String): String {
    println("abbreviateFolderPath called with: '$fullPath'")
    
    // Extract storage volume name and path
    val storagePattern = Regex("""^/storage/([^/]+)(?:/(.+))?$""")
    val matchResult = storagePattern.find(fullPath)
    
    val result = if (matchResult != null) {
        val volumeName = matchResult.groupValues[1]
        val relativePath = matchResult.groupValues[2].takeIf { it.isNotEmpty() }
        
        val abbreviatedVolume = when (volumeName) {
            "emulated" -> "SD1"  // Internal storage
            else -> {
                // Check if it's a removable SD card (format like XXXX-XXXX)
                if (volumeName.matches(Regex("""^[A-F0-9]{4}-[A-F0-9]{4}$"""))) {
                    "SD${volumeName.take(2)}"  // SD2, SD3, etc.
                } else {
                    "VOL${volumeName.take(2)}"  // VOL1, VOL2, etc.
                }
            }
        }
        
        if (relativePath != null) {
            "$abbreviatedVolume/$relativePath"
        } else {
            abbreviatedVolume
        }
    } else {
        // For non-standard paths, try to extract meaningful parts
        val parts = fullPath.split("/").filter { it.isNotEmpty() }
        when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> {
                val volume = parts[0].take(4)
                val pathParts = parts.subList(1, parts.size)
                "$volume/${pathParts.joinToString("/")}"
            }
        }
    }
    
    println("abbreviateFolderPath result: '$result'")
    return result
}

/**
 * Group video URIs by their folders and create VideoListEntry items.
 * @param expandedFolders Set of folder paths that are currently expanded
 */
private fun groupVideosByFolder(uris: List<String>, expandedFolders: Set<String> = emptySet()): List<VideoListEntry> {
    if (uris.isEmpty()) return emptyList()

    // 1. Extract folder info for each URI
    val folderMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
    val ungroupedFiles = mutableListOf<Pair<String, String>>()

    uris.forEachIndexed { index, uriString ->
        extractFolderInfo(uriString)?.let { (folderPath, fileName) ->
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(folderPath to fileName)
        } ?: run {
            // Failed to parse, put in ungrouped
            ungroupedFiles.add(uriString to "Видео ${index + 1}")
        }
    }
    
    // Debug output
    println("=== Debug: groupVideosByFolder ===")
    println("URIs: ${uris.size}")
    println("Folder map keys: ${folderMap.keys}")
    folderMap.forEach { (path, files) ->
        println("  '$path': ${files.size} files")
    }
    println("Ungrouped files: ${ungroupedFiles.size}")
    println("================================")

    // 2. Sort folder paths alphabetically
    val sortedFolderPaths = folderMap.keys.sorted()
    
    // 3. Calculate depths based on relative nesting
    val folderDepths = calculateFolderDepths(sortedFolderPaths)
    
    // 4. Build the result list
        val result = mutableListOf<VideoListEntry>()
        
        // Add grouped folders first
        sortedFolderPaths.forEach { folderPath ->
            val depth = folderDepths[folderPath] ?: 0
            val folderName = extractFolderName(folderPath)
            val isExpanded = expandedFolders.contains(folderPath)
            
            result.add(VideoListEntry.FolderHeader(
                folderName = folderName,
                folderPath = folderPath,
                depth = depth,
                isExpanded = isExpanded
            ))
            
            // Add videos in this folder only if it's expanded
            if (isExpanded) {
                folderMap[folderPath]?.forEach { (_, fileName) ->
                    // Find the original URI that matches this folder and filename
                    val matchingUri = uris.find { uri ->
                        val (extractedFolder, extractedFileName) = extractFolderInfo(uri) ?: Pair("", "")
                        extractedFolder == folderPath && extractedFileName == fileName
                    }
                    if (matchingUri != null) {
                        val originalIndex = uris.indexOf(matchingUri)
                        result.add(VideoListEntry.VideoEntry(
                            uriString = matchingUri,
                            fileName = fileName,
                            originalIndex = originalIndex
                        ))
                    }
                }
            }
        }
    
    // Add ungrouped files at the end with depth 0
    if (ungroupedFiles.isNotEmpty()) {
        result.add(VideoListEntry.FolderHeader(
            folderName = "Другие",
            folderPath = "other",
            depth = 0,
            isExpanded = true  // По умолчанию развернута
        ))
        ungroupedFiles.forEachIndexed { _, (uriString, fileName) ->
            val originalIndex = uris.indexOf(uriString)
            result.add(VideoListEntry.VideoEntry(
                uriString = uriString,
                fileName = fileName,
                originalIndex = originalIndex
            ))
        }
    }
    
    return result
}

/**
 * Calculate folder depths based on relative nesting.
 * Returns Map<folderPath, depth>
 */
private fun calculateFolderDepths(folderPaths: List<String>): Map<String, Int> {
    val depths = mutableMapOf<String, Int>()
    
    if (folderPaths.isEmpty()) return depths
    
    // Find the shallowest depth (minimum number of path segments)
    val minSegments = folderPaths.minOf { path ->
        path.count { it == '/' }
    }
    
    // Calculate relative depth from shallowest
    folderPaths.forEach { path ->
        val segments = path.count { it == '/' }
        depths[path] = segments - minSegments
    }
    
    return depths
}

/**
 * Extract folder name from full path (using abbreviated format).
 */
private fun extractFolderName(folderPath: String): String {
    println("extractFolderName called with: '$folderPath'")
    
    // Return the full path instead of just the last part
    val result = folderPath
    
    println("extractFolderName result: '$result'")
    return result
}

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
    val expandedFolders by videoRepository.expandedFolders.collectAsStateWithLifecycle(initialValue = emptySet())

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
                        // Transform flat URI list into grouped entries
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
                                    is VideoListEntry.FolderHeader -> FolderHeaderItem(
                                        folderName = entry.folderName,
                                        isExpanded = entry.isExpanded,
                                        onToggle = {
                                            val updatedFolders = if (entry.isExpanded) {
                                                expandedFolders - entry.folderPath
                                            } else {
                                                expandedFolders + entry.folderPath
                                            }
                                            coroutineScope.launch {
                                                videoRepository.saveExpandedFolders(updatedFolders)
                                            }
                                        }
                                    )
                                    is VideoListEntry.VideoEntry -> VideoListItem(
                                        index = entry.originalIndex,
                                        uriString = entry.uriString,
                                        onClick = { pendingPlayIndex = entry.originalIndex },
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
 * Folder header item in the grouped list.
 */
@Composable
private fun FolderHeaderItem(
    folderName: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            color = FolderBlue
        )
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
