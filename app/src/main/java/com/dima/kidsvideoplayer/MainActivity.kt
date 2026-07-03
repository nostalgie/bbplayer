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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
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

    /** Single source of truth for kiosk state — set from Compose [AppState]. */
    private var appState: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContext = applicationContext
        lockTaskManager = LockTaskManager(appContext)
        videoRepository = VideoRepository(appContext)
        videoCompatibilityChecker = VideoCompatibilityChecker(appContext)
        playbackStateRepository = PlaybackStateRepository(appContext)

        requestVideoPermissionIfNeeded()

        enableEdgeToEdge()
        hideSystemUI()

        setContent {
            KidsVideoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val state = rememberAppState(
                        lockTaskManager = lockTaskManager,
                        videoRepository = videoRepository,
                        videoPlayerManager = videoPlayerManager,
                        videoCompatibilityChecker = videoCompatibilityChecker,
                        playbackStateRepository = playbackStateRepository,
                        onEnterKidMode = { enterKidMode() },
                        onExitKidMode = { exitKidMode() },
                        onExitApp = { exitApp() }
                    )

                    SideEffect { appState = state }

                    AppNavHost(
                        navController = navController,
                        appState = state
                    )
                }
            }
        }

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
        videoPlayerManager.pause()
        savePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()

        if (appState?.isLockTaskActive == true) {
            if (!lockTaskManager.isLockTaskRunning()) {
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

    private fun enterKidMode() {
        Log.d(TAG, "Entering Kid Mode — starting Lock Task")
        if (lockTaskManager.isDeviceOwner()) {
            lockTaskManager.applyKioskPolicies()
        }
        lockTaskManager.startKioskMode(this)
        hideSystemUI()
    }

    private fun exitKidMode() {
        Log.d(TAG, "Exiting Kid Mode — stopping Lock Task")
        lockTaskManager.stopKioskMode(this)
    }

    private fun exitApp() {
        Log.d(TAG, "Full admin exit — de-kiosk and relinquish Device Owner")
        exitKidMode()
        lockTaskManager.removeKioskPolicies()
        lockTaskManager.relinquishDeviceOwner()
        videoPlayerManager.release()
        finishAffinity()
    }

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
