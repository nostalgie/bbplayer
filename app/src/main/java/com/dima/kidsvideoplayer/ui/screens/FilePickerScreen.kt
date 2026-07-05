package com.dima.kidsvideoplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.utils.StoragePermissionHelper
import com.dima.kidsvideoplayer.ui.screens.filepicker.*
import com.dima.kidsvideoplayer.ui.components.VerticalScrollbar
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton
import kotlinx.coroutines.*
import java.io.File

/**
 * Custom file browser screen for batch video selection.
 *
 * Two-phase selection process:
 *   Phase 1 — user browses and marks files/folders for potential addition.
 *   Phase 2 — user presses "Добавить" and selected files are persisted via VideoRepository.
 *
 * URI scheme: stores `file://` URIs from File.toURI().toString().
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

    val hasStoragePermission = remember {
        mutableStateOf(StoragePermissionHelper.hasStoragePermission(context))
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission.value = StoragePermissionHelper.hasStoragePermission(context)
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission.value = permissions.values.all { it }
    }

    var currentPath by remember { mutableStateOf(STORAGE_ROOT) }
    var storageVolumes by remember { mutableStateOf<List<StorageVolume>>(emptyList()) }

    LaunchedEffect(Unit) {
        storageVolumes = withContext(Dispatchers.IO) { listStorageVolumes(context) }
    }

    val storageRootPaths = storageVolumes.map { it.path }.toSet()
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var directoryItems by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
    var folderVideoPaths by remember { mutableStateOf<Map<String, List<String>?>>(emptyMap()) }
    var scanningFolders by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(currentPath) {
        if (currentPath == STORAGE_ROOT) {
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

            val dirs = items.filter { it.isDirectory }
            val dirPaths = dirs.map { it.path }.toSet()
            val loadingMap: Map<String, List<String>?> = dirs.associate { it.path to null }
            val existingKeep = folderVideoPaths.filterKeys { it !in dirPaths }
            folderVideoPaths = loadingMap + existingKeep

            isLoading = false

            dirs.chunked(4).forEach { chunk ->
                chunk.forEach { dirItem ->
                    launch {
                        scanningFolders = scanningFolders + dirItem.path
                        try {
                            val videos = withContext(Dispatchers.IO) {
                                findVideosRecursively(File(dirItem.path))
                            }
                            folderVideoPaths = folderVideoPaths.toMutableMap().apply {
                                this[dirItem.path] = videos.map { it.absolutePath }
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
        } catch (e: SecurityException) {
            errorMessage = "Нет доступа к директории"
            directoryItems = emptyList()
        } catch (e: Exception) {
            errorMessage = "Ошибка: ${e.message}"
            directoryItems = emptyList()
        }

        isLoading = false
    }

    if (!hasStoragePermission.value) {
        PermissionRequestScreen(
            onRequestPermission = {
                if (StoragePermissionHelper.needsManageAllFilesAccess()) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:com.dima.kidsvideoplayer")
                        )
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
    ) {
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
                    Text("Выбрать все", color = GreenPrimary)
                }
                TextButton(onClick = { selectedFiles = emptySet() }) {
                    Text("Снять все", color = RedButton)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.ProgressBar(ctx).apply {
                                indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Папка пуста",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }
            } else {
                val filePickerListState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = filePickerListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val folders = directoryItems.filter { it.isDirectory }
                        val files = directoryItems.filter { it.isFile }

                        items(items = folders, key = { it.path }) { item ->
                            val cachedPaths = folderVideoPaths[item.path]
                            val videoCount = cachedPaths?.size
                            val selectedCount = cachedPaths?.count { it in selectedFiles } ?: 0

                            val toggleableState = when {
                                item.path in scanningFolders -> ToggleableState.Off
                                cachedPaths == null -> ToggleableState.Off
                                cachedPaths.isEmpty() -> ToggleableState.Off
                                selectedCount == 0 -> ToggleableState.Off
                                selectedCount >= cachedPaths.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }

                            FolderItem(
                                item = item,
                                videoCount = videoCount,
                                supportedVideoCount = videoCount,
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
                                            selectedFiles = selectedFiles + paths
                                        }
                                    } else if (item.path !in scanningFolders) {
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
                                },
                                onClick = { currentPath = item.path }
                            )
                        }

                        items(items = files, key = { it.path }) { item ->
                            VideoFileItem(
                                item = item,
                                isSelected = item.path in selectedFiles,
                                isSupported = null,
                                onSelect = {
                                    selectedFiles = if (item.path in selectedFiles) {
                                        selectedFiles - item.path
                                    } else {
                                        selectedFiles + item.path
                                    }
                                }
                            )
                        }
                    }
                    VerticalScrollbar(state = filePickerListState)
                }
            }

            val selectedFolderCount = folderVideoPaths.entries.count { (_, paths) ->
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
}
