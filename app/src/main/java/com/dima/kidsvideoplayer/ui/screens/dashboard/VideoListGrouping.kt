package com.dima.kidsvideoplayer.ui.screens.dashboard

import com.dima.kidsvideoplayer.data.VideoEntry
import com.dima.kidsvideoplayer.utils.abbreviateFolderPath

sealed interface VideoListEntry {
    data class FolderHeader(
        val folderName: String,
        val folderPath: String,
        val isExpanded: Boolean,
        val videoCount: Int
    ) : VideoListEntry

    data class VideoEntryItem(
        val uriString: String,
        val fileName: String,
        val originalIndex: Int
    ) : VideoListEntry
}

fun groupLibraryByWatchedFolder(
    videos: List<VideoEntry>,
    watchedFolders: List<String>,
    expandedFolders: Set<String>
): List<VideoListEntry> {
    if (watchedFolders.isEmpty()) return emptyList()

    val videosByFolder = videos.groupBy { it.sourceFolder }
    val uriIndexMap = videos.withIndex().associate { (index, entry) -> entry.uriString to index }
    val result = mutableListOf<VideoListEntry>()

    watchedFolders.sorted().forEach { folderPath ->
        val folderVideos = videosByFolder[folderPath].orEmpty()
        result.add(
            VideoListEntry.FolderHeader(
                folderName = abbreviateFolderPath(folderPath),
                folderPath = folderPath,
                isExpanded = expandedFolders.contains(folderPath),
                videoCount = folderVideos.size
            )
        )

        if (expandedFolders.contains(folderPath)) {
            folderVideos.sortedBy { it.fileName.lowercase() }.forEach { entry ->
                result.add(
                    VideoListEntry.VideoEntryItem(
                        uriString = entry.uriString,
                        fileName = entry.fileName,
                        originalIndex = uriIndexMap[entry.uriString] ?: 0
                    )
                )
            }
        }
    }

    return result
}

fun isFolderSelected(folderPath: String, selectedFolders: Set<String>): Boolean =
    folderPath in selectedFolders
