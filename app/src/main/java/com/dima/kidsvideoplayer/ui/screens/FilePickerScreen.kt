package com.dima.kidsvideoplayer.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * Custom file browser for adding watched folders to the library.
 */
@Composable
fun FilePickerScreen(
    videoRepository: VideoRepository,
    onBack: () -> Unit,
    onOpenExternalSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val watchedFolderSet = remember(watchedFolders) { watchedFolders.toSet() }

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
    var selectedFolders by remember { mutableStateOf(emptySet<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var directoryItems by remember { mutableStateOf<List<FileSystemItem>>(emptyList()) }
    var folderVideoCounts by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
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
            val loadingMap: Map<String, Int?> = dirs.associate { it.path to null }
            val existingKeep = folderVideoCounts.filterKeys { it !in dirPaths }
            folderVideoCounts = loadingMap + existingKeep

            isLoading = false

            dirs.chunked(4).forEach { chunk ->
                chunk.forEach { dirItem ->
                    launch {
                        scanningFolders = scanningFolders + dirItem.path
                        try {
                            val count = withContext(Dispatchers.IO) {
                                findVideosRecursively(File(dirItem.path)).size
                            }
                            folderVideoCounts = folderVideoCounts.toMutableMap().apply {
                                this[dirItem.path] = count
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FilePicker", "Error scanning ${dirItem.path}", e)
                            folderVideoCounts = folderVideoCounts.toMutableMap().apply {
                                this[dirItem.path] = 0
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
                    onOpenExternalSettings()
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
                modifier = Modifier.fillMaxWidth().weight(1f),
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
                    val selectable = directoryItems
                        .filter { it.isDirectory }
                        .map { it.path }
                        .filter { it !in watchedFolderSet }
                    selectedFolders = selectable.toSet()
                }) {
                    Text("Выбрать все", color = GreenPrimary)
                }
                TextButton(onClick = { selectedFolders = emptySet() }) {
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
                val folders = directoryItems.filter { it.isDirectory }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = filePickerListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items = folders, key = { it.path }) { item ->
                            val videoCount = folderVideoCounts[item.path]
                            val isAlreadyWatched = item.path in watchedFolderSet
                            val isSelected = item.path in selectedFolders

                            FolderItem(
                                item = item,
                                videoCount = videoCount,
                                supportedVideoCount = videoCount,
                                selectedCount = if (isSelected) 1 else 0,
                                toggleableState = when {
                                    isAlreadyWatched -> androidx.compose.ui.state.ToggleableState.On
                                    isSelected -> androidx.compose.ui.state.ToggleableState.On
                                    else -> androidx.compose.ui.state.ToggleableState.Off
                                },
                                isScanning = item.path in scanningFolders,
                                isAlreadyWatched = isAlreadyWatched,
                                onSelect = {
                                    if (isAlreadyWatched) return@FolderItem
                                    selectedFolders = if (isSelected) {
                                        selectedFolders - item.path
                                    } else {
                                        selectedFolders + item.path
                                    }
                                },
                                onClick = { currentPath = item.path }
                            )
                        }
                    }
                    VerticalScrollbar(state = filePickerListState)
                }
            }

            val newFolderCount = selectedFolders.count { it !in watchedFolderSet }

            FilePickerBottomBar(
                selectedFolderCount = newFolderCount,
                onConfirm = {
                    coroutineScope.launch {
                        val toAdd = selectedFolders.filter { it !in watchedFolderSet }
                        if (toAdd.isNotEmpty()) {
                            videoRepository.addWatchedFolders(toAdd)
                        }
                        onBack()
                    }
                },
                onCancel = onBack
            )
        }
    }
}
