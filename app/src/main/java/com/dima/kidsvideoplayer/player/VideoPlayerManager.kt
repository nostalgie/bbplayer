package com.dima.kidsvideoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Video playback via libVLC (same engine as VLC desktop/Android).
 *
 * Supports AVI/XVID/AC3 and other formats that ExoPlayer's demuxer + FFmpeg audio extension
 * cannot handle reliably.
 */
class VideoPlayerManager(
    private val context: Context,
    private val onError: ((String) -> Unit)? = null
) {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var attachedLayout: VLCVideoLayout? = null

    private var playlist: List<String> = emptyList()
    private var pendingStartPositionMs: Long = 0L
    private var hasPendingPlay: Boolean = false

    var currentMediaItemIndex: Int = 0
        private set

    var playbackListener: PlaybackListener? = null

    data class PlaybackListener(
        val onError: (String) -> Unit = {},
        val onPlayingChanged: (Boolean) -> Unit = {},
        val onReady: () -> Unit = {}
    )

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    val currentPosition: Long
        get() = mediaPlayer?.time?.coerceAtLeast(0L) ?: 0L

    val duration: Long
        get() = mediaPlayer?.length?.coerceAtLeast(0L) ?: 0L

    /** Initialize libVLC and the media player (idempotent). */
    fun initialize() {
        if (mediaPlayer != null) return

        val options = arrayListOf(
            "--aout=audiotrack",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter=0",
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0"
        )
        libVlc = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVlc).apply {
            setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        playbackListener?.onPlayingChanged?.invoke(true)
                        playbackListener?.onReady?.invoke()
                    }
                    MediaPlayer.Event.Paused -> playbackListener?.onPlayingChanged?.invoke(false)
                    MediaPlayer.Event.EndReached -> onVideoEnded()
                    MediaPlayer.Event.EncounteredError -> {
                        val msg = "VLC playback error"
                        Log.e(TAG, msg)
                        playbackListener?.onError?.invoke(msg)
                        onError?.invoke(msg)
                    }
                }
            }
        }
        videoLayout?.let { attachVideoLayout(it) }
        if (hasPendingPlay && playlist.isNotEmpty()) {
            playCurrent(pendingStartPositionMs)
        }
    }

    fun attachVideoLayout(layout: VLCVideoLayout) {
        videoLayout = layout
        if (attachedLayout === layout) return
        val player = mediaPlayer ?: return
        layout.post {
            if (videoLayout !== layout) return@post
            try {
                player.detachViews()
            } catch (_: Exception) {
                // Not attached yet
            }
            player.attachViews(layout, null, false, false)
            attachedLayout = layout
            Log.d(TAG, "Video surface attached (${layout.width}x${layout.height})")
            if (hasPendingPlay && playlist.isNotEmpty()) {
                playCurrent(pendingStartPositionMs)
            }
        }
    }

    fun detachVideoLayout() {
        attachedLayout = null
        mediaPlayer?.detachViews()
        videoLayout = null
    }

    fun setVideoList(uris: List<String>, startIndex: Int = 0, startPositionMs: Long = 0) {
        playlist = uris
        if (uris.isEmpty()) {
            mediaPlayer?.stop()
            currentMediaItemIndex = 0
            hasPendingPlay = false
            return
        }
        currentMediaItemIndex = startIndex.coerceIn(0, uris.lastIndex)
        if (mediaPlayer == null) {
            pendingStartPositionMs = startPositionMs
            hasPendingPlay = true
            return
        }
        playCurrent(startPositionMs)
    }

    fun next() {
        if (playlist.isEmpty()) return
        currentMediaItemIndex = if (currentMediaItemIndex >= playlist.lastIndex) 0
        else currentMediaItemIndex + 1
        playCurrent(0)
    }

    fun previous() {
        if (playlist.isEmpty()) return
        currentMediaItemIndex = if (currentMediaItemIndex <= 0) playlist.lastIndex
        else currentMediaItemIndex - 1
        playCurrent(0)
    }

    fun seekToIndex(index: Int, positionMs: Long = 0) {
        if (index !in playlist.indices) return
        currentMediaItemIndex = index
        playCurrent(positionMs)
    }

    fun seekForward(offsetMs: Long) {
        val player = mediaPlayer ?: return
        val newPosition = player.time + offsetMs
        val max = if (player.length > 0) player.length else newPosition
        player.time = newPosition.coerceIn(0L, max)
    }

    fun seekBackward(offsetMs: Long) {
        val player = mediaPlayer ?: return
        player.time = (player.time - offsetMs).coerceAtLeast(0L)
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs.coerceAtLeast(0L)
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun play() {
        mediaPlayer?.play()
    }

    fun getMediaItemCount(): Int = playlist.size

    fun getCurrentVideoUri(): String? = playlist.getOrNull(currentMediaItemIndex)

    fun reinitialize() {
        release()
        initialize()
    }

    fun release() {
        detachVideoLayout()
        attachedLayout = null
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc?.release()
        libVlc = null
        playlist = emptyList()
        hasPendingPlay = false
        pendingStartPositionMs = 0L
    }

    private fun onVideoEnded() {
        if (playlist.isEmpty()) return
        currentMediaItemIndex = if (currentMediaItemIndex >= playlist.lastIndex) 0
        else currentMediaItemIndex + 1
        playCurrent(0)
    }

    private fun playCurrent(positionMs: Long) {
        val vlc = libVlc ?: return
        val player = mediaPlayer ?: return
        val uriString = playlist.getOrNull(currentMediaItemIndex) ?: return

        if (videoLayout == null) {
            pendingStartPositionMs = positionMs
            hasPendingPlay = true
            Log.d(TAG, "Deferring play until video surface is attached: $uriString")
            return
        }
        hasPendingPlay = false

        player.stop()

        val uri = Uri.parse(uriString)
        val media = when (uri.scheme) {
            "content" -> Media(vlc, uri)
            else -> Media(vlc, toLocalPath(uriString))
        }
        player.media = media
        media.release()

        if (positionMs > 0) {
            player.time = positionMs
        }
        player.play()
        Log.d(TAG, "Playing index=$currentMediaItemIndex uri=$uriString pos=$positionMs")
    }

    /** libVLC Media(path) expects a raw filesystem path, not a file:// MRL. */
    private fun toLocalPath(uriString: String): String {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> Uri.decode(uri.path ?: uriString.removePrefix("file:"))
            else -> uriString
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setPlaylistForTesting(uris: List<String>) {
        playlist = uris
    }

    companion object {
        private const val TAG = "VideoPlayerManager"
    }
}
