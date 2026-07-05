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
import com.dima.kidsvideoplayer.data.VideoLibraryService
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.screens.kidplayer.PlayerControlsOverlay
import com.dima.kidsvideoplayer.ui.screens.kidplayer.SecretDoorGesture
import kotlinx.coroutines.delay

@Composable
fun KidPlayerScreen(
    videoRepository: VideoRepository,
    videoLibraryService: VideoLibraryService,
    videoPlayerManager: VideoPlayerManager,
    playbackStateRepository: PlaybackStateRepository,
    onSecretDoorActivated: () -> Unit,
    pendingStartVideoIndex: Int = -1,
    onPendingIndexConsumed: () -> Unit = {}
) {
    val secretDoorTouchSizePx = with(LocalDensity.current) { 72.dp.toPx() }
    val selectedFolders by videoRepository.selectedFolders.collectAsStateWithLifecycle(initialValue = emptySet())
    val libraryState by videoLibraryService.libraryState.collectAsStateWithLifecycle()
    val watchedFolders by videoRepository.watchedFolders.collectAsStateWithLifecycle(initialValue = emptyList())

    val playableUris = remember(libraryState.videos, selectedFolders) {
        if (selectedFolders.isEmpty()) emptyList()
        else libraryState.videos
            .filter { it.sourceFolder in selectedFolders }
            .map { it.uriString }
    }

    LaunchedEffect(libraryState.uriMigrations) {
        libraryState.uriMigrations.forEach { (oldUri, newUri) ->
            playbackStateRepository.migrateUri(oldUri, newUri)
        }
    }

    var hasRestoredState by remember { mutableStateOf(false) }
    var initialSetupDone by remember { mutableStateOf(false) }

    LaunchedEffect(playableUris, pendingStartVideoIndex, libraryState.lastScanTime) {
        videoPlayerManager.initialize()

        if (playableUris.isEmpty()) {
            videoPlayerManager.setVideoList(emptyList())
            return@LaunchedEffect
        }

        val currentUri = videoPlayerManager.getCurrentVideoUri()
        if (initialSetupDone && currentUri != null && currentUri in playableUris) {
            val newIndex = playableUris.indexOf(currentUri)
            videoPlayerManager.setVideoList(
                playableUris,
                newIndex,
                videoPlayerManager.currentPosition
            )
            return@LaunchedEffect
        }

        val startIndex: Int
        val startPositionMs: Long

        if (pendingStartVideoIndex >= 0) {
            startIndex = pendingStartVideoIndex.coerceIn(0, playableUris.lastIndex)
            startPositionMs = 0L
            onPendingIndexConsumed()
            hasRestoredState = true
            initialSetupDone = true
        } else if (!hasRestoredState) {
            hasRestoredState = true
            initialSetupDone = true
            val saved = playbackStateRepository.get()
            if (saved != null) {
                val savedIndex = playableUris.indexOf(saved.videoUri)
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
            return@LaunchedEffect
        }

        videoPlayerManager.setVideoList(playableUris, startIndex, startPositionMs)
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(videoPlayerManager.isPlaying) }

    LaunchedEffect(videoPlayerManager) {
        while (true) {
            isPlaying = videoPlayerManager.isPlaying
            delay(300)
        }
    }

    LaunchedEffect(controlsVisible, controlsInteraction, isPlaying) {
        if (controlsVisible && isPlaying) {
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

    LaunchedEffect(videoPlayerManager, playableUris) {
        if (playableUris.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val currentIndex = videoPlayerManager.currentMediaItemIndex
            if (currentIndex in playableUris.indices) {
                playbackStateRepository.save(
                    PlaybackStateRepository.PlaybackState(
                        videoUri = playableUris[currentIndex],
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
        if (playableUris.isNotEmpty()) {
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
                    text = if (watchedFolders.isEmpty()) {
                        "🎬 Нет видео\n\nРодитель может добавить папки\nчерез настройки"
                    } else {
                        "🎬 Нет выбранных папок\n\nПожалуйста, выберите папки\nв настройках родителя"
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

        if (playableUris.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(controlsVisible, controlsInteraction, isPlaying, secretDoorTouchSizePx) {
                        detectTapGestures { offset ->
                            val inSecretZone = offset.x >= size.width - secretDoorTouchSizePx &&
                                offset.y <= secretDoorTouchSizePx
                            if (!inSecretZone) {
                                if (isPlaying) {
                                    controlsVisible = !controlsVisible
                                } else {
                                    controlsVisible = true
                                }
                                controlsInteraction++
                            }
                        }
                    }
            )

            PlayerControlsOverlay(
                visible = controlsVisible,
                filteredVideoCount = playableUris.size,
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
                    if (videoPlayerManager.isPlaying) {
                        videoPlayerManager.pause()
                        controlsVisible = true
                    } else {
                        videoPlayerManager.play()
                    }
                },
                onSeekBackward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekBackward(offsetMs)
                },
                onSeekForward = { offsetMs ->
                    controlsInteraction++
                    videoPlayerManager.seekForward(offsetMs)
                }
            )
        }

        SecretDoorGesture(
            onActivated = onSecretDoorActivated,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
