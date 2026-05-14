package com.dima.kidsvideoplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import com.dima.kidsvideoplayer.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

// Supported video file extensions
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "ts", "mpg", "mpeg", "rmvb", "vob"
)

/** Root directory for the file browser */
private const val ROOT_PATH = "/storage/emulated/0/"

/**
 * Custom file browser screen for batch video selection.
 *
 * Allows selecting individual video files and entire folders (selects all videos
 * inside recursively). Uses java.io.File for filesystem browsing and converts
 * selected files to URI strings for storage via VideoRepository.
 *
 * @param videoRepository Repository for persisting video URIs
 * @param onBack Callback to navigate back to parent dashboard
 */
@Composable
fun FilePickerScreen(
    videoRepository: VideoRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permission state
    val hasStoragePermission = remember {
        mutableStateOf(checkStoragePermission())
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE settings screen (API 30+)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permission when returning from settings
        hasStoragePermission.value = checkStoragePermission()
    }

    // Launcher for legacy runtime permissions (API < 30)
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        hasStoragePermission.value = granted
    }

    // Current directory path
    var currentPath by remember { mutableStateOf(ROOT_PATH) }

    // Set of selected file absolute paths — immutable set to ensure recomposition
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }

    // Loading state
    var isLoading by remember { mutableStateOf(false) }

    // Error message
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // List of items in current directory
    var directoryItems by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }

    // Cached video file paths for folders: folder path -> list of video paths (null means still loading)
    val folderVideoPaths = remember { mutableStateOf<Map<String, List<String>?>>(emptyMap()) }

    // Load directory contents when path changes
    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        try {
            val items = withContext(Dispatchers.IO) {
                listDirectoryItems(currentPath)
            }
            directoryItems = items
            // Initialize folder video paths as null (loading)
            folderVideoPaths.value = items
                .filter { it.isDirectory }
                .associate { it.path to null }
            // Launch async loading of video paths for each folder
            for (item in items.filter { it.isDirectory }) {
                launch {
                    val videos = withContext(Dispatchers.IO) {
                        findVideosRecursively(File(item.path))
                    }
                    val paths = videos.map { it.absolutePath }
                    val current = folderVideoPaths.value.toMutableMap()
                    current[item.path] = paths
                    folderVideoPaths.value = current
                }
            }
        } catch (e: SecurityException) {
            errorMessage = "Нет доступа к директории"
            directoryItems = emptyList()
        } catch (e: Exception) {
            errorMessage = "Ошибка: ${e.message}"
            directoryItems = emptyList()
        }
        isLoading = false
    }

    // Permission request screen or file browser
    if (!hasStoragePermission.value) {
        PermissionRequestScreen(
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+: Open system settings for MANAGE_EXTERNAL_STORAGE
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:com.dima.kidsvideoplayer")
                        )
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        // Fallback to generic settings
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                } else {
                    // API < 30: Request runtime permissions
                    runtimePermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    )
                }
            },
            onBack = onBack
        )
        return
    }

    // ==============================
    // File Browser UI
    // ==============================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Top bar with navigation
        FilePickerTopBar(
            currentPath = currentPath,
            onNavigateUp = {
                val parent = File(currentPath).parentFile
                if (parent != null && parent.canRead()) {
                    currentPath = parent.absolutePath
                }
            },
            onBack = onBack
        )

        // Select All / Deselect All buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                // Select all: all folders and all video files in current directory
                coroutineScope.launch {
                    val allPaths = mutableSetOf<String>()
                    allPaths.addAll(selectedFiles)
                    for (folder in directoryItems.filter { it.isDirectory }) {
                        val cached = folderVideoPaths.value[folder.path]
                        if (cached != null) {
                            allPaths.addAll(cached)
                        } else {
                            val videos = withContext(Dispatchers.IO) {
                                findVideosRecursively(File(folder.path))
                            }
                            allPaths.addAll(videos.map { it.absolutePath })
                        }
                    }
                    for (file in directoryItems.filter { it.isFile }) {
                        allPaths.add(file.path)
                    }
                    selectedFiles = allPaths.toSet()
                }
            }) {
                Text("Выбрать все", color = Color(0xFF4CAF50))
            }
            TextButton(onClick = {
                selectedFiles = emptySet()
            }) {
                Text("Снять все", color = Color(0xFFEF5350))
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.ProgressBar(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
                        }
                    },
                    modifier = Modifier.size(48.dp)
                )
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        } else if (directoryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Папка пуста",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp
                )
            }
        } else {
            // File/folder list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val folders = directoryItems.filter { it.isDirectory }
                val files = directoryItems.filter { it.isFile }

                // Folders first
                items(items = folders, key = { it.path }) { item ->
                    val cachedPaths = folderVideoPaths.value[item.path]
                    val videoCount = cachedPaths?.size
                    val selectedCount = if (cachedPaths != null) {
                        cachedPaths.count { it in selectedFiles }
                    } else 0

                    val toggleableState = when {
                        cachedPaths == null -> ToggleableState.Off
                        cachedPaths.isEmpty() -> ToggleableState.Off
                        selectedCount == 0 -> ToggleableState.Off
                        selectedCount == cachedPaths.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                    FolderItem(
                        item = item,
                        videoCount = videoCount,
                        selectedCount = selectedCount,
                        toggleableState = toggleableState,
                        onSelect = {
                            val paths = cachedPaths
                            if (paths != null) {
                                if (toggleableState == ToggleableState.On) {
                                    // Deselect all videos in this folder
                                    selectedFiles = selectedFiles - paths.toSet()
                                } else {
                                    // Select all videos in this folder
                                    selectedFiles = selectedFiles + paths
                                }
                            } else {
                                // Still loading — find videos asynchronously
                                coroutineScope.launch {
                                    val videos = withContext(Dispatchers.IO) {
                                        findVideosRecursively(File(item.path))
                                    }
                                    val foundPaths = videos.map { it.absolutePath }
                                    selectedFiles = selectedFiles + foundPaths
                                }
                            }
                        },
                        onClick = {
                            // Navigate into folder
                            currentPath = item.path
                        }
                    )
                }

                // Video files
                items(items = files, key = { it.path }) { item ->
                    VideoFileItem(
                        item = item,
                        isSelected = item.path in selectedFiles,
                        onSelect = {
                            if (item.path in selectedFiles) {
                                selectedFiles = selectedFiles - item.path
                            } else {
                                selectedFiles = selectedFiles + item.path
                            }
                        }
                    )
                }
            }
        }

        // Bottom bar with selection info and action buttons
        // Compute selected folder count: folders where all videos are selected
        val selectedFolderCount = folderVideoPaths.value.count { (_, paths) ->
            paths != null && paths.isNotEmpty() && paths.all { it in selectedFiles }
        }

        FilePickerBottomBar(
            selectedFileCount = selectedFiles.size,
            selectedFolderCount = selectedFolderCount,
            onConfirm = {
                coroutineScope.launch {
                    val uris = selectedFiles.map { path ->
                        File(path).toURI().toString()
                    }
                    if (uris.isNotEmpty()) {
                        videoRepository.addVideoUris(uris)
                    }
                    onBack()
                }
            },
            onCancel = onBack
        )
    }
}

