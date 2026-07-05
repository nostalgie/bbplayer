package com.dima.kidsvideoplayer.ui.screens.filepicker

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class FileSystemServiceTest {

    @Test
    fun isVideoFile_recognizesCommonExtensions() {
        assertThat(isVideoFile("movie.mp4")).isTrue()
        assertThat(isVideoFile("movie.MKV")).isTrue()
        assertThat(isVideoFile("movie.avi")).isTrue()
        assertThat(isVideoFile("movie.txt")).isFalse()
        assertThat(isVideoFile("noextension")).isFalse()
    }

    @Test
    fun formatFileSize_formatsBytes() {
        assertThat(formatFileSize(0)).isEqualTo("0 Б")
        assertThat(formatFileSize(1024)).contains("КБ")
    }

    @Test
    fun listDirectoryItems_returnsEmptyForMissingPath() {
        assertThat(listDirectoryItems("/nonexistent/path/12345")).isEmpty()
    }

    @Test
    fun listDirectoryItems_listsVideosInTempDir() {
        val dir = createTempDir("filepicker-test")
        try {
            File(dir, "video.mp4").writeText("test")
            File(dir, "readme.txt").writeText("test")
            File(dir, "subdir").mkdir()

            val items = listDirectoryItems(dir.absolutePath)
            assertThat(items.map { it.name }).containsExactly("subdir", "video.mp4")
            assertThat(items.first { it.name == "video.mp4" }.isFile).isTrue()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun findVideosRecursively_findsNestedVideos() {
        val dir = createTempDir("filepicker-recursive")
        try {
            val sub = File(dir, "nested").also { it.mkdir() }
            File(sub, "clip.mkv").writeText("test")

            val videos = runBlocking { findVideosRecursively(dir) }
            assertThat(videos).hasSize(1)
            assertThat(videos[0].name).isEqualTo("clip.mkv")
        } finally {
            dir.deleteRecursively()
        }
    }
}
