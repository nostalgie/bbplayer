package com.dima.kidsvideoplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom file browser screen for batch video selection.
 *
 * Two-phase selection process (see plans/file-picker-architecture-documentation.md):
 *   Phase 1 — "Отбор файлов": user browses and marks files/folders for potential addition.
 *   Phase 2 — "Подтверждение добавления": user presses "Добавить" and only supported
 *              files are persisted via VideoRepository.
 *
 * Performance optimisations vs. the original implementation:
 *  1. Directory listing is loaded on IO and shown immediately; folder video counts
 *     and compatibility checks run in the background without blocking the UI.
 *  2. State updates are batched — compatibility results and folder paths are written
 *     in bulk instead of one-by-one, drastically reducing recompositions.
 *  3. A single supervisor-backed coroutine scope is used per directory load so that
 *     navigation to a new folder automatically cancels work for the old one.
 *  4. ConcurrentHashMap for compat cache avoids creating new immutable maps on every
 *     single file check; a snapshot is published to a mutableStateOf periodically.
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
        storageVolumes = withContext(Dispatchers.IO) { listStorageVolumes(context) }
    }

    // Set of storage root paths (for navigation logic)
    val storageRootPaths = storageVolumes.map { it.path }.toSet()

    // ── Selection state (Phase 1: Отбор файлов) ──────────────────────────
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }

    // ── Directory content state ──────────────────────────────────────────
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var directoryItems by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }

    // Folder → video paths inside it.  null = still loading.
    var folderVideoPaths by remember { mutableStateOf<Map<String, List<String>?>>(emptyMap()) }

    // Track which folders are currently being scanned
    var scanningFolders by remember { mutableStateOf(emptySet<String>()) }

    // Compatibility cache persists across directory navigations.
    // ConcurrentHashMap for thread-safe writes from coroutines without
    // triggering recomposition on every entry.  We snapshot it into a
    // mutableStateOf periodically (batched) so the UI sees updates.
    val compatCacheMap = remember { ConcurrentHashMap<String, CompatibilityResult>() }
    var compatCacheSnapshot by remember { mutableStateOf<Map<String, CompatibilityResult>>(emptyMap()) }

    // ── Load directory contents when path changes ────────────────────────
    LaunchedEffect(currentPath) {
        if (currentPath == STORAGE_ROOT) {
            directoryItems = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        try {
            // 1. Load directory listing on IO — this is fast (just one level)
            val items = withContext(Dispatchers.IO) {
                listDirectoryItems(currentPath)
            }
            directoryItems = items

            // 2. Mark all sub-directories as "loading"
            val dirs = items.filter { it.isDirectory }
            val dirPaths = dirs.map { it.path }.toSet()
            val loadingMap: Map<String, List<String>?> = dirs.associate { it.path to null }
            val existingKeep = folderVideoPaths.filterKeys { it !in dirPaths }
            folderVideoPaths = loadingMap + existingKeep

            // Publish whatever compat results we already have cached
            compatCacheSnapshot = compatCacheMap.toMap()

            isLoading = false  // Show the list immediately

            // 3. Scan folders for video paths in parallel (max 4 at a time)
            dirs.chunked(4).forEach { chunk ->
                chunk.forEach { dirItem ->
                    launch {
                        scanningFolders = scanningFolders + dirItem.path
                        try {
                            val videos = withContext(Dispatchers.IO) {
                                findVideosRecursively(File(dirItem.path))
                            }
                            val paths = videos.map { it.absolutePath }
                            folderVideoPaths = folderVideoPaths.toMutableMap().apply {
                                this[dirItem.path] = paths
                            }

                            // Batch compatibility check for all videos in this folder
                            val uncached = paths.filter { !compatCacheMap.containsKey(it) }
                            if (uncached.isNotEmpty()) {
                                val results = checkCompatibilityBatch(
                                    uncached,
                                    videoCompatibilityChecker
                                )
                                compatCacheMap.putAll(results)
                                // Single snapshot update for the whole batch
                                compatCacheSnapshot = compatCacheMap.toMap()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FilePicker", "Error scanning ${dirItem.path}", e)
                            folderVideoPaths = folderVideoPaths.toMutableMap().apply {
                                this[dirItem.path] = emptyList()
                            }
                        } finally {
                            scanningFolders = scanningFolders - dirItem.path
                        }
                    }
                }
            }

            // 4. Check compatibility for video FILES in current directory
            val files = items.filter { it.isFile }
            val uncachedFiles = files.filter { !compatCacheMap.containsKey(it.path) }
            if (uncachedFiles.isNotEmpty()) {
                launch {
                    val paths = uncachedFiles.map { it.path }
                    val results = checkCompatibilityBatch(paths, videoCompatibilityChecker)
                    compatCacheMap.putAll(results)
                    compatCacheSnapshot = compatCacheMap.toMap()
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

    // ── Permission request screen or file browser ────────────────────────
    if (!hasStoragePermission.value) {
        PermissionRequestScreen(
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:com.dima.kidsvideoplayer")
                        )
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                } else {
                    runtimePermissionLauncher.launch(
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    )
                }
            },
            onBack = onBack
        )
        return
    }

    // ══════════════════════════════════════════════════════════════════════
    // File Browser UI
    // ══════════════════════════════════════════════════════════════════════
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
    ) {
        // ── Top bar with navigation ──────────────────────────────────────
        FilePickerTopBar(
            currentPath = currentPath,
            storageRootPaths = storageRootPaths.toSet(),
            onNavigateUp = {
                if (currentPath in storageRootPaths) {
                    currentPath = STORAGE_ROOT
                } else {
                    val parent = File(currentPath).parentFile
                    if (parent != null) {
                        when {
                            parent.absolutePath in storageRootPaths ->
                                currentPath = parent.absolutePath
                            parent.absolutePath == STORAGE_ROOT ||
                                parent.absolutePath == "/storage/emulated" ->
                                currentPath = STORAGE_ROOT
                            parent.canRead() ->
                                currentPath = parent.absolutePath
                        }
                    }
                }
            }
        )

        // ── Storage selection level ──────────────────────────────────────
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

            FilePickerBottomBar(
                selectedFileCount = 0,
                selectedFolderCount = 0,
                onConfirm = {},
                onCancel = onBack
            )
        } else {
            // ── Select All / Deselect All ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val allPaths = mutableSetOf<String>()
                        allPaths.addAll(selectedFiles)
                        for (folder in directoryItems.filter { it.isDirectory }) {
                            val cached = folderVideoPaths[folder.path]
                            if (cached != null) {
                                allPaths.addAll(cached.filter { path ->
                                    compatCacheSnapshot[path]?.isFullySupported != false
                                })
                            } else {
                                val videos = withContext(Dispatchers.IO) {
                                    findVideosRecursively(File(folder.path))
                                }
                                allPaths.addAll(videos.map { it.absolutePath })
                            }
                        }
                        for (file in directoryItems.filter { it.isFile }) {
                            if (compatCacheSnapshot[file.path]?.isFullySupported != false) {
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

            // ── Loading / error / empty states ───────────────────────────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Use AndroidView to avoid Compose animation API compatibility issues
                    // (CircularProgressIndicator crashes on some devices with older Compose runtime)
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
                // ── File/folder list ─────────────────────────────────────
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
                        val cache = compatCacheSnapshot

                        // Folders first
                        items(items = folders, key = { it.path }) { item ->
                            val cachedPaths = folderVideoPaths[item.path]
                            val videoCount = cachedPaths?.size

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
                                item.path in scanningFolders -> ToggleableState.Off
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
                                isScanning = item.path in scanningFolders,
                                onSelect = {
                                    if (item.path in scanningFolders) return@FolderItem

                                    val paths = cachedPaths
                                    if (paths != null) {
                                        if (toggleableState == ToggleableState.On) {
                                            selectedFiles = selectedFiles - paths.toSet()
                                        } else {
                                            val selectable = paths.filter { path ->
                                                cache[path]?.isFullySupported != false
                                            }
                                            selectedFiles = selectedFiles + selectable
                                        }
                                    } else {
                                        if (item.path !in scanningFolders) {
                                            coroutineScope.launch {
                                                scanningFolders = scanningFolders + item.path
                                                try {
                                                    val videos = withContext(Dispatchers.IO) {
                                                        findVideosRecursively(File(item.path))
                                                    }
                                                    val foundPaths = videos.map { it.absolutePath }
                                                    folderVideoPaths = folderVideoPaths.toMutableMap().apply {
                                                        this[item.path] = foundPaths
                                                    }
                                                    selectedFiles = selectedFiles + foundPaths
                                                } finally {
                                                    scanningFolders = scanningFolders - item.path
                                                }
                                            }
                                        }
                                    }
                                },
                                onClick = {
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

            // ── Bottom bar: Подтверждение добавления ─────────────────────
            val selectedFolderCount = folderVideoPaths.entries.count { (_, paths) ->
                if (paths == null || paths.isEmpty()) return@count false
                val supported = paths.filter {
                    compatCacheSnapshot[it]?.isFullySupported == true
                }
                supported.isNotEmpty() && supported.all { it in selectedFiles }
            }

            FilePickerBottomBar(
                selectedFileCount = selectedFiles.size,
                selectedFolderCount = selectedFolderCount,
                onConfirm = {
                    coroutineScope.launch {
                        // Phase 2: Подтверждение добавления
                        // Only persist supported files. If compat check hasn't
                        // completed yet, include the file (optimistic add).
                        val supportedPaths = selectedFiles.filter { path ->
                            val result = compatCacheSnapshot[path]
                            result == null || result.isFullySupported
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

// ──────────────────────────────────────────────────────────────────────────
// Helper: batch compatibility check (runs on IO)
// ──────────────────────────────────────────────────────────────────────────
private suspend fun checkCompatibilityBatch(
    paths: List<String>,
    checker: VideoCompatibilityChecker
): Map<String, CompatibilityResult> = coroutineScope {
    val results = mutableMapOf<String, CompatibilityResult>()
    // Process in small chunks to keep memory usage bounded
    paths.chunked(8).forEach { chunk ->
        chunk.map { path ->
            async(Dispatchers.IO) {
                try {
                    val uri = Uri.fromFile(File(path))
                    path to checker.checkCompatibility(uri)
                } catch (e: Exception) {
                    path to CompatibilityResult(
                        isFullySupported = false,
                        videoCodec = null,
                        audioCodec = null,
                        videoSupported = false,
                        audioSupported = false,
                        warnings = listOf(e.message ?: "Unknown error"),
                        canReadFile = false
                    )
                }
            }
        }.awaitAll().forEach { pair ->
            results[pair.first] = pair.second
        }
    }
    results
}

/**
 * Check if storage permission is granted.
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