/**
 * Check if storage permission is granted.
 */
private fun checkStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // For older APIs, we'll request at runtime if needed
    }
}

// ==============================
// Data Models
// ==============================

/**
 * Represents a file system item (file or directory).
 */
data class FileSystemItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean = !isDirectory,
    val size: Long = 0
)

// ==============================
// File System Helper Functions
// ==============================

/**
 * List all folders and video files in the given directory.
 * Folders are listed first, then video files, both sorted alphabetically.
 */
private fun listDirectoryItems(dirPath: String): List<FileSystemItem> {
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
        return emptyList()
    }

    val items = mutableListOf<FileSystemItem>()
    val files = dir.listFiles() ?: return emptyList()

    for (file in files) {
        val name = file.name
        // Skip hidden files/directories
        if (name.startsWith(".")) continue

        if (file.isDirectory) {
            items.add(
                FileSystemItem(
                    name = name,
                    path = file.absolutePath,
                    isDirectory = true
                )
            )
        } else if (isVideoFile(name)) {
            items.add(
                FileSystemItem(
                    name = name,
                    path = file.absolutePath,
                    isDirectory = false,
                    size = file.length()
                )
            )
        }
    }

    // Sort: folders first (alphabetically), then files (alphabetically)
    return items.sortedWith(
        compareBy<FileSystemItem> { !it.isDirectory }
            .thenBy { it.name.lowercase() }
    )
}

