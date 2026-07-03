package com.dima.kidsvideoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer

/**
 * Manages ExoPlayer lifecycle and playback operations.
 *
 * Usage:
 *   - Call [initialize] to create the player
 *   - Call [play] / [pause] / [seekTo] for playback control
 *   - Call [setVideoList] to set the playlist
 *   - Call [release] when done (e.g., in Activity.onDestroy)
 *
 * URI schemes supported:
 *   - `content://` — SAF URIs with persistable permission (preferred)
 *   - `file://` — direct file paths (requires MANAGE_EXTERNAL_STORAGE)
 *
 * @param context Application context (use applicationContext to avoid leaks)
 * @param onError Optional callback invoked when a playback error occurs
 */
class VideoPlayerManager(
    private val context: Context,
    private val onError: ((PlaybackException) -> Unit)? = null
) {

    var player: ExoPlayer? = null
        private set

    var currentMediaItemIndex: Int = 0
        private set

    /**
     * Initialize ExoPlayer with appropriate settings for a kids' video player.
     */
    fun initialize(): ExoPlayer {
        // Return existing player if already initialized
        player?.let { return it }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Use FFmpeg only when hardware decoding is unavailable — saves native memory.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // Keep buffers small — default ExoPlayer settings can exceed the 128MB app heap
        // on mid-range devices when playing large/local video files.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setTargetBufferBytes(4 * 1024 * 1024) // 4 MB cap
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(/* backBufferDurationMs = */ 0, /* retainBackBufferFromKeyframe = */ false)
            .build()

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setUseLazyPreparation(true)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // Don't preload — save bandwidth
                playWhenReady = true
                // Repeat all items in the playlist
                repeatMode = Player.REPEAT_MODE_ALL
            }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Avoid logging full stack traces — can trigger secondary OOM when heap is full
                Log.e(TAG, "Player error: code=${error.errorCodeName}, msg=${error.message}")
                exoPlayer.stop()
                if (error.cause is MediaCodecRenderer.DecoderInitializationException) {
                    val decoderError = error.cause as MediaCodecRenderer.DecoderInitializationException
                    Log.e(TAG, "Decoder init failed: mimeType=${decoderError.mimeType}")
                }
                onError?.invoke(error)
            }
        })
        player = exoPlayer
        return exoPlayer
    }

    /**
     * Set the list of video URIs as the playlist.
     * @param uris List of content URIs (from SAF with persistable permission)
     * @param startIndex Index to start playback from (default 0)
     * @param startPositionMs Position in milliseconds to seek to within the start item (default 0)
     */
    fun setVideoList(uris: List<String>, startIndex: Int = 0, startPositionMs: Long = 0) {
        val exoPlayer = player ?: return

        // Release decoder/buffer memory from the previous item before loading a new playlist.
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        uris.forEach { uriString ->
            val mediaItem = MediaItem.fromUri(Uri.parse(uriString))
            exoPlayer.addMediaItem(mediaItem)
        }

        val safeIndex = if (uris.isNotEmpty()) {
            startIndex.coerceIn(0, uris.size - 1)
        } else {
            0
        }

        if (uris.isNotEmpty()) {
            exoPlayer.seekTo(safeIndex, startPositionMs)
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }

        currentMediaItemIndex = safeIndex
    }

    /**
     * Navigate to the next video in the playlist.
     */
    fun next() {
        val exoPlayer = player ?: return
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex
        }
    }

    /**
     * Navigate to the previous video in the playlist.
     */
    fun previous() {
        val exoPlayer = player ?: return
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex
        }
    }

    /**
     * Seek forward by the given offset in milliseconds, clamped to the video duration.
     */
    fun seekForward(offsetMs: Long) {
        val exoPlayer = player ?: return
        val newPosition = exoPlayer.currentPosition + offsetMs
        val duration = exoPlayer.duration
        val clamped = if (duration > 0) {
            newPosition.coerceAtMost(duration)
        } else {
            newPosition
        }
        exoPlayer.seekTo(clamped)
    }

    /**
     * Seek backward by the given offset in milliseconds, clamped to 0.
     */
    fun seekBackward(offsetMs: Long) {
        val exoPlayer = player ?: return
        val newPosition = exoPlayer.currentPosition - offsetMs
        exoPlayer.seekTo(newPosition.coerceAtLeast(0))
    }

    /**
     * Pause the current video playback.
     */
    fun pause() {
        player?.pause()
    }

    /**
     * Resume the current video playback.
     */
    fun play() {
        player?.play()
    }

    /**
     * Get total number of items in the playlist.
     */
    fun getMediaItemCount(): Int {
        return player?.mediaItemCount ?: 0
    }

    /**
     * Release the player — MUST be called when Activity is destroyed.
     */
    fun release() {
        player?.release()
        player = null
    }

    /**
     * Set the player instance directly — for testing only.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setPlayerForTesting(exoPlayer: ExoPlayer?) {
        player = exoPlayer
    }

    companion object {
        private const val TAG = "VideoPlayerManager"
    }
}
