package com.dima.kidsvideoplayer.ui.screens.filepicker

import android.content.Context
import android.os.Environment
import com.dima.kidsvideoplayer.utils.HuaweiStorageHelper
import com.dima.kidsvideoplayer.utils.PerformanceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Supported video file extensions
val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "ts", "mpg", "mpeg", "rmvb", "vob"
)

/** Storage root — shows available storage volumes */
const val STORAGE_ROOT = "/storage"

/** Internal storage path */
const val INTERNAL_STORAGE_PATH = "/storage/emulated/0"

/** Legacy root path for backward compatibility */
const val ROOT_PATH = INTERNAL_STORAGE_PATH

// Cache for directory scanning results
private val directoryScanCache = ConcurrentHashMap<String, DirectoryScanResult>()
private val scanCacheMutex = Any()
private var cacheVersion = AtomicLong(0)

/**
 * Cache entry for directory scan results
 */
data class DirectoryScanResult(
    val files: List<FileSystemItem>,
    val videoCount: Int,
    val lastScanTime: Long,
    val cacheVersion: Long
)

/**
 * Get or create cached directory scan result
 */
private fun getCachedDirectoryScan(dirPath: String, forceRefresh: Boolean = false): DirectoryScanResult? {
    synchronized(scanCacheMutex) {
        if (!forceRefresh) {
            directoryScanCache[dirPath]?.let { result ->
                // Check if cache is still valid (not older than 5 minutes)
                if (System.currentTimeMillis() - result.lastScanTime < 5 * 60 * 1000 &&
                    result.cacheVersion == cacheVersion.get()) {
                    return result
                }
            }
        }
        return null
    }
}

/**
 * Cache directory scan result
 */
private fun cacheDirectoryScan(dirPath: String, files: List<FileSystemItem>, videoCount: Int): DirectoryScanResult {
    synchronized(scanCacheMutex) {
        val result = DirectoryScanResult(
            files = files,
            videoCount = videoCount,
            lastScanTime = System.currentTimeMillis(),
            cacheVersion = cacheVersion.get()
        )
        directoryScanCache[dirPath] = result
        return result
    }
}

/**
 * Invalidate directory cache for a specific path
 */
fun invalidateDirectoryCache(dirPath: String) {
    synchronized(scanCacheMutex) {
        directoryScanCache.remove(dirPath)
        cacheVersion.incrementAndGet()
    }
}

/**
 * Clear all directory caches
 */
fun clearAllDirectoryCaches() {
    synchronized(scanCacheMutex) {
        directoryScanCache.clear()
        cacheVersion.incrementAndGet()
    }
}

/**
 * Represents a storage volume (internal storage or SD card).
 */
data class StorageVolume(
    val name: String,
    val path: String,
    val isRemovable: Boolean
)

/**
 * List available storage volumes on the device.
 * Returns internal storage first, then any removable storage (SD cards).
 */
fun listStorageVolumes(): List<StorageVolume> {
    // For basic usage without context, use standard method
    return listStorageVolumes(null)
}

/**
 * List available storage volumes on the device with context for Huawei-specific handling.
 * Returns internal storage first, then any removable storage (SD cards).
 */
fun listStorageVolumes(context: Context?): List<StorageVolume> {
    val volumes = mutableListOf<StorageVolume>()

    // Check if this is a Huawei device that needs special handling
    val isHuawei = context != null && HuaweiStorageHelper.isHuaweiDevice()
    
    if (isHuawei && context != null) {
        // Use Huawei-specific storage detection
        val huaweiVolumes = HuaweiStorageHelper.getAvailableStorageVolumes(context)
        for (volume in huaweiVolumes) {
            if (volume.isAccessible) {
                volumes.add(
                    StorageVolume(
                        name = volume.name,
                        path = volume.path,
                        isRemovable = volume.isRemovable
                    )
                )
            }
        }
    } else {
        // Standard Android storage detection
        // Internal storage
        val internal = File(INTERNAL_STORAGE_PATH)
        if (internal.exists() && internal.isDirectory && internal.canRead()) {
            volumes.add(
                StorageVolume(
                    name = "Внутренняя память",
                    path = internal.absolutePath,
                    isRemovable = false
                )
            )
        }

        // External storage (SD cards) — typically mounted as /storage/XXXX-XXXX
        val storageDir = File(STORAGE_ROOT)
        val subDirs = storageDir.listFiles()
        if (subDirs != null) {
            for (dir in subDirs.sortedBy { it.name }) {
                val name = dir.name
                // Skip system directories and internal storage
                if (name == "emulated" || name == "self") continue
                if (!dir.isDirectory || name.startsWith(".")) continue
                if (!dir.canRead()) continue
                // SD card mount points typically have format like XXXX-XXXX
                volumes.add(
                    StorageVolume(
                        name = "SD-карта",
                        path = dir.absolutePath,
                        isRemovable = true
                    )
                )
            }
        }
    }

    return volumes
}

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