/**
 * Check if a filename has a video extension.
 */
private fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

/**
 * Recursively count video files in a directory.
 */
private fun countVideosRecursively(dir: File): Int {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return 0
    var count = 0
    val files = dir.listFiles() ?: return 0
    for (file in files) {
        if (file.name.startsWith(".")) continue
        if (file.isDirectory) {
            count += countVideosRecursively(file)
        } else if (isVideoFile(file.name)) {
            count++
        }
    }
    return count
}

/**
 * Recursively find all video files in a directory.
 */
private fun findVideosRecursively(dir: File): List<File> {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
    val result = mutableListOf<File>()
    val files = dir.listFiles() ?: return emptyList()
    for (file in files) {
        if (file.name.startsWith(".")) continue
        if (file.isDirectory) {
            result.addAll(findVideosRecursively(file))
        } else if (isVideoFile(file.name)) {
            result.add(file)
        }
    }
    return result
}

/**
 * Format file size to human-readable string.
 */
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
}

// ==============================
// UI Sub-Composables
// ==============================

/**
 * Permission request screen shown when storage access is not granted.
 */
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔒",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Требуется доступ к файлам",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Для просмотра и выбора видеофайлов приложению необходим доступ к хранилищу устройства.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Предоставить доступ",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Назад", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

/**
 * Top bar showing current path and navigation controls.
 */
@Composable
private fun FilePickerTopBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = Color(0xFF2C2C3E),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                TextButton(onClick = onBack) {
                    Text("← Назад", color = Color.White, fontSize = 14.sp)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Up button
                IconButton(onClick = onNavigateUp, enabled = currentPath != ROOT_PATH) {
                    Text("⬆", fontSize = 20.sp, color = if (currentPath != ROOT_PATH) Color.White else Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Path display
                Text(
                    text = currentPath.removePrefix("/storage/emulated/0").ifEmpty { "/" },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Divider(color = Color.White.copy(alpha = 0.1f))
        }
    }
}

/**
 * Folder item in the file list.
 *
 * @param item The folder's file system info
 * @param videoCount Total number of videos in the folder (null if still counting)
 * @param selectedCount Number of videos currently selected in this folder
 * @param toggleableState The tri-state for the checkbox (Off / Indeterminate / On)
 * @param onSelect Called when the folder checkbox is toggled
 * @param onClick Called when the folder body is clicked (navigates into folder)
 */
@Composable
private fun FolderItem(
    item: FileSystemItem,
    videoCount: Int?,
    selectedCount: Int,
    toggleableState: ToggleableState,
    onSelect: () -> Unit,
    onClick: () -> Unit
) {
    val hasSelection = selectedCount > 0

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (hasSelection) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFF2C2C3E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tri-state checkbox for selection
            TriStateCheckbox(
                state = toggleableState,
                onClick = onSelect,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = Color.White.copy(alpha = 0.5f)
                )
            )

            // Folder content (clickable to navigate)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📁", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Secondary line: selection count or video count
                    Text(
                        text = when {
                            videoCount == null -> "Подсчёт..."
                            videoCount == 0 -> "Нет видео"
                            selectedCount > 0 -> "Выбрано: $selectedCount из $videoCount"
                            else -> "$videoCount видео"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Video file item in the file list.
 */
@Composable
private fun VideoFileItem(
    item: FileSystemItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFF2C2C3E)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = Color.White.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🎬", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(item.size),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Bottom bar with selection summary and action buttons.
 */
@Composable
private fun FilePickerBottomBar(
    selectedFileCount: Int,
    selectedFolderCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF2C2C3E),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Divider(color = Color.White.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection count
                Column {
                    Text(
                        text = "Выбрано: $selectedFileCount файлов",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (selectedFolderCount > 0) {
                        Text(
                            text = "из $selectedFolderCount папок",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = selectedFileCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                        )
                    ) {
                        Text("Добавить", color = Color.White)
                    }
                }
            }
        }
    }
}
