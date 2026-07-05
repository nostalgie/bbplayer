package com.dima.kidsvideoplayer.ui.screens.dashboard

import com.dima.kidsvideoplayer.data.VideoEntry
import java.io.File

fun videoCountForFolder(videos: List<VideoEntry>, folderPath: String): Int =
    videos.count { it.sourceFolder == folderPath }

fun buildVideosByParentPath(videos: List<VideoEntry>): Map<String, List<VideoEntry>> =
    videos.groupBy { File(it.filePath).parent.orEmpty() }

fun buildLibraryIndexByPath(videos: List<VideoEntry>): Map<String, Int> =
    videos.withIndex().associate { (index, entry) -> entry.filePath to index }

fun parentBrowsePath(currentPath: String, watchedFolders: List<String>): String? {
    val root = watchedFolders.find { currentPath == it || currentPath.startsWith("$it/") }
        ?: return null
    if (currentPath == root) return null
    val parent = File(currentPath).parent ?: return null
    return if (parent == root || parent.startsWith("$root/")) parent else root
}

fun isPathWithinWatchedFolders(path: String, watchedFolders: List<String>): Boolean =
    watchedFolders.any { path == it || path.startsWith("$it/") }