/**
 * List all folders and video files in the given directory with caching.
 * Folders are listed first, then video files, both sorted alphabetically.
 */
fun listDirectoryItems(dirPath: String): List<FileSystemItem> {
    PerformanceMonitor.startTimer("listDirectoryItems")
    PerformanceMonitor.incrementCounter("directoryListOperations")
    
    // Try to get from cache first
    getCachedDirectoryScan(dirPath)?.let { result ->
        PerformanceMonitor.incrementCounter("cacheHits")
        PerformanceMonitor.CacheStats.recordHit()
        PerformanceMonitor.stopTimer("listDirectoryItems")
        return result.files
    }
    
    PerformanceMonitor.incrementCounter("cacheMisses")
    PerformanceMonitor.CacheStats.recordMiss()
    
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
        PerformanceMonitor.stopTimer("listDirectoryItems")
        return emptyList()
    }

    val items = mutableListOf<FileSystemItem>()
    val files = dir.listFiles() ?: return emptyList()
    var videoCount = 0

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
            videoCount++
        }
    }

    // Sort: folders first (alphabetically), then files (alphabetically)
    val sortedItems = items.sortedWith(
        compareBy<FileSystemItem> { !it.isDirectory }
            .thenBy { it.name.lowercase() }
    )
    
    // Cache the result
    cacheDirectoryScan(dirPath, sortedItems, videoCount)
    
    PerformanceMonitor.stopTimer("listDirectoryItems")
    return sortedItems
}

/**
 * Check if a filename has a video extension.
 */
fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

/**
 * Recursively find all video files in a directory with parallel processing.
 */
suspend fun findVideosRecursively(dir: File): List<File> = withContext(Dispatchers.IO) {
    findVideosRecursivelyPaginated(dir, 0, Int.MAX_VALUE)
}

/**
 * Synchronous version for compatibility (deprecated - use async version)
 */
@Deprecated("Use the async version instead", ReplaceWith("findVideosRecursively(dir)"))
fun findVideosRecursivelySync(dir: File): List<File> {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
    val result = mutableListOf<File>()
    val files = dir.listFiles() ?: return emptyList()
    for (file in files) {
        if (file.name.startsWith(".")) continue
        if (file.isDirectory) {
            result.addAll(findVideosRecursivelySync(file))
        } else if (isVideoFile(file.name)) {
            result.add(file)
        }
    }
    return result
}

/**
 * Format file size to human-readable string.
 */
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
}

/**
 * Paginated version of findVideosRecursively for large directories.
 */
suspend fun findVideosRecursivelyPaginated(
    dir: File,
    page: Int = 0,
    pageSize: Int = 100
): List<File> = withContext(Dispatchers.IO) {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()
    
    // Check cache for this directory and page
    val cacheKey = "${dir.absolutePath}_page$page"
    getCachedDirectoryScan(cacheKey)?.let { result ->
        if (result.videoCount > 0) {
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, result.files.size)
            if (startIndex < result.files.size) {
                return@withContext result.files.subList(startIndex, endIndex)
                    .filter { it.isFile && isVideoFile(it.name) }
                    .map { File(it.path) }
            }
        }
    }
    
    val allVideos = mutableListOf<File>()
    val directories = mutableListOf<File>()
    val currentDirFiles = dir.listFiles() ?: return@withContext emptyList()
    
    // First pass: collect all files and directories
    for (file in currentDirFiles) {
        if (file.name.startsWith(".")) continue
        if (file.isDirectory) {
            directories.add(file)
        } else if (isVideoFile(file.name)) {
            allVideos.add(file)
        }
    }
    
    // Process directories recursively with pagination
    val paginatedDirectories = directories.chunked(pageSize).getOrNull(page) ?: emptyList()
    
    for (dirFile in paginatedDirectories) {
        allVideos.addAll(findVideosRecursively(dirFile))
    }
    
    // Cache the result
    cacheDirectoryScan(
        cacheKey,
        currentDirFiles.map { file ->
            FileSystemItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0
            )
        },
        allVideos.size
    )
    
    // Return only the current page of results
    val startIndex = page * pageSize
    val endIndex = minOf(startIndex + pageSize, allVideos.size)
    if (startIndex < allVideos.size) {
        allVideos.subList(startIndex, endIndex)
    } else {
        emptyList()
    }
}

/**
 * Get total video count in a directory without loading all files (for pagination UI).
 */
suspend fun getVideoCountInDirectory(dir: File): Int = withContext(Dispatchers.IO) {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext 0
    
    var count = 0
    val files = dir.listFiles() ?: return@withContext 0
    
    for (file in files) {
        if (file.name.startsWith(".")) continue
        if (file.isDirectory) {
            count += getVideoCountInDirectory(file)
        } else if (isVideoFile(file.name)) {
            count++
        }
    }
    
    count
}
