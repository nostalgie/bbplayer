package com.dima.kidsvideoplayer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the last playback state (video URI + position) across app restarts.
 *
 * Uses [SharedPreferences] for fast synchronous reads and async applies.
 * The state is saved periodically during playback and restored on app launch.
 *
 * @param context Application context (use applicationContext to avoid leaks)
 */
class PlaybackStateRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    /**
     * Saved playback state: the URI of the last played video and the position in milliseconds.
     */
    data class PlaybackState(
        val videoUri: String,
        val positionMs: Long
    )

    /**
     * Save the current playback state.
     * Uses [SharedPreferences.apply] for async disk write (non-blocking).
     */
    fun save(state: PlaybackState) {
        prefs.edit()
            .putString(KEY_VIDEO_URI, state.videoUri)
            .putLong(KEY_POSITION_MS, state.positionMs)
            .apply()
    }

    /**
     * Retrieve the last saved playback state, or null if none exists.
     */
    fun get(): PlaybackState? {
        val uri = prefs.getString(KEY_VIDEO_URI, null) ?: return null
        val position = prefs.getLong(KEY_POSITION_MS, 0L)
        return PlaybackState(uri, position)
    }

    /**
     * Clear the saved playback state.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_VIDEO_URI = "last_video_uri"
        private const val KEY_POSITION_MS = "last_position_ms"
    }
}
