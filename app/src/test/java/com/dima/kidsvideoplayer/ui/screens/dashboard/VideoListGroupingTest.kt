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
    fun videoCountForFolder_countsVideosInRootFolder() {
        val folderA = "/storage/emulated/0/Movies"
        val folderB = "/storage/emulated/0/Anime"
        val videos = listOf(
            video("$folderA/a.mp4", folderA, "a.mp4"),
            video("$folderA/b.mp4", folderA, "b.mp4"),
            video("$folderB/c.mp4", folderB, "c.mp4")
        )

        assertThat(videoCountForFolder(videos, folderA)).isEqualTo(2)
        assertThat(videoCountForFolder(videos, folderB)).isEqualTo(1)
        assertThat(videoCountForFolder(videos, "/missing")).isEqualTo(0)
    }

    @Test
    fun buildVideosByParentPath_groupsByImmediateParent() {
        val root = "/storage/emulated/0/Movies"
        val sub = "$root/Season1"
        val videos = listOf(
            video("$root/trailer.mp4", root, "trailer.mp4"),
            video("$sub/ep1.mp4", root, "ep1.mp4"),
            video("$sub/ep2.mp4", root, "ep2.mp4")
        )

        val byParent = buildVideosByParentPath(videos)

        assertThat(byParent[root]).hasSize(1)
        assertThat(byParent[sub]).hasSize(2)
    }

    @Test
    fun buildLibraryIndexByPath_mapsFilePathToIndex() {
        val folder = "/storage/emulated/0/Movies"
        val videos = listOf(
            video("$folder/a.mp4", folder, "a.mp4"),
            video("$folder/b.mp4", folder, "b.mp4")
        )

        val indexByPath = buildLibraryIndexByPath(videos)

        assertThat(indexByPath["$folder/a.mp4"]).isEqualTo(0)
        assertThat(indexByPath["$folder/b.mp4"]).isEqualTo(1)
    }

    @Test
    fun parentBrowsePath_navigatesWithinWatchedTree() {
        val root = "/storage/emulated/0/Movies"
        val sub = "$root/Anime"
        val watched = listOf(root)

        assertThat(parentBrowsePath(sub, watched)).isEqualTo(root)
        assertThat(parentBrowsePath(root, watched)).isNull()
        assertThat(parentBrowsePath("/other/path", watched)).isNull()
    }

    @Test
    fun isPathWithinWatchedFolders_checksPrefix() {
        val root = "/storage/emulated/0/Movies"
        val watched = listOf(root)

        assertThat(isPathWithinWatchedFolders(root, watched)).isTrue()
        assertThat(isPathWithinWatchedFolders("$root/Sub", watched)).isTrue()
        assertThat(isPathWithinWatchedFolders("/other", watched)).isFalse()
    }
}
