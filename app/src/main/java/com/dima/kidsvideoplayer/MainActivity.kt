package com.dima.kidsvideoplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.navigation.AppNavHost
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.theme.KidsVideoPlayerTheme

class MainActivity : ComponentActivity() {

    private lateinit var lockTaskManager: LockTaskManager
    private lateinit var videoRepository: VideoRepository
    private lateinit var videoPlayerManager: VideoPlayerManager

    // Track whether we're currently in kiosk/lock-task mode
    private var isLockTaskActive = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers — use Application context to avoid memory leaks
        val appContext = applicationContext
        lockTaskManager = LockTaskManager(appContext)
        videoRepository = VideoRepository(appContext)
        videoPlayerManager = VideoPlayerManager(appContext)

        // Full immersive mode — hide status bar and navigation bar
        enableEdgeToEdge()
        hideSystemUI()

        // Set up Compose content
        setContent {
            KidsVideoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val appState = rememberAppState(
                        lockTaskManager = lockTaskManager,
                        videoRepository = videoRepository,
                        videoPlayerManager = videoPlayerManager,
                        onEnterKidMode = { enterKidMode() },
                        onExitKidMode = { exitKidMode() },
                        onExitApp = { exitApp() }
                    )

                    // Sync lock task state from Activity to AppState
                    LaunchedEffect(isLockTaskActive.value) {
                        // AppState reads from Activity's ground truth
                    }

                    AppNavHost(
                        navController = navController,
                        appState = appState
                    )
                }
            }
        }

        // Wait for content to be laid out, then splash screen disappears
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.player?.pause()
    }

    override fun onResume() {
        super.onResume()
        // Only hide system UI when in kiosk/lock-task mode
        if (isLockTaskActive.value) {
            hideSystemUI()
        }
        // Only auto-play when in kiosk mode (kid player screen is active)
        if (isLockTaskActive.value) {
            videoPlayerManager.player?.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safety net: ensure kiosk mode is always released
        try {
            lockTaskManager.stopKioskMode(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping kiosk mode in onDestroy", e)
        }
        videoPlayerManager.release()
    }

    // ==============================
    // Lock Task Mode Management
    // ==============================

    /**
     * Enter Kid Mode: start Lock Task (Kiosk Mode).
     * This pins the app to the screen so the child cannot leave.
     */
    private fun enterKidMode() {
        Log.d(TAG, "Entering Kid Mode — starting Lock Task")
        lockTaskManager.startKioskMode(this)
        isLockTaskActive.value = true
        hideSystemUI()
    }

    /**
     * Exit Kid Mode: stop Lock Task.
     * Called after parent successfully enters PIN code.
     */
    private fun exitKidMode() {
        Log.d(TAG, "Exiting Kid Mode — stopping Lock Task")
        lockTaskManager.stopKioskMode(this)
        isLockTaskActive.value = false
    }

    /**
     * Exit the app completely.
     * Called from the Parent Dashboard "Exit App" button.
     */
    private fun exitApp() {
        Log.d(TAG, "Exiting app from Parent Dashboard")
        exitKidMode()
        finishAffinity()
    }

    // ==============================
    // Full Immersive Mode
    // ==============================

    /**
     * Hide status bar, navigation bar — full immersive sticky mode.
     */
    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(android.view.WindowInsets.Type.systemBars())
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
