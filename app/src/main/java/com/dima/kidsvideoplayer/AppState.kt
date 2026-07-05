package com.dima.kidsvideoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.data.VideoLibraryService
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager

/**
 * Centralized app state holding managers and lock-task status.
 */
@Stable
class AppState(
    val lockTaskManager: LockTaskManager,
    val videoRepository: VideoRepository,
    val videoLibraryService: VideoLibraryService,
    val videoPlayerManager: VideoPlayerManager,
    val playbackStateRepository: PlaybackStateRepository,
    val onEnterKidMode: () -> Unit,
    val onExitKidMode: () -> Unit,
    val onSuspendKiosk: () -> Unit
) {
    var isLockTaskActive: Boolean by mutableStateOf(false)
        internal set

    /** True while parent dashboard or file picker is open — kiosk stays off. */
    var kioskPausedForParent: Boolean by mutableStateOf(false)
        internal set

    /** True while returning to home — blocks onResume from re-entering kiosk. */
    var exitingToHome: Boolean by mutableStateOf(false)
        internal set

    var pendingStartVideoIndex: Int by mutableStateOf(-1)
        internal set

    fun enterKidMode() {
        onEnterKidMode()
        isLockTaskActive = true
    }

    fun exitKidMode() {
        onExitKidMode()
        isLockTaskActive = false
    }

    fun suspendKiosk() {
        exitingToHome = true
        isLockTaskActive = false
        onSuspendKiosk()
    }

    companion object {
        internal fun encodeForSave(isLockTaskActive: Boolean, exitingToHome: Boolean): List<String> =
            listOf(
                if (isLockTaskActive) "1" else "0",
                if (exitingToHome) "1" else "0"
            )

        internal fun decodeExitingToHome(saved: List<String>, suspendedFromKiosk: Boolean): Boolean =
            saved.getOrNull(1) == "1" || suspendedFromKiosk
    }
}

@Composable
fun rememberAppState(
    lockTaskManager: LockTaskManager,
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    suspendedFromKiosk: Boolean,
    onEnterKidMode: () -> Unit,
    onExitKidMode: () -> Unit,
    onSuspendKiosk: () -> Unit
): AppState {
    return rememberSaveable(
        saver = listSaver(
            save = { AppState.encodeForSave(it.isLockTaskActive, it.exitingToHome) },
            restore = { saved ->
                AppState(
                    lockTaskManager = lockTaskManager,
                    videoRepository = videoRepository,
                    videoLibraryService = videoLibraryService,
                    videoPlayerManager = videoPlayerManager,
                    playbackStateRepository = playbackStateRepository,
                    onEnterKidMode = onEnterKidMode,
                    onExitKidMode = onExitKidMode,
                    onSuspendKiosk = onSuspendKiosk
                ).also { state ->
                    state.isLockTaskActive = saved[0] == "1"
                    state.exitingToHome = AppState.decodeExitingToHome(saved, suspendedFromKiosk)
                }
            }
        )
    ) {
        AppState(
            lockTaskManager = lockTaskManager,
            videoRepository = videoRepository,
            videoLibraryService = videoLibraryService,
            videoPlayerManager = videoPlayerManager,
            playbackStateRepository = playbackStateRepository,
            onEnterKidMode = onEnterKidMode,
            onExitKidMode = onExitKidMode,
            onSuspendKiosk = onSuspendKiosk
        ).also { state ->
            state.exitingToHome = suspendedFromKiosk
        }
    }
}
