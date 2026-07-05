package com.dima.kidsvideoplayer.data

import android.content.Context
import com.dima.kidsvideoplayer.ui.screens.filepicker.findVideosRecursively
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class VideoEntry(
    val uriString: String,
    val filePath: String,
    val fileName: String,
    val sourceFolder: String,
    val lastModified: Long,
    val fileSize: Long
)

data class LibraryState(
    val videos: List<VideoEntry> = emptyList(),
    val unsupportedFiles: List<String> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long? = null,
    val uriMigrations: Map<String, String> = emptyMap(),
    val inaccessibleFolders: List<String> = emptyList()
)

/**
 * Scans watched folders, probes video compatibility, and tracks library changes.
 */
class VideoLibraryService(
    context: Context,
    private val videoRepository: VideoRepository
) {
    private val appContext = context.applicationContext
    private val compatibilityChecker = VideoCompatibilityChecker(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanMutex = Mutex()

    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    private var periodicScanJob: Job? = null
    private var watchedFoldersJob: Job? = null

    fun start() {
        if (watchedFoldersJob != null) return

        watchedFoldersJob = scope.launch {
            videoRepository.ensureMigrated()
            videoRepository.watchedFolders
                .distinctUntilChanged()
                .collect {
                    scanNow()
                }
        }
    }

    fun startPeriodicScan(intervalMs: Long = SCAN_INTERVAL_MS) {
        periodicScanJob?.cancel()
        periodicScanJob = scope.launch {
            while (true) {
                delay(intervalMs)
                scanNow()
            }
        }
    }

    fun stopPeriodicScan() {
        periodicScanJob?.cancel()
        periodicScanJob = null
    }

    fun scanNow() {
        scope.launch {
            scanMutex.withLock {
                performScan()
            }
        }
    }

    private suspend fun performScan() {
        _libraryState.value = _libraryState.value.copy(isScanning = true)

        val folders = videoRepository.watchedFolders.first()
        val previousVideos = _libraryState.value.videos

        val scanResult = withContext(Dispatchers.IO) {
            scanFolders(folders)
        }

        val uriMigrations = detectVideoRenames(previousVideos, scanResult.videos)
        val sortedVideos = scanResult.videos.sortedWith(
            compareBy<VideoEntry> { it.sourceFolder }
                .thenBy { it.fileName.lowercase() }
        )

        _libraryState.value = LibraryState(
            videos = sortedVideos,
            unsupportedFiles = scanResult.unsupportedFiles.sorted(),
            isScanning = false,
            lastScanTime = System.currentTimeMillis(),
            uriMigrations = uriMigrations,
            inaccessibleFolders = scanResult.inaccessibleFolders
        )
    }

    private data class ScanResult(
        val videos: List<VideoEntry>,
        val unsupportedFiles: List<String>,
        val inaccessibleFolders: List<String>
    )

    private suspend fun scanFolders(folders: List<String>): ScanResult {
        val videos = mutableListOf<VideoEntry>()
        val unsupported = mutableListOf<String>()
        val inaccessible = mutableListOf<String>()

        for (folderPath in folders) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory || !folder.canRead()) {
                inaccessible.add(folderPath)
                continue
            }

            val files = findVideosRecursively(folder)
            for (file in files) {
                val playable = compatibilityChecker.isPlayable(file)
                if (playable) {
                    videos.add(
                        VideoEntry(
                            uriString = file.toURI().toString(),
                            filePath = file.absolutePath,
                            fileName = file.name,
                            sourceFolder = folderPath,
                            lastModified = file.lastModified(),
                            fileSize = file.length()
                        )
                    )
                } else {
                    unsupported.add(file.absolutePath)
                }
            }
        }

        return ScanResult(videos, unsupported, inaccessible)
    }

    companion object {
        const val SCAN_INTERVAL_MS = 5 * 60 * 1000L
    }
}

internal fun detectVideoRenames(
    previous: List<VideoEntry>,
    current: List<VideoEntry>
): Map<String, String> {
    val currentPaths = current.map { it.filePath }.toSet()
    val removed = previous.filter { it.filePath !in currentPaths }
    val added = current.filter { entry ->
        previous.none { it.filePath == entry.filePath }
    }

    if (removed.isEmpty() || added.isEmpty()) return emptyMap()

    val migrations = mutableMapOf<String, String>()
    val usedAdded = mutableSetOf<String>()

    for (old in removed) {
        val match = added.firstOrNull { new ->
            new.filePath !in usedAdded &&
                new.sourceFolder == old.sourceFolder &&
                new.fileSize == old.fileSize &&
                new.lastModified == old.lastModified
        }
        if (match != null) {
            migrations[old.uriString] = match.uriString
            usedAdded.add(match.filePath)
        }
    }

    return migrations
}
