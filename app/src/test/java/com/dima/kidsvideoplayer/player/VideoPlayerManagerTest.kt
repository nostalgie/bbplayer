/**
 * Tests for [VideoPlayerManager] playlist index logic without initializing libVLC.
 */
package com.dima.kidsvideoplayer.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoPlayerManagerTest {

    private lateinit var context: Context
    private lateinit var manager: VideoPlayerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = VideoPlayerManager(context)
    }

    @Test
    fun setVideoList_setsStartIndexWithoutNativePlayer() {
        val uris = listOf("file:///a.avi", "file:///b.avi", "file:///c.avi")
        manager.setVideoList(uris, startIndex = 2)
        assertThat(manager.currentMediaItemIndex).isEqualTo(2)
        assertThat(manager.getMediaItemCount()).isEqualTo(3)
    }

    @Test
    fun setVideoList_coercesStartIndexBeyondListSize() {
        val uris = listOf("file:///a.avi", "file:///b.avi")
        manager.setVideoList(uris, startIndex = 10)
        assertThat(manager.currentMediaItemIndex).isEqualTo(1)
    }

    @Test
    fun setVideoList_withEmptyList_resetsIndex() {
        manager.setPlaylistForTesting(listOf("file:///a.avi"))
        manager.setVideoList(emptyList())
        assertThat(manager.currentMediaItemIndex).isEqualTo(0)
        assertThat(manager.getMediaItemCount()).isEqualTo(0)
    }

    @Test
    fun next_wrapsToStartOfPlaylist() {
        manager.setVideoList(listOf("file:///a.avi", "file:///b.avi", "file:///c.avi"), startIndex = 2)
        manager.next()
        assertThat(manager.currentMediaItemIndex).isEqualTo(0)
    }

    @Test
    fun previous_wrapsToEndOfPlaylist() {
        manager.setVideoList(listOf("file:///a.avi", "file:///b.avi", "file:///c.avi"), startIndex = 0)
        manager.previous()
        assertThat(manager.currentMediaItemIndex).isEqualTo(2)
    }

    @Test
    fun getCurrentVideoUri_returnsActiveItem() {
        val uris = listOf("file:///a.avi", "file:///b.avi")
        manager.setVideoList(uris, startIndex = 1)
        assertThat(manager.getCurrentVideoUri()).isEqualTo("file:///b.avi")
    }

    @Test
    fun release_withoutInitialize_doesNotThrow() {
        manager.release()
        assertThat(manager.getMediaItemCount()).isEqualTo(0)
    }
}
