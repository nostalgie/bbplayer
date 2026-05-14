package com.dima.kidsvideoplayer.ui.screens.filepicker

import java.io.File
import java.text.DecimalFormat

// Supported video file extensions
val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp",
    "m4v", "ts", "mpg", "mpeg", "rmvb", "vob"
)

/** Root directory for the file browser */
const val ROOT_PATH = "/storage/emulated/0/"

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
 * List all folders and video files in the given directory.
 * Folders are listed first, then video files, both sorted alphabetically.
 */
fun listDirectoryItems(dirPath: String): List<FileSystemItem> {
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
fun isVideoFile(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

/**
 * Recursively find all video files in a directory.
 */
fun findVideosRecursively(dir: File): List<File> {
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
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups.coerceAtMost(units.size - 1)]
}
