package com.dima.kidsvideoplayer.ui.screens.dashboard

import com.dima.kidsvideoplayer.utils.calculateFolderDepths
import com.dima.kidsvideoplayer.utils.groupVideosByFolderData

sealed interface VideoListEntry {
    data class FolderHeader(
        val folderName: String,
        val folderPath: String,
        val depth: Int,
        val isExpanded: Boolean
    ) : VideoListEntry

    data class VideoEntry(
        val uriString: String,
        val fileName: String,
        val originalIndex: Int
    ) : VideoListEntry
}

fun groupVideosByFolder(
    uris: List<String>,
    expandedFolders: Set<String> = emptySet()
): List<VideoListEntry> {
    if (uris.isEmpty()) return emptyList()

    val grouped = groupVideosByFolderData(uris)
    val sortedFolderPaths = grouped.folderMap.keys.sorted()
    val folderDepths = calculateFolderDepths(sortedFolderPaths)
    val uriIndexMap = uris.withIndex().associate { (index, uri) -> uri to index }

    val result = mutableListOf<VideoListEntry>()

    sortedFolderPaths.forEach { folderPath ->
        result.add(
            VideoListEntry.FolderHeader(
                folderName = folderPath,
                folderPath = folderPath,
                depth = folderDepths[folderPath] ?: 0,
                isExpanded = expandedFolders.contains(folderPath)
            )
        )

        if (expandedFolders.contains(folderPath)) {
            grouped.folderMap[folderPath]?.forEach { (uriString, fileName) ->
                result.add(
                    VideoListEntry.VideoEntry(
                        uriString = uriString,
                        fileName = fileName,
                        originalIndex = uriIndexMap[uriString] ?: 0
                    )
                )
            }
        }
    }

    if (grouped.ungroupedFiles.isNotEmpty()) {
        result.add(
            VideoListEntry.FolderHeader(
                folderName = "Другие",
                folderPath = "other",
                depth = 0,
                isExpanded = true
            )
        )
        grouped.ungroupedFiles.forEach { (uriString, fileName) ->
            result.add(
                VideoListEntry.VideoEntry(
                    uriString = uriString,
                    fileName = fileName,
                    originalIndex = uriIndexMap[uriString] ?: 0
                )
            )
        }
    }

    return result
}

fun areAllVideosInFolderSelected(
    folderPath: String,
    videoUris: List<String>,
    selectedVideos: Set<String>
): Boolean {
    val grouped = groupVideosByFolderData(videoUris)
    val videosInFolder = grouped.folderMap[folderPath] ?: return false
    return videosInFolder.isNotEmpty() && videosInFolder.all { (uri, _) -> uri in selectedVideos }
}

fun getSelectedCountInFolder(
    folderPath: String,
    videoUris: List<String>,
    selectedVideos: Set<String>
): Pair<Int, Int> {
    val videosInFolder = getVideosInFolder(folderPath, videoUris)
    val selectedCount = videosInFolder.count { it in selectedVideos }
    return selectedCount to videosInFolder.size
}

fun getVideosInFolder(folderPath: String, videoUris: List<String>): List<String> {
    val grouped = groupVideosByFolderData(videoUris)
    return grouped.folderMap[folderPath]?.map { (uri, _) -> uri } ?: emptyList()
}
