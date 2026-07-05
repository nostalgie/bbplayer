package com.dima.kidsvideoplayer.utils

import android.net.Uri
import java.net.URLDecoder

data class FolderGroupResult(
    val folderMap: Map<String, List<Pair<String, String>>>,
    val ungroupedFiles: List<Pair<String, String>>
)

/**
 * Extract folder information from a URI string.
 * Returns Pair<displayPath, fileName> or null if parsing fails.
 */
fun extractFolderInfo(uriString: String): Pair<String, String>? {
    val uri = Uri.parse(uriString)
    return when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: return null
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) {
                val folderPath = path.substring(0, lastSlash)
                val fileName = uri.lastPathSegment.orEmpty()
                abbreviateFolderPath(folderPath) to fileName
            } else null
        }
        "content" -> {
            val segment = uri.lastPathSegment ?: return null
            val colonIdx = segment.indexOf(':')
            val relative = if (colonIdx >= 0) segment.substring(colonIdx + 1) else segment
            val lastSlash = relative.lastIndexOf('/')
            if (lastSlash > 0) {
                val folder = relative.substring(0, lastSlash)
                val fileName = URLDecoder.decode(relative.substring(lastSlash + 1), "UTF-8")
                abbreviateFolderPath(folder) to fileName
            } else null
        }
        else -> null
    }
}

/**
 * Convert full folder path to abbreviated format with storage volume names.
 */
fun abbreviateFolderPath(fullPath: String): String {
    val storagePattern = Regex("""^/storage/([^/]+)(?:/(.+))?$""")
    val matchResult = storagePattern.find(fullPath)

    return if (matchResult != null) {
        val volumeName = matchResult.groupValues[1]
        val relativePath = matchResult.groupValues[2].takeIf { it.isNotEmpty() }

        val abbreviatedVolume = when (volumeName) {
            "emulated" -> "SD1"
            else -> {
                if (volumeName.matches(Regex("""^[A-F0-9]{4}-[A-F0-9]{4}$"""))) {
                    "SD${volumeName.take(2)}"
                } else {
                    "VOL${volumeName.take(2)}"
                }
            }
        }

        if (relativePath != null) {
            "$abbreviatedVolume/$relativePath"
        } else {
            abbreviatedVolume
        }
    } else {
        val parts = fullPath.split("/").filter { it.isNotEmpty() }
        when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> {
                val volume = parts[0].take(4)
                val pathParts = parts.subList(1, parts.size)
                "$volume/${pathParts.joinToString("/")}"
            }
        }
    }
}

/**
 * Group video URIs by folder path in a single pass.
 * Returns map of folderPath → list of (uri, fileName) pairs, plus ungrouped files.
 */
fun groupVideosByFolderData(uris: List<String>): FolderGroupResult {
    val folderMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
    val ungroupedFiles = mutableListOf<Pair<String, String>>()

    uris.forEachIndexed { index, uriString ->
        val info = extractFolderInfo(uriString)
        if (info != null) {
            val (folderPath, fileName) = info
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(uriString to fileName)
        } else {
            ungroupedFiles.add(uriString to "Видео ${index + 1}")
        }
    }

    return FolderGroupResult(
        folderMap = folderMap.mapValues { it.value.toList() },
        ungroupedFiles = ungroupedFiles
    )
}

fun calculateFolderDepths(folderPaths: List<String>): Map<String, Int> {
    if (folderPaths.isEmpty()) return emptyMap()
    val minSegments = folderPaths.minOf { path -> path.count { it == '/' } }
    return folderPaths.associateWith { path ->
        path.count { it == '/' } - minSegments
    }
}

fun isSdCardPath(path: String): Boolean {
    return path.contains("sdcard", ignoreCase = true) ||
        path.matches(Regex("/storage/[A-F0-9]{4}-[A-F0-9]{4}")) ||
        path == "/storage/extSdCard" ||
        path == "/storage/sdcard1"
}
