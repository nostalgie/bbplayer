package com.dima.kidsvideoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
 */
class VideoPlayerManager(private val context: Context) {

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

        // Enable software decoder fallback for codecs not supported by hardware
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
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
                Log.e(TAG, "Player error: ${error.message}", error)
                if (error.cause is MediaCodecRenderer.DecoderInitializationException) {
                    val decoderError = error.cause as MediaCodecRenderer.DecoderInitializationException
                    Log.e(TAG, "Decoder init failed: mimeType=${decoderError.mimeType}, " +
                        "secureDecoderRequired=${decoderError.secureDecoderRequired}")
                }
            }
        })
        player = exoPlayer
        return exoPlayer
    }

    /**
     * Set the list of video URIs as the playlist.
     * @param uris List of content URIs (from SAF with persistable permission)
     * @param startIndex Index to start playback from (default 0)
     */
    fun setVideoList(uris: List<String>, startIndex: Int = 0) {
        val exoPlayer = player ?: return
        exoPlayer.clearMediaItems()

        uris.forEach { uriString ->
            val mediaItem = MediaItem.fromUri(Uri.parse(uriString))
            exoPlayer.addMediaItem(mediaItem)
        }

        if (uris.isNotEmpty()) {
            exoPlayer.seekToDefaultPosition(startIndex.coerceAtMost(uris.size - 1))
            exoPlayer.prepare()
        }

        currentMediaItemIndex = startIndex
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

    companion object {
        private const val TAG = "VideoPlayerManager"
    }
}
