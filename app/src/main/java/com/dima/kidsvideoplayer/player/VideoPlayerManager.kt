package com.dima.kidsvideoplayer.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.decoder.ffmpeg.FfmpegDecoderException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
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

    /** Retry counter for "stuck buffering" auto-recovery. */
    private var stuckBufferingRetries = 0

    /** True when audio has been disabled for the current media item. */
    private var audioDisabledForCurrentMedia = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Runnable that disables the audio track if the player has been buffering
     * for too long.  Handles cases where the audio MIME type is present but
     * no decoder can actually process the data (e.g. multi-channel AC3).
     */
    private val bufferingTimeoutRunnable = Runnable {
        val exo = player ?: return@Runnable
        if (exo.playbackState == Player.STATE_BUFFERING && !audioDisabledForCurrentMedia) {
            Log.w(TAG, "Buffering timeout (${BUFFERING_TIMEOUT_MS}ms) reached — " +
                "disabling audio track as fallback")
            disableAudioAndRetry(exo)
        }
    }

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

        // PREFER extension (FFmpeg) renderers over platform renderers.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,  // minBufferMs
                50_000,  // maxBufferMs
                1_000,   // bufferForPlaybackMs
                500      // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(100 * 1024 * 1024) // 100 MB
            .build()

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
            }

        exoPlayer.addListener(object : Player.Listener {

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Re-enable audio when the player moves to a DIFFERENT media
                // item (next/previous/auto).  Do NOT re-enable on REPEAT —
                // the same item will hit the same decoder error again.
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                    && audioDisabledForCurrentMedia
                ) {
                    Log.d(TAG, "Media item changed — re-enabling audio")
                    audioDisabledForCurrentMedia = false
                    tryEnableAudio()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                mainHandler.removeCallbacks(bufferingTimeoutRunnable)
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    stuckBufferingRetries = 0
                    audioDisabledForCurrentMedia = false
                } else if (playbackState == Player.STATE_BUFFERING) {
                    mainHandler.postDelayed(bufferingTimeoutRunnable, BUFFERING_TIMEOUT_MS)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Detect audio tracks with no MIME type early — the AVI
                // extractor sometimes fails to map the audio codec.
                if (audioDisabledForCurrentMedia) return
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.sampleMimeType == null) {
                                Log.w(TAG, "Audio track has no MIME type — " +
                                    "disabling audio (codec=${format.codecs})")
                                val exo = player ?: return
                                disableAudioAndRetry(exo)
                                return
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}", error)
                mainHandler.removeCallbacks(bufferingTimeoutRunnable)

                // --- FFmpeg audio decoder fatal error (e.g. multi-channel AC3) ---
                if (error.cause is FfmpegDecoderException && !audioDisabledForCurrentMedia) {
                    Log.w(TAG, "FFmpeg audio decoder failed — disabling audio and retrying")
                    val exo = player ?: return
                    disableAudioAndRetry(exo)
                    return
                }

                // --- MediaCodec decoder init failure ---
                if (error.cause is MediaCodecRenderer.DecoderInitializationException) {
                    val decoderError = error.cause as MediaCodecRenderer.DecoderInitializationException
                    Log.e(TAG, "Decoder init failed: mimeType=${decoderError.mimeType}, " +
                        "secureDecoderRequired=${decoderError.secureDecoderRequired}")
                }

                // --- "Stuck buffering" recovery ---
                val isStuckBuffering = error.cause is IllegalStateException &&
                    (error.cause as IllegalStateException).message
                        ?.contains("stuck buffering") == true
                if (isStuckBuffering) {
                    val exo = player ?: return
                    if (stuckBufferingRetries < MAX_STUCK_BUFFERING_RETRIES) {
                        stuckBufferingRetries++
                        if (stuckBufferingRetries >= AUDIO_DISABLE_THRESHOLD
                            && !audioDisabledForCurrentMedia
                        ) {
                            Log.w(TAG, "Disabling audio track — possible unsupported " +
                                "audio codec in this container")
                            disableAudioAndRetry(exo)
                        }
                        Log.w(TAG, "Recovering from stuck buffering " +
                            "(attempt $stuckBufferingRetries/$MAX_STUCK_BUFFERING_RETRIES)")
                        val index = exo.currentMediaItemIndex
                        val position = exo.currentPosition
                        exo.seekTo(index, position)
                        exo.prepare()
                        return
                    }
                    Log.e(TAG, "Max stuck-buffering retries ($MAX_STUCK_BUFFERING_RETRIES) reached")
                    stuckBufferingRetries = 0
                }
                onError?.invoke(error)
            }
        })
        player = exoPlayer
        return exoPlayer
    }

    /**
     * Disable the audio track via track-selection parameters and re-prepare.
     */
    private fun disableAudioAndRetry(exo: ExoPlayer) {
        audioDisabledForCurrentMedia = true
        try {
            exo.trackSelectionParameters = exo.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable audio track", e)
        }
        exo.prepare()
    }

    /**
     * Re-enable the audio track (called when switching to a new media item).
     */
    private fun tryEnableAudio() {
        val exo = player ?: return
        try {
            exo.trackSelectionParameters = exo.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-enable audio track selection", e)
        }
    }

    /**
     * Set the list of video URIs as the playlist.
     * @param uris List of content URIs (from SAF with persistable permission)
     * @param startIndex Index to start playback from (default 0)
     * @param startPositionMs Position in milliseconds to seek to within the start item (default 0)
     */
    fun setVideoList(uris: List<String>, startIndex: Int = 0, startPositionMs: Long = 0) {
        val exoPlayer = player ?: return

        // Reset per-media state for the new playlist.
        audioDisabledForCurrentMedia = false
        stuckBufferingRetries = 0
        mainHandler.removeCallbacks(bufferingTimeoutRunnable)

        // Re-enable audio in case it was disabled for a previous file.
        tryEnableAudio()

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
        mainHandler.removeCallbacks(bufferingTimeoutRunnable)
        stuckBufferingRetries = 0
        audioDisabledForCurrentMedia = false
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
        private const val MAX_STUCK_BUFFERING_RETRIES = 3
        private const val AUDIO_DISABLE_THRESHOLD = 2
        private const val BUFFERING_TIMEOUT_MS = 10_000L
    }
}
