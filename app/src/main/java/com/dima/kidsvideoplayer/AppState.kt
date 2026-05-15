package com.dima.kidsvideoplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoCompatibilityChecker
import com.dima.kidsvideoplayer.player.VideoPlayerManager

/**
 * Centralized app state holding managers and lock-task status.
 *
 * Reduces prop drilling through navigation composables and provides
 * a single source of truth for shared state.
 */
@Stable
class AppState(
    val lockTaskManager: LockTaskManager,
    val videoRepository: VideoRepository,
    val videoPlayerManager: VideoPlayerManager,
    val videoCompatibilityChecker: VideoCompatibilityChecker,
    val onEnterKidMode: () -> Unit,
    val onExitKidMode: () -> Unit,
    val onExitApp: () -> Unit
) {
    /** Whether kiosk/lock-task mode is currently active. */
    var isLockTaskActive: Boolean by mutableStateOf(false)
        internal set

    /** Enter kiosk mode — updates state and delegates to [LockTaskManager]. */
    fun enterKidMode() {
        onEnterKidMode()
        isLockTaskActive = true
    }

    /** Exit kiosk mode — updates state and delegates to [LockTaskManager]. */
    fun exitKidMode() {
        onExitKidMode()
        isLockTaskActive = false
    }

    /** Exit the app completely. */
    fun exitApp() {
        onExitApp()
    }
}

/**
 * Remember [AppState] across recompositions.
 */
@Composable
fun rememberAppState(
    lockTaskManager: LockTaskManager,
    videoRepository: VideoRepository,
    videoPlayerManager: VideoPlayerManager,
    videoCompatibilityChecker: VideoCompatibilityChecker,
    onEnterKidMode: () -> Unit,
    onExitKidMode: () -> Unit,
    onExitApp: () -> Unit
): AppState {
    return rememberSaveable(
        saver = listSaver(
            save = { listOf(if (it.isLockTaskActive) "1" else "0") },
            restore = { saved ->
                AppState(
                    lockTaskManager = lockTaskManager,
                    videoRepository = videoRepository,
                    videoPlayerManager = videoPlayerManager,
                    videoCompatibilityChecker = videoCompatibilityChecker,
                    onEnterKidMode = onEnterKidMode,
                    onExitKidMode = onExitKidMode,
                    onExitApp = onExitApp
                ).also { state ->
                    state.isLockTaskActive = saved[0] == "1"
                }
            }
        )
    ) {
        AppState(
            lockTaskManager = lockTaskManager,
            videoRepository = videoRepository,
            videoPlayerManager = videoPlayerManager,
            videoCompatibilityChecker = videoCompatibilityChecker,
            onEnterKidMode = onEnterKidMode,
            onExitKidMode = onExitKidMode,
            onExitApp = onExitApp
        )
    }
}
