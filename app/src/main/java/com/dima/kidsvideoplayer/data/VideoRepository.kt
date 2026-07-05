package com.dima.kidsvideoplayer.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dima.kidsvideoplayer.utils.extractParentFolderPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Repository for watched folder paths and kid-mode folder selection.
 *
 * Videos are discovered dynamically by [VideoLibraryService] from watched folders.
 * Migrates legacy `video_uris` / `selected_videos` keys on first read.
 */
class VideoRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "video_prefs"
    )

    companion object {
        private val WATCHED_FOLDERS_KEY = stringPreferencesKey("watched_folders")
        private val EXPANDED_FOLDERS_KEY = stringPreferencesKey("expanded_folders")
        private val SELECTED_FOLDERS_KEY = stringPreferencesKey("selected_folders")

        private val LEGACY_VIDEO_URIS_KEY = stringPreferencesKey("video_uris")
        private val LEGACY_SELECTED_VIDEOS_KEY = stringPreferencesKey("selected_videos")
        private const val LEGACY_SEPARATOR = "|"
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun serialize(paths: List<String>): String {
        val array = JSONArray()
        paths.forEach { array.put(it) }
        return array.toString()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun deserialize(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            return (0 until array.length()).map { array.getString(it) }
        }
        return raw.split(LEGACY_SEPARATOR).filter { it.isNotBlank() }
    }

    private fun parseCommaSet(raw: String): Set<String> =
        if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()

    private fun migrateLegacyData(prefs: MutablePreferences) {
        val legacyUris = deserialize(prefs[LEGACY_VIDEO_URIS_KEY] ?: "")
        if (legacyUris.isEmpty()) return

        val watchedFolders = legacyUris
            .mapNotNull { extractParentFolderPath(it) }
            .distinct()

        val legacySelected = parseCommaSet(prefs[LEGACY_SELECTED_VIDEOS_KEY] ?: "")
        val selectedFolders = legacySelected
            .mapNotNull { extractParentFolderPath(it) }
            .toSet()

        prefs[WATCHED_FOLDERS_KEY] = serialize(watchedFolders)
        if (selectedFolders.isNotEmpty()) {
            prefs[SELECTED_FOLDERS_KEY] = selectedFolders.joinToString(",")
        }
        prefs.remove(LEGACY_VIDEO_URIS_KEY)
        prefs.remove(LEGACY_SELECTED_VIDEOS_KEY)
    }

    private fun readWatchedFolders(prefs: Preferences): List<String> =
        deserialize(prefs[WATCHED_FOLDERS_KEY] ?: "")

    val watchedFolders: Flow<List<String>> = context.dataStore.data.map { prefs ->
        readWatchedFolders(prefs)
    }

    val expandedFolders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseCommaSet(prefs[EXPANDED_FOLDERS_KEY] ?: "")
    }

    val selectedFolders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseCommaSet(prefs[SELECTED_FOLDERS_KEY] ?: "")
    }

    suspend fun ensureMigrated() {
        context.dataStore.edit { prefs ->
            if (prefs[WATCHED_FOLDERS_KEY].isNullOrBlank() &&
                !prefs[LEGACY_VIDEO_URIS_KEY].isNullOrBlank()
            ) {
                migrateLegacyData(prefs)
            }
        }
    }

    suspend fun addWatchedFolder(path: String) {
        context.dataStore.edit { prefs ->
            val existing = readWatchedFolders(prefs).toMutableList()
            if (path !in existing) {
                existing.add(path)
                prefs[WATCHED_FOLDERS_KEY] = serialize(existing)
            }
        }
    }

    suspend fun addWatchedFolders(paths: List<String>) {
        if (paths.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = readWatchedFolders(prefs).toMutableList()
            val newPaths = paths.filter { it !in existing }
            if (newPaths.isNotEmpty()) {
                existing.addAll(newPaths)
                prefs[WATCHED_FOLDERS_KEY] = serialize(existing)
            }
        }
    }

    suspend fun removeWatchedFolder(path: String) {
        context.dataStore.edit { prefs ->
            val folders = readWatchedFolders(prefs).filter { it != path }
            prefs[WATCHED_FOLDERS_KEY] = serialize(folders)
            val selected = parseCommaSet(prefs[SELECTED_FOLDERS_KEY] ?: "") - path
            prefs[SELECTED_FOLDERS_KEY] = selected.joinToString(",")
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.remove(WATCHED_FOLDERS_KEY)
            prefs.remove(SELECTED_FOLDERS_KEY)
            prefs.remove(EXPANDED_FOLDERS_KEY)
            prefs.remove(LEGACY_VIDEO_URIS_KEY)
            prefs.remove(LEGACY_SELECTED_VIDEOS_KEY)
        }
    }

    suspend fun saveExpandedFolders(folders: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[EXPANDED_FOLDERS_KEY] = folders.joinToString(",")
        }
    }

    suspend fun saveSelectedFolders(selected: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_FOLDERS_KEY] = selected.joinToString(",")
        }
    }

    suspend fun toggleFolderSelection(folderPath: String) {
        context.dataStore.edit { prefs ->
            val current = parseCommaSet(prefs[SELECTED_FOLDERS_KEY] ?: "")
            val updated = if (folderPath in current) current - folderPath else current + folderPath
            prefs[SELECTED_FOLDERS_KEY] = updated.joinToString(",")
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun setLegacyDataForMigration(videoUris: List<String>, selectedVideos: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[LEGACY_VIDEO_URIS_KEY] = serialize(videoUris)
            prefs[LEGACY_SELECTED_VIDEOS_KEY] = selectedVideos.joinToString(",")
            prefs.remove(WATCHED_FOLDERS_KEY)
            prefs.remove(SELECTED_FOLDERS_KEY)
        }
    }
}
