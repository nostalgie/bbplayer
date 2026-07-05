package com.dima.kidsvideoplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dima.kidsvideoplayer.AppState
import com.dima.kidsvideoplayer.ui.screens.FilePickerScreen
import com.dima.kidsvideoplayer.ui.screens.KidPlayerScreen
import com.dima.kidsvideoplayer.ui.screens.ParentDashboardScreen
import kotlinx.coroutines.delay

/**
 * Navigation routes for the app.
 */
object Routes {
    const val KID_PLAYER = "kid_player"
    const val PARENT_DASHBOARD = "parent_dashboard"
    const val FILE_PICKER = "file_picker"
}

private const val KIOSK_START_DELAY_MS = 800L

/**
 * Main navigation host for the app.
 *
 * Uses [AppState] to access shared managers and callbacks,
 * reducing prop drilling through the navigation layer.
 *
 * @param navController Navigation controller
 * @param appState Centralized app state with managers and callbacks
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    appState: AppState
) {
    // Auto-enter kiosk after first frame so libVLC can initialize first.
    LaunchedEffect(Unit) {
        delay(KIOSK_START_DELAY_MS)
        if (!appState.kioskPausedForParent && !appState.lockTaskManager.isLockTaskRunning()) {
            appState.enterKidMode()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.KID_PLAYER
    ) {
        composable(Routes.KID_PLAYER) {
            LaunchedEffect(Unit) { appState.kioskPausedForParent = false }

            KidPlayerScreen(
                videoRepository = appState.videoRepository,
                videoLibraryService = appState.videoLibraryService,
                videoPlayerManager = appState.videoPlayerManager,
                playbackStateRepository = appState.playbackStateRepository,
                onSecretDoorActivated = {
                    appState.kioskPausedForParent = true
                    appState.exitKidMode()
                    appState.videoPlayerManager.pause()
                    navController.navigate(Routes.PARENT_DASHBOARD) {
                        popUpTo(Routes.KID_PLAYER) { inclusive = false }
                    }
                },
                pendingStartVideoIndex = appState.pendingStartVideoIndex,
                onPendingIndexConsumed = { appState.pendingStartVideoIndex = -1 }
            )
        }

        composable(Routes.PARENT_DASHBOARD) {
            LaunchedEffect(Unit) { appState.kioskPausedForParent = true }

            ParentDashboardScreen(
                videoRepository = appState.videoRepository,
                videoLibraryService = appState.videoLibraryService,
                onBackToKidMode = {
                    appState.kioskPausedForParent = false
                    appState.videoPlayerManager.play()
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                    appState.enterKidMode()
                },
                onNavigateToFilePicker = {
                    navController.navigate(Routes.FILE_PICKER)
                },
                onPlayVideo = { index ->
                    appState.pendingStartVideoIndex = index
                    appState.kioskPausedForParent = false
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                    appState.enterKidMode()
                },
                onExit = { appState.suspendKiosk() }
            )
        }

        composable(Routes.FILE_PICKER) {
            LaunchedEffect(Unit) { appState.kioskPausedForParent = true }

            FilePickerScreen(
                videoRepository = appState.videoRepository,
                onBack = {
                    appState.videoPlayerManager.play()
                    navController.popBackStack(Routes.PARENT_DASHBOARD, inclusive = false)
                }
            )
        }
    }
}
