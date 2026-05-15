package com.dima.kidsvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Repository for storing and retrieving video URIs using DataStore (Preferences).
 *
 * URIs are stored as a JSON array string under a single key.
 * Each URI should have been granted takePersistableUriPermission before saving.
 *
 * Backward compatibility: legacy pipe-delimited format is automatically detected
 * and migrated to JSON array on first write.
 */
class VideoRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "video_prefs"
    )

    companion object {
        private val VIDEO_URIS_KEY = stringPreferencesKey("video_uris")
        private val EXPANDED_FOLDERS_KEY = stringPreferencesKey("expanded_folders")
        private const val LEGACY_SEPARATOR = "|"
    }

    /**
     * Serialize a list of URI strings into a JSON array string.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun serialize(uris: List<String>): String {
        val array = JSONArray()
        uris.forEach { array.put(it) }
        return array.toString()
    }

    /**
     * Deserialize a raw stored string into a list of URI strings.
     * Supports both JSON array format and legacy pipe-delimited format.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun deserialize(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            return (0 until array.length()).map { array.getString(it) }
        }
        // Legacy format: pipe-delimited → migrate
        return raw.split(LEGACY_SEPARATOR).filter { it.isNotBlank() }
    }

    /**
     * Get the list of saved video URIs as a Flow.
     */
    val videoUris: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[VIDEO_URIS_KEY] ?: ""
        deserialize(raw)
    }

    /**
     * Get the set of expanded folder paths as a Flow.
     */
    val expandedFolders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[EXPANDED_FOLDERS_KEY] ?: ""
        if (raw.isBlank()) {
            emptySet()
        } else {
            raw.split(",").toSet()
        }
    }

    /**
     * Add a new video URI to the list.
     */
    suspend fun addVideoUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[VIDEO_URIS_KEY] ?: ""
            val existing = deserialize(current).toMutableList()
            if (uri !in existing) {
                existing.add(uri)
            }
            prefs[VIDEO_URIS_KEY] = serialize(existing)
        }
    }

    /**
     * Add multiple video URIs to the list (batch add with deduplication).
     */
    suspend fun addVideoUris(uris: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[VIDEO_URIS_KEY] ?: ""
            val existing = deserialize(current).toMutableList()
            for (uri in uris) {
                if (uri !in existing) {
                    existing.add(uri)
                }
            }
            prefs[VIDEO_URIS_KEY] = serialize(existing)
        }
    }

    /**
     * Remove a video URI from the list.
     */
    suspend fun removeVideoUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[VIDEO_URIS_KEY] ?: ""
            val uris = deserialize(current).filter { it != uri }
            prefs[VIDEO_URIS_KEY] = serialize(uris)
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

    /**
     * Save the set of expanded folder paths.
     */
    suspend fun saveExpandedFolders(folders: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[EXPANDED_FOLDERS_KEY] = folders.joinToString(",")
        }
    }
}
