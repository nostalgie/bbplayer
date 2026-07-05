package com.dima.kidsvideoplayer.ui.screens.dashboard

import com.dima.kidsvideoplayer.data.VideoEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoListGroupingTest {

    private fun video(path: String, folder: String, name: String) = VideoEntry(
        uriString = "file://$path",
        filePath = path,
        fileName = name,
        sourceFolder = folder,
        lastModified = 0L,
        fileSize = 0L
    )

    @Test
    fun groupLibraryByWatchedFolder_groupsVideosUnderFolders() {
        val folderA = "/storage/emulated/0/Movies"
        val folderB = "/storage/emulated/0/Anime"
        val videos = listOf(
            video("$folderA/a.mp4", folderA, "a.mp4"),
            video("$folderA/b.mp4", folderA, "b.mp4"),
            video("$folderB/c.mp4", folderB, "c.mp4")
        )

        val entries = groupLibraryByWatchedFolder(
            videos = videos,
            watchedFolders = listOf(folderB, folderA),
            expandedFolders = setOf(folderA)
        )

        assertThat(entries).hasSize(4)
        assertThat(entries[0]).isInstanceOf(VideoListEntry.FolderHeader::class.java)
        assertThat((entries[0] as VideoListEntry.FolderHeader).folderPath).isEqualTo(folderB)
        assertThat(entries[1]).isInstanceOf(VideoListEntry.FolderHeader::class.java)
        assertThat((entries[1] as VideoListEntry.FolderHeader).folderPath).isEqualTo(folderA)
        assertThat(entries[2]).isInstanceOf(VideoListEntry.VideoEntryItem::class.java)
        assertThat((entries[2] as VideoListEntry.VideoEntryItem).fileName).isEqualTo("a.mp4")
    }

    @Test
    fun isFolderSelected_returnsTrueWhenFolderInSet() {
        assertThat(isFolderSelected("/storage/Movies", setOf("/storage/Movies"))).isTrue()
        assertThat(isFolderSelected("/storage/Movies", emptySet())).isFalse()
    }
}
