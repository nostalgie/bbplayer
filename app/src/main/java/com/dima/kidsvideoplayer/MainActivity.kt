package com.dima.kidsvideoplayer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import android.view.WindowManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dima.kidsvideoplayer.admin.LockTaskManager
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.navigation.AppNavHost
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.utils.StoragePermissionHelper
import com.dima.kidsvideoplayer.ui.theme.KidsVideoPlayerTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var lockTaskManager: LockTaskManager
    private val app: KidsVideoApp
        get() = application as KidsVideoApp
    private val videoRepository
        get() = app.videoRepository
    private val videoLibraryService
        get() = app.videoLibraryService
    private val videoPlayerManager: VideoPlayerManager
        get() = app.videoPlayerManager
    private lateinit var playbackStateRepository: PlaybackStateRepository

    /** Single source of truth for kiosk state — set from Compose [AppState]. */
    private var appState: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContext = applicationContext
        lockTaskManager = LockTaskManager(appContext)
        playbackStateRepository = PlaybackStateRepository(appContext)

        videoLibraryService.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                videoLibraryService.startPeriodicScan()
                try {
                    awaitCancellation()
                } finally {
                    videoLibraryService.stopPeriodicScan()
                }
            }
        }

        requestVideoPermissionIfNeeded()

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                        videoLibraryService = videoLibraryService,
                        videoPlayerManager = videoPlayerManager,
                        playbackStateRepository = playbackStateRepository,
                        onEnterKidMode = { enterKidMode() },
                        onExitKidMode = { exitKidMode() },
                        onSuspendKiosk = { suspendKiosk() }
                    )

                    SideEffect { appState = state }

                    AppNavHost(
                        navController = navController,
                        appState = state
                    )
                }
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        videoPlayerManager.pause()
        savePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        videoPlayerManager.refreshVideoSurface()

        val state = appState
        if (state != null &&
            !state.kioskPausedForParent &&
            lockTaskManager.isDeviceOwner() &&
            !lockTaskManager.isLockTaskRunning()
        ) {
            Log.d(TAG, "Re-starting kiosk mode in onResume")
            state.enterKidMode()
        }
        if (state?.isLockTaskActive == true) {
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

    private fun suspendKiosk() {
        Log.d(TAG, "Suspending kiosk — returning to home (Device Owner retained)")
        exitKidMode()
        lockTaskManager.removeKioskPolicies()
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
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
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            val permissions = StoragePermissionHelper.requiredPermissions()
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions, 0)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
