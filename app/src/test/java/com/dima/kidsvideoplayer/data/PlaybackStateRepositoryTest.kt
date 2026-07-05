package com.dima.kidsvideoplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackStateRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PlaybackStateRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = PlaybackStateRepository(context)
        repository.clear()
    }

    @Test
    fun get_returnsNullWhenEmpty() {
        assertThat(repository.get()).isNull()
    }

    @Test
    fun saveAndGet_roundTripsState() {
        val state = PlaybackStateRepository.PlaybackState("file:///video.mp4", 12_345L)
        repository.save(state)
        assertThat(repository.get()).isEqualTo(state)
    }

    @Test
    fun save_overwritesPreviousState() {
        repository.save(PlaybackStateRepository.PlaybackState("file:///a.mp4", 100L))
        repository.save(PlaybackStateRepository.PlaybackState("file:///b.mp4", 200L))
        assertThat(repository.get()?.videoUri).isEqualTo("file:///b.mp4")
        assertThat(repository.get()?.positionMs).isEqualTo(200L)
    }

    @Test
    fun clear_removesSavedState() {
        repository.save(PlaybackStateRepository.PlaybackState("file:///video.mp4", 500L))
        repository.clear()
        assertThat(repository.get()).isNull()
    }

    @Test
    fun get_returnsZeroPositionWhenSaved() {
        repository.save(PlaybackStateRepository.PlaybackState("file:///video.mp4", 0L))
        assertThat(repository.get()?.positionMs).isEqualTo(0L)
    }

    @Test
    fun playbackState_dataClassEquality() {
        val a = PlaybackStateRepository.PlaybackState("uri", 1L)
        val b = PlaybackStateRepository.PlaybackState("uri", 1L)
        assertThat(a).isEqualTo(b)
    }
}
