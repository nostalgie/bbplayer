package com.dima.kidsvideoplayer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dima.kidsvideoplayer.AppState
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
    NavHost(
        navController = navController,
        startDestination = Routes.KID_PLAYER
    ) {
        composable(Routes.KID_PLAYER) {
            KidPlayerScreen(
                videoRepository = appState.videoRepository,
                videoPlayerManager = appState.videoPlayerManager,
                onSecretDoorActivated = {
                    // Navigate to parent dashboard when secret door is triggered
                    navController.navigate(Routes.PARENT_DASHBOARD) {
                        popUpTo(Routes.KID_PLAYER) { inclusive = false }
                    }
                },
                onExitKidMode = { appState.exitKidMode() },
                isLockTaskActive = appState.isLockTaskActive
            )
        }

        composable(Routes.PARENT_DASHBOARD) {
            ParentDashboardScreen(
                videoRepository = appState.videoRepository,
                onBackToKidMode = {
                    // Navigate back to kid player and enter kiosk mode
                    navController.popBackStack(Routes.KID_PLAYER, inclusive = false)
                    appState.enterKidMode()
                },
                onNavigateToFilePicker = {
                    navController.navigate(Routes.FILE_PICKER)
                },
                onExitApp = { appState.exitApp() }
            )
        }

        composable(Routes.FILE_PICKER) {
            FilePickerScreen(
                videoRepository = appState.videoRepository,
                onBack = {
                    navController.popBackStack(Routes.PARENT_DASHBOARD, inclusive = false)
                }
            )
        }
    }
}
