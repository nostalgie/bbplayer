package com.dima.kidsvideoplayer.ui.screens.filepicker

import android.content.Context
import com.dima.kidsvideoplayer.utils.HuaweiStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap

val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "ts", "mpg", "mpeg", "rmvb", "vob"
)

const val STORAGE_ROOT = "/storage"
const val INTERNAL_STORAGE_PATH = "/storage/emulated/0"

private val directoryScanCache = ConcurrentHashMap<String, DirectoryScanResult>()

data class DirectoryScanResult(
    val files: List<FileSystemItem>,
    val videoCount: Int,
    val lastScanTime: Long
)

private fun getCachedDirectoryScan(dirPath: String): DirectoryScanResult? {
    return directoryScanCache[dirPath]?.takeIf { result ->
        System.currentTimeMillis() - result.lastScanTime < 5 * 60 * 1000
    }
}

private fun cacheDirectoryScan(dirPath: String, files: List<FileSystemItem>, videoCount: Int) {
    directoryScanCache[dirPath] = DirectoryScanResult(
        files = files,
        videoCount = videoCount,
        lastScanTime = System.currentTimeMillis()
    )
}

data class StorageVolume(
    val name: String,
    val path: String,
    val isRemovable: Boolean
)

fun listStorageVolumes(context: Context?): List<StorageVolume> {
    val volumes = mutableListOf<StorageVolume>()
    val isHuawei = context != null && HuaweiStorageHelper.isHuaweiDevice()

    if (isHuawei && context != null) {
        for (volume in HuaweiStorageHelper.getAvailableStorageVolumes(context)) {
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

        val storageDir = File(STORAGE_ROOT)
        val subDirs = storageDir.listFiles()
        if (subDirs != null) {
            for (dir in subDirs.sortedBy { it.name }) {
                val name = dir.name
                if (name == "emulated" || name == "self") continue
                if (!dir.isDirectory || name.startsWith(".")) continue
                if (!dir.canRead()) continue
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

data class FileSystemItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean = !isDirectory,
    val size: Long = 0
)

fun listDirectoryItems(dirPath: String): List<FileSystemItem> {
    getCachedDirectoryScan(dirPath)?.let { return it.files }

    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
        return emptyList()
    }

    val items = mutableListOf<FileSystemItem>()
    val files = dir.listFiles() ?: return emptyList()
    var videoCount = 0

    for (file in files) {
        val name = file.name
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

    val sortedItems = items.sortedWith(
        compareBy<FileSystemItem> { !it.isDirectory }
            .thenBy { it.name.lowercase() }
    )

    cacheDirectoryScan(dirPath, sortedItems, videoCount)
    return sortedItems
}

fun listSubdirectories(dirPath: String): List<String> {
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
    return dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
        ?.map { it.absolutePath }
        ?.sortedBy { it.substringAfterLast('/').lowercase() }
        ?: emptyList()
}

fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

suspend fun findVideosRecursively(dir: File): List<File> = withContext(Dispatchers.IO) {
    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()

    val result = mutableListOf<File>()
    val stack = ArrayDeque<File>()
    stack.add(dir)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val files = current.listFiles() ?: continue
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                stack.add(file)
            } else if (isVideoFile(file.name)) {
                result.add(file)
            }
        }
    }

    result
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
}
