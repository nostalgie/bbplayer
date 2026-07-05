package com.dima.kidsvideoplayer.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoLibraryServiceTest {

    private fun entry(
        path: String,
        folder: String,
        size: Long = 1000L,
        mtime: Long = 12345L
    ) = VideoEntry(
        uriString = "file://$path",
        filePath = path,
        fileName = path.substringAfterLast('/'),
        sourceFolder = folder,
        lastModified = mtime,
        fileSize = size
    )

    @Test
    fun detectVideoRenames_matchesBySizeAndMtimeInSameFolder() {
        val previous = listOf(
            entry("/storage/Movies/old_name.mp4", "/storage/Movies", mtime = 999L)
        )
        val current = listOf(
            entry("/storage/Movies/new_name.mp4", "/storage/Movies", mtime = 999L)
        )

        val migrations = detectVideoRenames(previous, current)

        assertThat(migrations).containsEntry(
            "file:///storage/Movies/old_name.mp4",
            "file:///storage/Movies/new_name.mp4"
        )
    }

    @Test
    fun detectVideoRenames_returnsEmptyWhenNoMatch() {
        val previous = listOf(
            entry("/storage/Movies/a.mp4", "/storage/Movies", size = 100L, mtime = 1L)
        )
        val current = listOf(
            entry("/storage/Movies/b.mp4", "/storage/Movies", size = 200L, mtime = 2L)
        )

        assertThat(detectVideoRenames(previous, current)).isEmpty()
    }

    @Test
    fun detectVideoRenames_returnsEmptyWhenListsUnchanged() {
        val videos = listOf(entry("/storage/Movies/a.mp4", "/storage/Movies"))
        assertThat(detectVideoRenames(videos, videos)).isEmpty()
    }
}
