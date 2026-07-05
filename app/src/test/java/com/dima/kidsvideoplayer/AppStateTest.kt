package com.dima.kidsvideoplayer

import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.data.VideoLibraryService
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock

class AppStateTest {

    private fun createState(onSuspendKiosk: () -> Unit = {}): AppState {
        return AppState(
            lockTaskManager = mock<LockTaskManager>(),
            videoRepository = mock<VideoRepository>(),
            videoLibraryService = mock<VideoLibraryService>(),
            videoPlayerManager = mock<VideoPlayerManager>(),
            playbackStateRepository = mock<PlaybackStateRepository>(),
            onEnterKidMode = {},
            onExitKidMode = {},
            onSuspendKiosk = onSuspendKiosk
        )
    }

    @Test
    fun suspendKiosk_setsExitingToHomeAndCallsCallback() {
        var callbackCalled = false
        val state = createState(onSuspendKiosk = { callbackCalled = true })

        state.suspendKiosk()

        assertThat(state.exitingToHome).isTrue()
        assertThat(state.isLockTaskActive).isFalse()
        assertThat(callbackCalled).isTrue()
    }

    @Test
    fun resetToKidMode_defaultsToFalse() {
        val state = createState()
        assertThat(state.resetToKidMode).isFalse()
    }

    @Test
    fun encodeForSave_persistsLockTaskAndExitingFlags() {
        assertThat(AppState.encodeForSave(isLockTaskActive = true, exitingToHome = true))
            .containsExactly("1", "1")
        assertThat(AppState.encodeForSave(isLockTaskActive = false, exitingToHome = false))
            .containsExactly("0", "0")
    }

    @Test
    fun decodeExitingToHome_restoresFromSaver() {
        assertThat(AppState.decodeExitingToHome(listOf("0", "1"), suspendedFromKiosk = false))
            .isTrue()
        assertThat(AppState.decodeExitingToHome(listOf("0", "0"), suspendedFromKiosk = false))
            .isFalse()
    }

    @Test
    fun decodeExitingToHome_usesProcessFlagWhenSaverMissing() {
        assertThat(AppState.decodeExitingToHome(listOf("0"), suspendedFromKiosk = true))
            .isTrue()
    }
}
