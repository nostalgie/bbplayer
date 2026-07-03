package com.dima.kidsvideoplayer.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.videolan.libvlc.util.VLCVideoLayout
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.SeekButton
import com.dima.kidsvideoplayer.ui.theme.BlueButton
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import kotlinx.coroutines.delay

/**
 * Kid Player Screen — fullscreen video player with navigation buttons.
 *
 * Features:
 * - libVLC video playback via VLCVideoLayout
 * - Prev/Next bounce buttons at the bottom
 * - Settings gear in top-right corner — secret door (long press 3 seconds → Parent Dashboard)
 */
@Composable
fun KidPlayerScreen(
    videoRepository: VideoRepository,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    onSecretDoorActivated: () -> Unit,
    isLockTaskActive: Boolean,
    pendingStartVideoIndex: Int = -1
) {
    val secretDoorTouchSizePx = with(LocalDensity.current) { SECRET_DOOR_TOUCH_SIZE.toPx() }
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedVideos by videoRepository.selectedVideos.collectAsStateWithLifecycle(initialValue = emptySet())
    
    // Filter to only show selected videos
    val filteredVideoUris = remember(videoUris, selectedVideos) {
        if (selectedVideos.isEmpty()) {
            emptyList()
        } else {
            videoUris.filter { selectedVideos.contains(it) }
        }
    }

    // Track whether this is the first launch with videos (for restoring state)
    var hasRestoredState by remember { mutableStateOf(false) }

    // Load playlist when URIs change or when a specific video is requested
    LaunchedEffect(filteredVideoUris, pendingStartVideoIndex) {
        videoPlayerManager.initialize()

        if (filteredVideoUris.isEmpty()) {
            videoPlayerManager.setVideoList(emptyList())
            return@LaunchedEffect
        }

        val startIndex: Int
        val startPositionMs: Long

        if (pendingStartVideoIndex >= 0) {
            startIndex = pendingStartVideoIndex.coerceIn(0, filteredVideoUris.lastIndex)
            startPositionMs = 0L
        } else if (!hasRestoredState) {
            hasRestoredState = true
            val saved = playbackStateRepository.get()
            if (saved != null) {
                val savedIndex = filteredVideoUris.indexOf(saved.videoUri)
                if (savedIndex >= 0) {
                    Log.d(TAG, "Restoring playback: uri=${saved.videoUri}, position=${saved.positionMs}")
                    startIndex = savedIndex
                    startPositionMs = saved.positionMs
                } else {
                    Log.d(TAG, "Saved video no longer in playlist, starting from first")
                    startIndex = 0
                    startPositionMs = 0L
                    playbackStateRepository.clear()
                }
            } else {
                startIndex = 0
                startPositionMs = 0L
            }
        } else {
            startIndex = 0
            startPositionMs = 0L
        }

        Log.d(TAG, "Loading playlist: ${filteredVideoUris.size} videos, startIndex=$startIndex")
        videoPlayerManager.setVideoList(filteredVideoUris, startIndex, startPositionMs)
    }

    var playbackError by remember { mutableStateOf<String?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    // Interaction counter — incremented on any user action to reset the auto-hide timer
    var controlsInteraction by remember { mutableStateOf(0) }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible, controlsInteraction) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Progress tracking
    var sliderValue by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1L) }
    var isSeeking by remember { mutableStateOf(false) }

    // Update progress only while controls are visible — avoids recomposition every 250ms in kid mode
    LaunchedEffect(videoPlayerManager, controlsVisible, isSeeking) {
        if (!controlsVisible) return@LaunchedEffect
        while (controlsVisible) {
            if (!isSeeking) {
                val pos = videoPlayerManager.currentPosition
                val dur = videoPlayerManager.duration.coerceAtLeast(1L)
                duration = dur
                sliderValue = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // Periodically save playback state every 5 seconds
    LaunchedEffect(videoPlayerManager, filteredVideoUris) {
        if (filteredVideoUris.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val currentIndex = videoPlayerManager.currentMediaItemIndex
            if (currentIndex in filteredVideoUris.indices) {
                val positionMs = videoPlayerManager.currentPosition
                playbackStateRepository.save(
                    PlaybackStateRepository.PlaybackState(
                        videoUri = filteredVideoUris[currentIndex],
                        positionMs = positionMs
                    )
                )
            }
        }
    }

    DisposableEffect(videoPlayerManager) {
        videoPlayerManager.playbackListener = VideoPlayerManager.PlaybackListener(
            onError = { error ->
                playbackError = error
                playbackStateRepository.clear()
            },
            onReady = { playbackError = null },
            onPlayingChanged = { /* updated via isPlaying poll in controls */ }
        )
        onDispose { videoPlayerManager.playbackListener = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ==============================
        // Video Player (full screen)
        // ==============================
        if (filteredVideoUris.isNotEmpty()) {
            AndroidView(
                factory = { ctx -> VLCVideoLayout(ctx) },
                update = { layout -> videoPlayerManager.attachVideoLayout(layout) },
                onRelease = { videoPlayerManager.detachVideoLayout() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // No videos — show placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (videoUris.isEmpty()) {
                    Text(
                        text = "🎬 Нет видео\n\nРодитель может добавить видео\nчерез настройки",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "🎬 Нет выбранных видео\n\nПожалуйста, выберите видео\nв настройках родителя",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        playbackError?.let { error ->
            Text(
                text = "⚠️ Не удалось воспроизвести видео\n($error)",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }

        // ==============================
        // Tap overlay — toggle controls on tap
        // ==============================
        if (filteredVideoUris.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(controlsVisible, controlsInteraction, secretDoorTouchSizePx) {
                        detectTapGestures { offset ->
                            val inSecretZone = offset.x >= size.width - secretDoorTouchSizePx &&
                                offset.y <= secretDoorTouchSizePx
                            if (!inSecretZone) {
                                controlsVisible = !controlsVisible
                                controlsInteraction++
                            }
                        }
                    }
            )
        }

        // ==============================
        // Controls (buttons + progress bar) with fade animation
        // ==============================
        if (filteredVideoUris.isNotEmpty()) {
            // Track playing state reactively so the play/pause icon updates
            var isPlaying by remember { mutableStateOf(videoPlayerManager.isPlaying) }

            LaunchedEffect(videoPlayerManager, controlsVisible) {
                while (controlsVisible) {
                    isPlaying = videoPlayerManager.isPlaying
                    delay(300)
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(bottom = 16.dp)
                ) {
                    // Progress bar
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            isSeeking = true
                            sliderValue = newValue
                            controlsInteraction++
                        },
                        onValueChangeFinished = {
                            videoPlayerManager.seekTo((sliderValue * duration).toLong())
                            isSeeking = false
                            controlsInteraction++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = GreenPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    // Navigation buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button — always visible, disabled (grayed out) when single video
                        val hasMultipleVideos = filteredVideoUris.size > 1
                        BounceButton(
                            text = "⏮",
                            onClick = {
                                controlsInteraction++
                                if (hasMultipleVideos) {
                                    videoPlayerManager.previous()
                                }
                            },
                            backgroundColor = BlueButton,
                            textColor = Color.White,
                            size = 80.dp,
                            fontSize = 36.sp,
                            modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                        )

                        // Seek backward button
                        SeekButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Seek backward",
                            onSeek = { offsetMs ->
                                controlsInteraction++
                                videoPlayerManager.seekBackward(offsetMs)
                            }
                        )

                        // Play/Pause button (always visible when videos exist)
                        BounceButton(
                            text = if (isPlaying) "⏸" else "▶",
                            onClick = {
                                controlsInteraction++
                                if (videoPlayerManager.isPlaying) {
                                    videoPlayerManager.pause()
                                } else {
                                    videoPlayerManager.play()
                                }
                            },
                            backgroundColor = GreenPrimary,
                            textColor = Color.White,
                            size = 90.dp,
                            fontSize = 36.sp
                        )

                        // Seek forward button
                        SeekButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = "Seek forward",
                            onSeek = { offsetMs ->
                                controlsInteraction++
                                videoPlayerManager.seekForward(offsetMs)
                            }
                        )

                        // Next button — always visible, disabled (grayed out) when single video
                        BounceButton(
                            text = "⏭",
                            onClick = {
                                controlsInteraction++
                                if (hasMultipleVideos) {
                                    videoPlayerManager.next()
                                }
                            },
                            backgroundColor = BlueButton,
                            textColor = Color.White,
                            size = 80.dp,
                            fontSize = 36.sp,
                            modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                        )
                    }
                }
            }
        }

        // ==============================
        // Settings gear — Secret Door (long press 3 seconds)
        // ==============================
        var isSettingsPressed by remember { mutableStateOf(false) }
        var holdProgress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isSettingsPressed) {
            if (!isSettingsPressed) {
                holdProgress = 0f
                return@LaunchedEffect
            }
            holdProgress = 0f
            val steps = (SECRET_DOOR_HOLD_MS / HOLD_PROGRESS_STEP_MS).toInt()
            repeat(steps) { step ->
                delay(HOLD_PROGRESS_STEP_MS)
                if (!isSettingsPressed) return@LaunchedEffect
                holdProgress = (step + 1).toFloat() / steps
            }
            if (isSettingsPressed) {
                onSecretDoorActivated()
                isSettingsPressed = false
                holdProgress = 0f
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(SECRET_DOOR_TOUCH_SIZE)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        isSettingsPressed = true
                        waitForUpOrCancellation()
                        isSettingsPressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White.copy(
                    alpha = if (isSettingsPressed) {
                        0.4f + 0.5f * holdProgress
                    } else {
                        0.4f
                    }
                )
            )
        }
    }
}

private const val TAG = "KidPlayerScreen"

/** Touch target for the secret-door settings gear (top-right). */
private val SECRET_DOOR_TOUCH_SIZE = 72.dp
private const val SECRET_DOOR_HOLD_MS = 3000L
private const val HOLD_PROGRESS_STEP_MS = 100L
