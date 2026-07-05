package com.dima.kidsvideoplayer.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.dima.kidsvideoplayer.ui.screens.kidplayer.PlayerControlsOverlay
import com.dima.kidsvideoplayer.ui.screens.kidplayer.SecretDoorGesture
import kotlinx.coroutines.delay

@Composable
fun KidPlayerScreen(
    videoRepository: VideoRepository,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    onSecretDoorActivated: () -> Unit,
    pendingStartVideoIndex: Int = -1,
    onPendingIndexConsumed: () -> Unit = {}
) {
    val secretDoorTouchSizePx = with(LocalDensity.current) { 72.dp.toPx() }
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedVideos by videoRepository.selectedVideos.collectAsStateWithLifecycle(initialValue = emptySet())

    val filteredVideoUris = remember(videoUris, selectedVideos) {
        if (selectedVideos.isEmpty()) emptyList()
        else videoUris.filter { selectedVideos.contains(it) }
    }

    var hasRestoredState by remember { mutableStateOf(false) }

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
            onPendingIndexConsumed()
        } else if (!hasRestoredState) {
            hasRestoredState = true
            val saved = playbackStateRepository.get()
            if (saved != null) {
                val savedIndex = filteredVideoUris.indexOf(saved.videoUri)
                if (savedIndex >= 0) {
                    startIndex = savedIndex
                    startPositionMs = saved.positionMs
                } else {
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

        videoPlayerManager.setVideoList(filteredVideoUris, startIndex, startPositionMs)
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableStateOf(0) }

    LaunchedEffect(controlsVisible, controlsInteraction) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    var sliderValue by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1L) }
    var isSeeking by remember { mutableStateOf(false) }

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

    LaunchedEffect(videoPlayerManager, filteredVideoUris) {
        if (filteredVideoUris.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val currentIndex = videoPlayerManager.currentMediaItemIndex
            if (currentIndex in filteredVideoUris.indices) {
                playbackStateRepository.save(
                    PlaybackStateRepository.PlaybackState(
                        videoUri = filteredVideoUris[currentIndex],
                        positionMs = videoPlayerManager.currentPosition
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
            onReady = { playbackError = null }
        )
        onDispose { videoPlayerManager.playbackListener = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (filteredVideoUris.isNotEmpty()) {
            AndroidView(
                factory = { ctx -> VLCVideoLayout(ctx) },
                update = { layout ->
                    videoPlayerManager.attachVideoLayout(layout)
                    if (layout.width > 0 && layout.height > 0) {
                        videoPlayerManager.updateVideoSurfaceSize(layout.width, layout.height)
                    }
                },
                onRelease = { videoPlayerManager.detachVideoLayout() },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        videoPlayerManager.updateVideoSurfaceSize(size.width, size.height)
                    }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (videoUris.isEmpty()) {
                        "🎬 Нет видео\n\nРодитель может добавить видео\nчерез настройки"
                    } else {
                        "🎬 Нет выбранных видео\n\nПожалуйста, выберите видео\nв настройках родителя"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        playbackError?.let { error ->
            Text(
                text = "⚠️ Не удалось воспроизвести видео\n($error)",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

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

            var isPlaying by remember { mutableStateOf(videoPlayerManager.isPlaying) }
            LaunchedEffect(videoPlayerManager, controlsVisible) {
                while (controlsVisible) {
                    isPlaying = videoPlayerManager.isPlaying
                    delay(300)
                }
            }

            PlayerControlsOverlay(
                visible = controlsVisible,
                filteredVideoCount = filteredVideoUris.size,
                isPlaying = isPlaying,
                sliderValue = sliderValue,
                onSliderChange = { newValue ->
                    isSeeking = true
                    sliderValue = newValue
                    controlsInteraction++
                },
                onSliderChangeFinished = {
                    videoPlayerManager.seekTo((sliderValue * duration).toLong())
                    isSeeking = false
                    controlsInteraction++
                },
                onPrevious = {
                    controlsInteraction++
                    videoPlayerManager.previous()
                },
                onNext = {
                    controlsInteraction++
                    videoPlayerManager.next()
                },
                onPlayPause = {
                    controlsInteraction++
                    if (videoPlayerManager.isPlaying) videoPlayerManager.pause()
                    else videoPlayerManager.play()
                },
                onSeekBackward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekBackward(offsetMs)
                },
                onSeekForward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekForward(offsetMs)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        SecretDoorGesture(
            onActivated = onSecretDoorActivated,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

private const val TAG = "KidPlayerScreen"
