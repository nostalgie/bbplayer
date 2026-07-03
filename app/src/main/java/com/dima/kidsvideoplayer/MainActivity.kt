package com.dima.kidsvideoplayer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.navigation.AppNavHost
import com.dima.kidsvideoplayer.player.VideoCompatibilityChecker
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.theme.KidsVideoPlayerTheme

class MainActivity : ComponentActivity() {

    private lateinit var lockTaskManager: LockTaskManager
    private lateinit var videoRepository: VideoRepository
    private val videoPlayerManager: VideoPlayerManager
        get() = (application as KidsVideoApp).videoPlayerManager
    private lateinit var videoCompatibilityChecker: VideoCompatibilityChecker
    private lateinit var playbackStateRepository: PlaybackStateRepository

    // Track whether we're currently in kiosk/lock-task mode
    private var isLockTaskActive = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers — use Application context to avoid memory leaks
        val appContext = applicationContext
        lockTaskManager = LockTaskManager(appContext)
        videoRepository = VideoRepository(appContext)
        videoCompatibilityChecker = VideoCompatibilityChecker(appContext)
        playbackStateRepository = PlaybackStateRepository(appContext)

        requestVideoPermissionIfNeeded()

        // Emergency: strip kiosk policies on launch (recover from crash/Smart Recovery loops).
        if (lockTaskManager.isDeviceOwner()) {
            lockTaskManager.removeKioskPolicies()
        }

        // Full immersive mode — hide status bar and navigation bar
        enableEdgeToEdge()
        hideSystemUI()

        // Apply kiosk device policies only when explicitly entering kid mode (not on cold start).
        // (Policies are removed above on launch for recovery.)

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
                        videoCompatibilityChecker = videoCompatibilityChecker,
                        playbackStateRepository = playbackStateRepository,
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

        // Kiosk is entered via enterKidMode() from UI — not automatically on cold start,
        // to avoid crash/relaunch loops when the app is also the HOME launcher.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.pause()

        // Save playback state when app goes to background
        savePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        // Always hide system UI
        hideSystemUI()

        // If in kiosk mode, ensure lock task is still active and resume playback
        if (isLockTaskActive.value) {
            if (!lockTaskManager.isLockTaskRunning() && lockTaskManager.isDeviceOwner()) {
                Log.d(TAG, "Re-starting kiosk mode in onResume (was lost)")
                lockTaskManager.startKioskMode(this)
            }
            videoPlayerManager.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT stop lock task or release the player here.
        // As the HOME launcher in kiosk mode, onDestroy runs on every restart;
        // releasing the player or stopping lock task causes a crash/relaunch loop.
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
        if (lockTaskManager.isDeviceOwner()) {
            lockTaskManager.applyKioskPolicies()
        }
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
        lockTaskManager.removeKioskPolicies()
        videoPlayerManager.release()
        finishAffinity()
    }

    // ==============================
    // Playback State Persistence
    // ==============================

    /**
     * Save current playback state (video URI + position) so we can resume later.
     * Called from [onPause] to capture the most up-to-date position.
     */
    private fun savePlaybackState() {
        val uri = videoPlayerManager.getCurrentVideoUri() ?: return
        val positionMs = videoPlayerManager.currentPosition

        playbackStateRepository.save(
            PlaybackStateRepository.PlaybackState(
                videoUri = uri,
                positionMs = positionMs
            )
        )
        Log.d(TAG, "Saved playback state: uri=$uri, position=${positionMs}ms")
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

    private fun requestVideoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO), 0)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
