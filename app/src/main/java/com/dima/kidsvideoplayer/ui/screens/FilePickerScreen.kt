package com.dima.kidsvideoplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.CompatibilityResult
import com.dima.kidsvideoplayer.player.VideoCompatibilityChecker
import com.dima.kidsvideoplayer.ui.screens.filepicker.*
import com.dima.kidsvideoplayer.ui.components.VerticalScrollbar
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom file browser screen for batch video selection.
 *
 * Allows selecting individual video files and entire folders (selects all videos
 * inside recursively). Uses java.io.File for filesystem browsing and converts
 * selected files to URI strings for storage via VideoRepository.
 *
 * URI scheme: stores `file://` URIs from File.toURI().toString().
 * See also: ParentDashboardScreen stores `content://` URIs from SAF.
 * Both schemes are handled by ExoPlayer and stored in the same DataStore list.
 *
 * @param videoRepository Repository for persisting video URIs
 * @param onBack Callback to navigate back to parent dashboard
 */
@Composable
fun FilePickerScreen(
    videoRepository: VideoRepository,
    videoCompatibilityChecker: VideoCompatibilityChecker,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permission state
    val hasStoragePermission = remember {
        mutableStateOf(checkStoragePermission(context))
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE settings screen (API 30+)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permission when returning from settings
        hasStoragePermission.value = checkStoragePermission(context)
    }

    // Launcher for legacy runtime permissions (API < 30)
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        hasStoragePermission.value = granted
    }

    // Current directory path — starts at storage root (shows storage volumes)
    var currentPath by remember { mutableStateOf(STORAGE_ROOT) }

    // Available storage volumes
    var storageVolumes by remember { mutableStateOf<List<StorageVolume>>(emptyList()) }

    // Load storage volumes once
    LaunchedEffect(Unit) {
        storageVolumes = withContext(Dispatchers.IO) { listStorageVolumes() }
    }

    // Set of storage root paths (for navigation logic)
    val storageRootPaths = storageVolumes.map { it.path }.toSet()

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

    // Compatibility check cache: file absolute path -> result.
    // Persists across directory navigations within the session so each file is only checked once.
    val compatibilityCache = remember { mutableStateOf<Map<String, CompatibilityResult>>(emptyMap()) }

    // Load directory contents when path changes (skip for storage root)
    LaunchedEffect(currentPath) {
        if (currentPath == STORAGE_ROOT) {
            // Storage root level — no directory items to load
            directoryItems = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

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

                    // Launch compatibility checks for videos in this folder
                    for (videoPath in paths) {
                        launch {
                            if (videoPath in compatibilityCache.value) return@launch
                            val uri = Uri.fromFile(File(videoPath))
                            val result = videoCompatibilityChecker.checkCompatibility(uri)
                            val updated = compatibilityCache.value.toMutableMap()
                            updated[videoPath] = result
                            compatibilityCache.value = updated
                            // Auto-deselect unsupported files
                            if (!result.isFullySupported) {
                                selectedFiles = selectedFiles - videoPath
                            }
                        }
                    }
                }
            }

            // Launch compatibility checks for video files in current directory
            for (fileItem in items.filter { it.isFile }) {
                launch {
                    if (fileItem.path in compatibilityCache.value) return@launch
                    val uri = Uri.fromFile(File(fileItem.path))
                    val result = videoCompatibilityChecker.checkCompatibility(uri)
                    val updated = compatibilityCache.value.toMutableMap()
                    updated[fileItem.path] = result
                    compatibilityCache.value = updated
                    // Auto-deselect unsupported files
                    if (!result.isFullySupported) {
                        selectedFiles = selectedFiles - fileItem.path
                    }
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
            .background(DashboardBackground)
    ) {
        // Top bar with navigation
        FilePickerTopBar(
            currentPath = currentPath,
            storageRootPaths = storageRootPaths.toSet(),
            onNavigateUp = {
                if (currentPath in storageRootPaths) {
                    // At storage root → go to storage selection
                    currentPath = STORAGE_ROOT
                } else {
                    // Normal directory → go to parent
                    val parent = File(currentPath).parentFile
                    if (parent != null) {
                        // Don't navigate above the storage root
                        if (parent.absolutePath in storageRootPaths) {
                            currentPath = parent.absolutePath
                        } else if (parent.absolutePath == STORAGE_ROOT || parent.absolutePath == "/storage/emulated") {
                            currentPath = STORAGE_ROOT
                        } else if (parent.canRead()) {
                            currentPath = parent.absolutePath
                        }
                    }
                }
            }
        )

        // Storage selection level
        if (currentPath == STORAGE_ROOT) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = storageVolumes, key = { it.path }) { volume ->
                    StorageVolumeItem(
                        volume = volume,
                        onClick = { currentPath = volume.path }
                    )
                }
            }

            // Bottom bar (only cancel button at storage root)
            FilePickerBottomBar(
                selectedFileCount = 0,
                selectedFolderCount = 0,
                onConfirm = {},
                onCancel = onBack
            )
        } else {
        // Select All / Deselect All buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                // Select all: only supported videos
                coroutineScope.launch {
                    val allPaths = mutableSetOf<String>()
                    allPaths.addAll(selectedFiles)
                    for (folder in directoryItems.filter { it.isDirectory }) {
                        val cached = folderVideoPaths.value[folder.path]
                        if (cached != null) {
                            // Only add supported (or not-yet-checked) videos
                            allPaths.addAll(cached.filter { path ->
                                compatibilityCache.value[path]?.isFullySupported != false
                            })
                        } else {
                            val videos = withContext(Dispatchers.IO) {
                                findVideosRecursively(File(folder.path))
                            }
                            allPaths.addAll(videos.map { it.absolutePath })
                        }
                    }
                    for (file in directoryItems.filter { it.isFile }) {
                        // Only add if not known to be unsupported
                        if (compatibilityCache.value[file.path]?.isFullySupported != false) {
                            allPaths.add(file.path)
                        }
                    }
                    selectedFiles = allPaths.toSet()
                }
            }) {
                Text("Выбрать все", color = GreenPrimary)
            }
            TextButton(onClick = {
                selectedFiles = emptySet()
            }) {
                Text("Снять все", color = RedButton)
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
            val filePickerListState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            LazyColumn(
                state = filePickerListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val folders = directoryItems.filter { it.isDirectory }
                val files = directoryItems.filter { it.isFile }
                val cache = compatibilityCache.value

                // Folders first
                items(items = folders, key = { it.path }) { item ->
                    val cachedPaths = folderVideoPaths.value[item.path]
                    val videoCount = cachedPaths?.size

                    // Compute supported video count for this folder
                    val allChecked = cachedPaths != null &&
                            cachedPaths.all { it in cache }
                    val supportedPaths = cachedPaths?.filter { path ->
                        cache[path]?.isFullySupported == true
                    } ?: emptyList()
                    val supportedVideoCount = if (allChecked) supportedPaths.size else null

                    val selectedCount = if (cachedPaths != null) {
                        cachedPaths.count { it in selectedFiles }
                    } else 0

                    val toggleableState = when {
                        cachedPaths == null -> ToggleableState.Off
                        supportedPaths.isEmpty() && allChecked -> ToggleableState.Off
                        selectedCount == 0 -> ToggleableState.Off
                        selectedCount >= supportedPaths.size && supportedPaths.isNotEmpty() -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                    FolderItem(
                        item = item,
                        videoCount = videoCount,
                        supportedVideoCount = supportedVideoCount,
                        selectedCount = selectedCount,
                        toggleableState = toggleableState,
                        onSelect = {
                            val paths = cachedPaths
                            if (paths != null) {
                                if (toggleableState == ToggleableState.On) {
                                    // Deselect all videos in this folder
                                    selectedFiles = selectedFiles - paths.toSet()
                                } else {
                                    // Select only supported (or not-yet-checked) videos
                                    val selectable = paths.filter { path ->
                                        cache[path]?.isFullySupported != false
                                    }
                                    selectedFiles = selectedFiles + selectable
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
                    val compatResult = cache[item.path]
                    val isSupported = compatResult?.isFullySupported

                    VideoFileItem(
                        item = item,
                        isSelected = item.path in selectedFiles,
                        isSupported = isSupported,
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
            VerticalScrollbar(state = filePickerListState)
            } // end Box
        }

        // Bottom bar with selection info and action buttons
        // Compute selected folder count: folders where all supported videos are selected
        val selectedFolderCount = folderVideoPaths.value.count { (_, paths) ->
            if (paths == null || paths.isEmpty()) false else {
                val supported = paths.filter {
                    compatibilityCache.value[it]?.isFullySupported == true
                }
                supported.isNotEmpty() && supported.all { it in selectedFiles }
            }
        }

        FilePickerBottomBar(
            selectedFileCount = selectedFiles.size,
            selectedFolderCount = selectedFolderCount,
            onConfirm = {
                coroutineScope.launch {
                    // Filter to only supported files as a safety net
                    val supportedPaths = selectedFiles.filter { path ->
                        compatibilityCache.value[path]?.isFullySupported == true
                    }
                    val uris = supportedPaths.map { path ->
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
        } // end else (not storage root)
    }
}

/**
 * Check if storage permission is granted.
 * Properly checks READ_EXTERNAL_STORAGE for API < 30.
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        // API 26-29: check runtime permission
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
