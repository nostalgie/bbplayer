/**
 * Tests for [VideoPlayerManager] — verifies playlist management logic
 * (setVideoList, next, previous, release, getMediaItemCount) using a mocked ExoPlayer.
 *
 * Robolectric is used so that android.net.Uri and MediaItem can be constructed
 * without a real device.
 */
package com.dima.kidsvideoplayer.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoPlayerManagerTest {

    private lateinit var context: Context
    private lateinit var manager: VideoPlayerManager
    private lateinit var mockPlayer: ExoPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = VideoPlayerManager(context)
        mockPlayer = mock()
        // Inject mock player via test-only setter
        manager.setPlayerForTesting(mockPlayer)
    }

    // --- initialize() ---

    @Test
    fun initialize_returnsExistingPlayerWhenAlreadySet() {
        val result = manager.initialize()
        assertThat(result).isSameInstanceAs(mockPlayer)
    }

    // --- setVideoList() ---

    @Test
    fun setVideoList_clearsAndAddsMediaItems() {
        val uris = listOf("content://media/1", "content://media/2", "content://media/3")
        manager.setVideoList(uris)

        verify(mockPlayer).clearMediaItems()
        verify(mockPlayer, times(3)).addMediaItem(any())
        verify(mockPlayer).seekTo(0, 0L)
        verify(mockPlayer).prepare()
        assertThat(manager.currentMediaItemIndex).isEqualTo(0)
    }

    @Test
    fun setVideoList_withStartIndex_seeksToCorrectPosition() {
        val uris = listOf("content://media/1", "content://media/2", "content://media/3")
        manager.setVideoList(uris, startIndex = 2)

        verify(mockPlayer).seekTo(2, 0L)
        assertThat(manager.currentMediaItemIndex).isEqualTo(2)
    }

    @Test
    fun setVideoList_withStartPosition_seeksToCorrectPosition() {
        val uris = listOf("content://media/1", "content://media/2", "content://media/3")
        manager.setVideoList(uris, startIndex = 1, startPositionMs = 5000L)

        verify(mockPlayer).seekTo(1, 5000L)
        assertThat(manager.currentMediaItemIndex).isEqualTo(1)
    }

    @Test
    fun setVideoList_coercesStartIndexBeyondListSize() {
        val uris = listOf("content://media/1", "content://media/2")
        manager.setVideoList(uris, startIndex = 10)

        // startIndex should be coerced to last valid index (1)
        verify(mockPlayer).seekTo(1, 0L)
        // currentMediaItemIndex is now also coerced (was a bug before fix)
        assertThat(manager.currentMediaItemIndex).isEqualTo(1)
    }

    @Test
    fun setVideoList_withEmptyList_clearsItemsWithoutPreparing() {
        manager.setVideoList(emptyList())

        verify(mockPlayer).clearMediaItems()
        verify(mockPlayer, never()).prepare()
        verify(mockPlayer, never()).seekTo(any(), any())
    }

    @Test
    fun setVideoList_doesNothingWhenPlayerIsNull() {
        manager.setPlayerForTesting(null)
        // Should not throw
        manager.setVideoList(listOf("content://media/1"))
    }

    // --- next() ---

    @Test
    fun next_navigatesToNextMediaItem() {
        whenever(mockPlayer.hasNextMediaItem()).thenReturn(true)
        whenever(mockPlayer.currentMediaItemIndex).thenReturn(2)

        manager.next()

        verify(mockPlayer).seekToNext()
        assertThat(manager.currentMediaItemIndex).isEqualTo(2)
    }

    @Test
    fun next_doesNothingWhenNoNextItem() {
        whenever(mockPlayer.hasNextMediaItem()).thenReturn(false)

        manager.next()

        verify(mockPlayer, never()).seekToNext()
    }

    @Test
    fun next_doesNothingWhenPlayerIsNull() {
        manager.setPlayerForTesting(null)
        // Should not throw
        manager.next()
    }

    // --- previous() ---

    @Test
    fun previous_navigatesToPreviousMediaItem() {
        whenever(mockPlayer.hasPreviousMediaItem()).thenReturn(true)
        whenever(mockPlayer.currentMediaItemIndex).thenReturn(0)

        manager.previous()

        verify(mockPlayer).seekToPrevious()
        assertThat(manager.currentMediaItemIndex).isEqualTo(0)
    }

    @Test
    fun previous_doesNothingWhenNoPreviousItem() {
        whenever(mockPlayer.hasPreviousMediaItem()).thenReturn(false)

        manager.previous()

        verify(mockPlayer, never()).seekToPrevious()
    }

    @Test
    fun previous_doesNothingWhenPlayerIsNull() {
        manager.setPlayerForTesting(null)
        // Should not throw
        manager.previous()
    }

    // --- getMediaItemCount() ---

    @Test
    fun getMediaItemCount_returnsPlayerItemCount() {
        whenever(mockPlayer.mediaItemCount).thenReturn(5)

        assertThat(manager.getMediaItemCount()).isEqualTo(5)
    }

    @Test
    fun getMediaItemCount_returnsZeroWhenPlayerIsNull() {
        manager.setPlayerForTesting(null)

        assertThat(manager.getMediaItemCount()).isEqualTo(0)
    }

    // --- release() ---

    @Test
    fun release_releasesPlayerAndSetsToNull() {
        manager.release()

        verify(mockPlayer).release()
        assertThat(manager.player).isNull()
    }

    @Test
    fun release_whenPlayerIsNull_doesNotThrow() {
        manager.setPlayerForTesting(null)
        // Should not throw
        manager.release()
    }

    @Test
    fun release_thenGetMediaItemCount_returnsZero() {
        whenever(mockPlayer.mediaItemCount).thenReturn(5)

        manager.release()

        assertThat(manager.getMediaItemCount()).isEqualTo(0)
    }
}
