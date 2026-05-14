package com.dima.kidsvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.PinDialog
import kotlinx.coroutines.delay

/**
 * Kid Player Screen — fullscreen video player with navigation buttons.
 *
 * Features:
 * - ExoPlayer video playback via PlayerView
 * - Prev/Next bounce buttons at the bottom
 * - "v1.0" text in corner — secret door (long press 3 seconds → PIN dialog)
 * - PIN dialog → if correct, navigates to Parent Dashboard
 */
@Composable
fun KidPlayerScreen(
    videoRepository: VideoRepository,
    videoPlayerManager: VideoPlayerManager,
    onSecretDoorActivated: () -> Unit,
    onExitKidMode: () -> Unit,
    isLockTaskActive: Boolean
) {
    val context = LocalContext.current
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())

    // PIN dialog state
    var showPinDialog by remember { mutableStateOf(false) }

    // Initialize player when URIs change
    LaunchedEffect(videoUris) {
        if (videoUris.isNotEmpty()) {
            videoPlayerManager.setVideoList(videoUris)
        }
    }

    // Create PlayerView once
    val exoPlayer = remember {
        videoPlayerManager.initialize()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayerManager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ==============================
        // Video Player (full screen)
        // ==============================
        if (videoUris.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Hide default controls — we have custom buttons
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // No videos — show placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎬 Нет видео\n\nРодитель может добавить видео\nчерез настройки",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // ==============================
        // Navigation Buttons (bottom)
        // ==============================
        if (videoUris.isNotEmpty()) {
            // Track playing state reactively so the play/pause icon updates
            var isPlaying by remember {
                mutableStateOf(
                    exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY
                )
            }

            // Listen for playback state changes to update the play/pause button icon
            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        isPlaying = playWhenReady && exoPlayer.playbackState == Player.STATE_READY
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isPlaying = exoPlayer.playWhenReady && playbackState == Player.STATE_READY
                    }
                }
                exoPlayer.addListener(listener)
                onDispose { exoPlayer.removeListener(listener) }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button (only when multiple videos)
                if (videoUris.size > 1) {
                    BounceButton(
                        text = "⏮",
                        onClick = { videoPlayerManager.previous() },
                        backgroundColor = Color(0xFF42A5F5),
                        textColor = Color.White,
                        size = 80.dp,
                        fontSize = 36.sp
                    )
                }

                // Play/Pause button (always visible when videos exist)
                BounceButton(
                    text = if (isPlaying) "⏸" else "▶",
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    backgroundColor = Color(0xFF4CAF50),
                    textColor = Color.White,
                    size = 90.dp,
                    fontSize = 36.sp
                )

                // Next button (only when multiple videos)
                if (videoUris.size > 1) {
                    BounceButton(
                        text = "⏭",
                        onClick = { videoPlayerManager.next() },
                        backgroundColor = Color(0xFF42A5F5),
                        textColor = Color.White,
                        size = 80.dp,
                        fontSize = 36.sp
                    )
                }
            }
        }

        // ==============================
        // Version Text — Secret Door
        // ==============================
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 8.dp)
        ) {
            Text(
                text = "v1.0",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.2f),
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                // Secret door activated — show PIN dialog
                                showPinDialog = true
                            }
                        )
                    }
            )
        }
    }

    // ==============================
    // PIN Dialog
    // ==============================
    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onPinCorrect = {
                showPinDialog = false
                onExitKidMode()
                onSecretDoorActivated()
            }
        )
    }
}

// Extension property to check if player is playing
private val Player.isPlaying: Boolean
    get() = playbackState == Player.STATE_READY && playWhenReady
