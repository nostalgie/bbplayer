package com.dima.kidsvideoplayer.ui.screens

import android.graphics.Rect
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.dima.kidsvideoplayer.data.PlaybackStateRepository
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.PinDialog
import com.dima.kidsvideoplayer.ui.components.SeekButton
import com.dima.kidsvideoplayer.ui.theme.BlueButton
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import kotlinx.coroutines.delay

/**
 * Kid Player Screen — fullscreen video player with navigation buttons.
 *
 * Features:
 * - ExoPlayer video playback via PlayerView
 * - Prev/Next bounce buttons at the bottom
 * - "v1.0" text in top-right corner — secret door (long press 1 second → PIN dialog)
 * - PIN dialog → if correct, navigates to Parent Dashboard
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
    val context = LocalContext.current
    val rootView = LocalView.current
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

    // PIN dialog state
    var showPinDialog by remember { mutableStateOf(false) }

    // Track whether this is the first launch with videos (for restoring state)
    var hasRestoredState by remember { mutableStateOf(false) }

    // Initialize player when URIs change or when a specific video is requested
    LaunchedEffect(filteredVideoUris, pendingStartVideoIndex) {
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

    val exoPlayer = remember { videoPlayerManager.initialize() }

    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
        }
    }

    var playbackError by remember { mutableStateOf<String?>(null) }

    // Controls visibility state — shown on tap, auto-hide after 5 seconds
    // Hidden by default in kid mode — tap to show; also avoids slider polling while hidden
    var controlsVisible by remember { mutableStateOf(false) }
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
    LaunchedEffect(exoPlayer, controlsVisible, isSeeking) {
        if (!controlsVisible) return@LaunchedEffect
        while (controlsVisible) {
            if (!isSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(1L)
                duration = dur
                sliderValue = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // Periodically save playback state every 5 seconds
    LaunchedEffect(exoPlayer, filteredVideoUris) {
        if (filteredVideoUris.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val currentIndex = exoPlayer.currentMediaItemIndex
            if (currentIndex in filteredVideoUris.indices) {
                val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                playbackStateRepository.save(
                    PlaybackStateRepository.PlaybackState(
                        videoUri = filteredVideoUris[currentIndex],
                        positionMs = positionMs
                    )
                )
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(rootView) {
        onDispose {
            ViewCompat.setSystemGestureExclusionRects(rootView, emptyList())
        }
    }

    // isPlaying listener is registered inside the controls block below.

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
                factory = { playerView },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
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
                            // Don't steal taps in the top-right secret-door zone
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
                            exoPlayer.seekTo((sliderValue * duration).toLong())
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
                                    val currentIndex = exoPlayer.currentMediaItemIndex
                                    val newIndex = if (currentIndex == 0) {
                                        filteredVideoUris.lastIndex
                                    } else {
                                        currentIndex - 1
                                    }
                                    exoPlayer.seekToDefaultPosition(newIndex)
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
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
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
                                    val currentIndex = exoPlayer.currentMediaItemIndex
                                    val newIndex = if (currentIndex == filteredVideoUris.lastIndex) 0 else currentIndex + 1
                                    exoPlayer.seekToDefaultPosition(newIndex)
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
        // Version Text — Secret Door (long press 1 second)
        // ==============================
        var isVersionPressed by remember { mutableStateOf(false) }

        // Separate timer: fires 1 second after press, cancels on release
        LaunchedEffect(isVersionPressed) {
            if (isVersionPressed) {
                delay(1000L)
                showPinDialog = true
                isVersionPressed = false
            }
        }

        var secretDoorExclusionRect by remember { mutableStateOf<Rect?>(null) }

        Box(
            modifier = Modifier
                .zIndex(10f)
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(WindowInsets.displayCutout)
                )
                .padding(top = 20.dp, end = 16.dp)
                .size(SECRET_DOOR_TOUCH_SIZE)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    val rect = Rect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt()
                    )
                    if (secretDoorExclusionRect != rect) {
                        secretDoorExclusionRect = rect
                        ViewCompat.setSystemGestureExclusionRects(rootView, listOf(rect))
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        isVersionPressed = true
                        waitForUpOrCancellation()
                        isVersionPressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "v1.0",
                fontSize = 18.sp,
                color = if (isVersionPressed) {
                    Color.White.copy(alpha = 0.7f)
                } else {
                    Color.White.copy(alpha = 0.25f)
                },
                fontWeight = FontWeight.Normal
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
                onSecretDoorActivated()
            }
        )
    }
}

// Extension property to check if player is playing
private val Player.isPlaying: Boolean
    get() = playbackState == Player.STATE_READY && playWhenReady

private const val TAG = "KidPlayerScreen"

/** Touch target for the secret-door version label (top-right). */
private val SECRET_DOOR_TOUCH_SIZE = 72.dp
