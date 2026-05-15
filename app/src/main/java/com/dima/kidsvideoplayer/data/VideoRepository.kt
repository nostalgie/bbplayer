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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import com.dima.kidsvideoplayer.utils.PerformanceMonitor

/**
 * Video metadata for caching purposes
 */
data class VideoMetadata(
    val uri: String,
    val fileName: String,
    val folderPath: String,
    val fileSize: Long,
    val lastModified: Long,
    val isSupported: Boolean,
    val accessCount: AtomicLong = AtomicLong(0)
)

/**
 * Simple LRU cache implementation
 */
class LruCache<K, V>(private val maxSize: Int) {
    val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)
    
    fun get(key: K): V? {
        synchronized(cache) {
            val value = cache[key]
            if (value != null) {
                // Move to end (most recently used)
                cache.remove(key)
                cache[key] = value
            }
            return value
        }
    }
    
    fun put(key: K, value: V) {
        synchronized(cache) {
            if (cache.size >= maxSize) {
                // Remove least recently used item
                cache.entries.iterator().next().also { cache.remove(it.key) }
            }
            cache[key] = value
        }
    }
    
    fun remove(key: K): V? {
        synchronized(cache) {
            return cache.remove(key)
        }
    }
    
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }
    
    fun size(): Int {
        synchronized(cache) {
            return cache.size
        }
    }
}

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
        private val SELECTED_VIDEOS_KEY = stringPreferencesKey("selected_videos")
        private const val LEGACY_SEPARATOR = "|"
    }

    // Cache for serialized data to avoid repeated JSON operations
    private val jsonCache = mutableMapOf<String, String>()
    private val jsonCacheMutex = Mutex()
    
    // LRU cache for frequently accessed video metadata
    private val videoMetadataCache = LruCache<String, VideoMetadata>(100)
    
    // Batch operation buffer
    private val batchBuffer = mutableListOf<() -> Unit>()
    private val batchMutex = Mutex()
    private var isBatchOperation = false
    
    // Cache invalidation flags
    private var cacheVersion = 0L
    private val cacheInvalidationMutex = Mutex()

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
     * Get the set of selected video URIs as a Flow.
     */
    val selectedVideos: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[SELECTED_VIDEOS_KEY] ?: ""
        if (raw.isBlank()) {
            emptySet()
        } else {
            raw.split(",").toSet()
        }
    }

    /**
     * Add a new video URI to the list with caching.
     */
    suspend fun addVideoUri(uri: String) {
        PerformanceMonitor.startTimer("addVideoUri")
        PerformanceMonitor.incrementCounter("videoUriAdditions")
        
        jsonCacheMutex.withLock {
            val current = jsonCache[VIDEO_URIS_KEY.name] ?: ""
            val existing = deserialize(current).toMutableList()
            if (uri !in existing) {
                existing.add(uri)
                val serialized = serialize(existing)
                jsonCache[VIDEO_URIS_KEY.name] = serialized
                
                context.dataStore.edit { prefs ->
                    prefs[VIDEO_URIS_KEY] = serialized
                }
                PerformanceMonitor.incrementCounter("actualUriAdditions")
            }
        }
        
        PerformanceMonitor.stopTimer("addVideoUri")
    }

    /**
     * Add multiple video URIs to the list (batch add with deduplication) with caching.
     */
    suspend fun addVideoUris(uris: List<String>) {
        jsonCacheMutex.withLock {
            val current = jsonCache[VIDEO_URIS_KEY.name] ?: ""
            val existing = deserialize(current).toMutableList()
            val newUris = uris.filter { it !in existing }
            
            if (newUris.isNotEmpty()) {
                existing.addAll(newUris)
                val serialized = serialize(existing)
                jsonCache[VIDEO_URIS_KEY.name] = serialized
                
                context.dataStore.edit { prefs ->
                    prefs[VIDEO_URIS_KEY] = serialized
                }
            }
        }
    }

    /**
     * Remove a video URI from the list with caching.
     */
    suspend fun removeVideoUri(uri: String) {
        jsonCacheMutex.withLock {
            val current = jsonCache[VIDEO_URIS_KEY.name] ?: ""
            val uris = deserialize(current).filter { it != uri }
            val serialized = serialize(uris)
            jsonCache[VIDEO_URIS_KEY.name] = serialized
            
            context.dataStore.edit { prefs ->
                prefs[VIDEO_URIS_KEY] = serialized
            }
        }
    }

    /**
     * Clear all video URIs with caching.
     */
    suspend fun clearAll() {
        jsonCacheMutex.withLock {
            jsonCache.remove(VIDEO_URIS_KEY.name)
            context.dataStore.edit { prefs ->
                prefs.remove(VIDEO_URIS_KEY)
            }
        }
    }
    
    /**
     * Batch operation: Add multiple URIs in a single transaction.
     */
    suspend fun batchAddUris(uris: List<String>) {
        PerformanceMonitor.startTimer("batchAddUris")
        PerformanceMonitor.incrementCounter("batchOperations")
        PerformanceMonitor.incrementCounter("batchUriAdditions", uris.size.toLong())
        
        // Direct execution without batching for now
        withContext(Dispatchers.IO) {
            addVideoUris(uris)
        }
        
        PerformanceMonitor.stopTimer("batchAddUris")
    }
    
    /**
     * Batch operation: Remove multiple URIs in a single transaction.
     */
    suspend fun batchRemoveUris(uris: List<String>) {
        batchMutex.withLock {
            if (isBatchOperation) {
                // Already in batch mode, just add to buffer
                batchBuffer.add {
                    val current = jsonCache[VIDEO_URIS_KEY.name] ?: ""
                    val existing = deserialize(current).toMutableList()
                    uris.forEach { uri ->
                        existing.remove(uri)
                    }
                    val serialized = serialize(existing)
                    jsonCache[VIDEO_URIS_KEY.name] = serialized
                    
                    
                    /**
                     * Get video metadata from cache or create new entry
                     */
                    suspend fun getVideoMetadata(uri: String, fileName: String, folderPath: String,
                                                 fileSize: Long, lastModified: Long, isSupported: Boolean): VideoMetadata {
                        return videoMetadataCache.get(uri) ?: VideoMetadata(
                            uri = uri,
                            fileName = fileName,
                            folderPath = folderPath,
                            fileSize = fileSize,
                            lastModified = lastModified,
                            isSupported = isSupported
                        ).also { metadata ->
                            videoMetadataCache.put(uri, metadata)
                            metadata.accessCount.incrementAndGet()
                        }
                    }
                    
                    /**
                     * Update video metadata in cache
                     */
                    suspend fun updateVideoMetadata(uri: String, update: (VideoMetadata) -> VideoMetadata) {
                        videoMetadataCache.get(uri)?.let { existing ->
                            val updated = update(existing)
                            videoMetadataCache.put(uri, updated)
                            updated.accessCount.incrementAndGet()
                        }
                    }
                    
                    /**
                     * Remove video metadata from cache
                     */
                    suspend fun removeVideoMetadata(uri: String) {
                        videoMetadataCache.remove(uri)
                    }
                    
                    /**
                     * Invalidate cache when folder structure changes
                     */
                    suspend fun invalidateFolderCache(folderPath: String) {
                        cacheInvalidationMutex.withLock {
                            cacheVersion++
                            // Remove all metadata for videos in the specified folder
                            val keysToRemove = mutableListOf<String>()
                            videoMetadataCache.cache.keys.forEach { key ->
                                val metadata = videoMetadataCache.get(key)
                                if (metadata?.folderPath == folderPath) {
                                    keysToRemove.add(key)
                                }
                            }
                            keysToRemove.forEach { videoMetadataCache.remove(it) }
                        }
                    }
                    
                    /**
                     * Get cache statistics
                     */
                    fun getCacheStats(): Map<String, Any> {
                        return mapOf(
                            "jsonCacheSize" to jsonCache.size,
                            "metadataCacheSize" to videoMetadataCache.size(),
                            "cacheVersion" to cacheVersion
                        )
                    }
                    
                    /**
                     * Clear all caches
                     */
                    suspend fun clearAllCaches() {
                        jsonCacheMutex.withLock {
                            jsonCache.clear()
                        }
                        videoMetadataCache.clear()
                        cacheInvalidationMutex.withLock {
                            cacheVersion++
                        }
                    }
                }
            } else {
                // Start batch operation
                isBatchOperation = true
                try {
                    val current = jsonCache[VIDEO_URIS_KEY.name] ?: ""
                    val existing = deserialize(current).toMutableList()
                    uris.forEach { uri ->
                        existing.remove(uri)
                    }
                    val serialized = serialize(existing)
                    jsonCache[VIDEO_URIS_KEY.name] = serialized
                    
                    context.dataStore.edit { prefs ->
                        prefs[VIDEO_URIS_KEY] = serialized
                    }
                } finally {
                    isBatchOperation = false
                    // Execute any pending batch operations
                    while (batchBuffer.isNotEmpty()) {
                        val operation = batchBuffer.removeAt(0)
                        operation()
                    }
                }
            }
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

    /**
     * Save the set of selected video URIs.
     */
    suspend fun saveSelectedVideos(selected: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_VIDEOS_KEY] = selected.joinToString(",")
        }
    }

    /**
     * Toggle video selection status.
     */
    suspend fun toggleVideoSelection(uri: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[SELECTED_VIDEOS_KEY] ?: ""
            val current = if (raw.isBlank()) emptySet() else raw.split(",").toSet()
            val updated = if (uri in current) current - uri else current + uri
            prefs[SELECTED_VIDEOS_KEY] = updated.joinToString(",")
        }
    }

    /**
     * Select all videos.
     */
    suspend fun selectAllVideos() {
        context.dataStore.edit { prefs ->
            val raw = prefs[VIDEO_URIS_KEY] ?: ""
            val allVideos = deserialize(raw)
            prefs[SELECTED_VIDEOS_KEY] = allVideos.joinToString(",")
        }
    }

    /**
     * Deselect all videos.
     */
    suspend fun deselectAllVideos() {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_VIDEOS_KEY] = ""
        }
    }
}
