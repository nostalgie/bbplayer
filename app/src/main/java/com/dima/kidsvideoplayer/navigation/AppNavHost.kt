package com.dima.kidsvideoplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.screens.FilePickerScreen
import com.dima.kidsvideoplayer.ui.screens.KidPlayerScreen
import com.dima.kidsvideoplayer.ui.screens.ParentDashboardScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val KID_PLAYER = "kid_player"
    const val PARENT_DASHBOARD = "parent_dashboard"
    const val FILE_PICKER = "file_picker"
}

/**
 * Main navigation host for the app.
 *
 * @param navController Navigation controller
 * @param lockTaskManager Manages kiosk mode
 * @param videoRepository DataStore repository for video URIs
 * @param videoPlayerManager ExoPlayer wrapper
 * @param isLockTaskActive Reactive state for lock task status
 * @param onEnterKidMode Callback to start lock task mode
 * @param onExitKidMode Callback to stop lock task mode
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    lockTaskManager: LockTaskManager,
    videoRepository: VideoRepository,
    videoPlayerManager: VideoPlayerManager,
    isLockTaskActive: MutableState<Boolean>,
    onEnterKidMode: () -> Unit,
    onExitKidMode: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Routes.KID_PLAYER
    ) {
        composable(Routes.KID_PLAYER) {
            KidPlayerScreen(
                videoRepository = videoRepository,
                videoPlayerManager = videoPlayerManager,
                onSecretDoorActivated = {
                    // Navigate to parent dashboard when secret door is triggered
                    navController.navigate(Routes.PARENT_DASHBOARD) {
                        popUpTo(Routes.KID_PLAYER) { inclusive = false }
                    }
                },
                onExitKidMode = onExitKidMode,
                isLockTaskActive = isLockTaskActive.value
            )
        }

        composable(Routes.PARENT_DASHBOARD) {
            ParentDashboardScreen(
                videoRepository = videoRepository,
                onBackToKidMode = {
                    // Navigate back to kid player and enter kiosk mode
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                    onEnterKidMode()
                },
                onNavigateToFilePicker = {
                    navController.navigate(Routes.FILE_PICKER)
                }
            )
        }

        composable(Routes.FILE_PICKER) {
            FilePickerScreen(
                videoRepository = videoRepository,
                onBack = {
                    navController.popBackStack(Routes.PARENT_DASHBOARD, inclusive = false)
                }
            )
        }
    }
}
