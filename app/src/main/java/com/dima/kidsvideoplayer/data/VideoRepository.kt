package com.dima.kidsvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for storing and retrieving video URIs using DataStore (Preferences).
 *
 * URIs are stored as a comma-separated string under a single key.
 * Each URI should have been granted takePersistableUriPermission before saving.
 */
class VideoRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "video_prefs"
    )

    companion object {
        private val VIDEO_URIS_KEY = stringPreferencesKey("video_uris")
        private const val SEPARATOR = "|"
    }

    /**
     * Get the list of saved video URIs as a Flow.
     */
    val videoUris: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[VIDEO_URIS_KEY] ?: ""
        if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(SEPARATOR).filter { it.isNotBlank() }
        }
    }

    /**
     * Add a new video URI to the list.
     */
    suspend fun addVideoUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[VIDEO_URIS_KEY] ?: ""
            val newList = if (current.isBlank()) {
                uri
            } else {
                "$current$SEPARATOR$uri"
            }
            prefs[VIDEO_URIS_KEY] = newList
        }
    }

    /**
     * Remove a video URI from the list.
     */
    suspend fun removeVideoUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[VIDEO_URIS_KEY] ?: ""
            val uris = current.split(SEPARATOR).filter { it.isNotBlank() && it != uri }
            prefs[VIDEO_URIS_KEY] = uris.joinToString(SEPARATOR)
        }
    }

    /**
     * Clear all video URIs.
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.remove(VIDEO_URIS_KEY)
        }
    }
}
