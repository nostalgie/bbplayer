/**
 * Tests for [VideoRepository] — verifies DataStore-backed CRUD operations
 * for video URI persistence (add, batch-add with dedup, remove, clear).
 *
 * Uses Robolectric to provide a real Android Context so that
 * DataStore Preferences works without an emulator.
 */
package com.dima.kidsvideoplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: VideoRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = VideoRepository(context)
        // Clear any leftover data from previous tests
        runBlocking { repository.clearAll() }
    }

    @Test
    fun videoUris_emitsEmptyListInitially() = runBlocking {
        val uris = repository.videoUris.first()
        assertThat(uris).isEmpty()
    }

    @Test
    fun addVideoUri_addsASingleUri() {
        runBlocking {
            repository.addVideoUri("content://video/1")
            val uris = repository.videoUris.first()
            assertThat(uris).containsExactly("content://video/1")
        }
    }

    @Test
    fun addVideoUri_appendsToExistingList() = runBlocking {
        repository.addVideoUri("content://video/1")
        repository.addVideoUri("content://video/2")
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly("content://video/1", "content://video/2").inOrder()
    }

    @Test
    fun addVideoUris_addsMultipleUrisWithDeduplication() = runBlocking {
        repository.addVideoUri("content://video/1")
        repository.addVideoUris(listOf("content://video/1", "content://video/2", "content://video/3"))
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly(
            "content://video/1",
            "content://video/2",
            "content://video/3"
        ).inOrder()
    }

    @Test
    fun addVideoUris_withEmptyList_doesNothing() {
        runBlocking {
            repository.addVideoUri("content://video/1")
            repository.addVideoUris(emptyList())
            val uris = repository.videoUris.first()
            assertThat(uris).containsExactly("content://video/1")
        }
    }

    @Test
    fun addVideoUris_deduplicatesCaseSensitive() = runBlocking {
        repository.addVideoUri("content://video/A")
        repository.addVideoUris(listOf("content://video/a"))
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly("content://video/A", "content://video/a").inOrder()
    }

    @Test
    fun removeVideoUri_removesTheSpecifiedUri() = runBlocking {
        repository.addVideoUris(listOf("content://video/1", "content://video/2", "content://video/3"))
        repository.removeVideoUri("content://video/2")
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly("content://video/1", "content://video/3").inOrder()
    }

    @Test
    fun removeVideoUri_onNonExistentUri_doesNothing() = runBlocking {
        repository.addVideoUris(listOf("content://video/1", "content://video/2"))
        repository.removeVideoUri("content://nonexistent")
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly("content://video/1", "content://video/2").inOrder()
    }

    @Test
    fun removeVideoUri_onEmptyList_doesNothing() = runBlocking {
        repository.removeVideoUri("content://video/1")
        val uris = repository.videoUris.first()
        assertThat(uris).isEmpty()
    }

    @Test
    fun clearAll_removesAllUris() = runBlocking {
        repository.addVideoUris(listOf("content://video/1", "content://video/2"))
        repository.clearAll()
        val uris = repository.videoUris.first()
        assertThat(uris).isEmpty()
    }

    @Test
    fun clearAll_onAlreadyEmptyStore_doesNotThrow() = runBlocking {
        repository.clearAll()
        val uris = repository.videoUris.first()
        assertThat(uris).isEmpty()
    }

    @Test
    fun addAfterClear_worksCorrectly() {
        runBlocking {
            repository.addVideoUris(listOf("content://video/1", "content://video/2"))
            repository.clearAll()
            repository.addVideoUri("content://video/3")
            val uris = repository.videoUris.first()
            assertThat(uris).containsExactly("content://video/3")
        }
    }

    // --- New tests for JSON array format and migration ---

    @Test
    fun addVideoUri_uriWithPipeCharacter_storedCorrectly() {
        runBlocking {
            val uriWithPipe = "content://com.provider/document|segment"
            repository.addVideoUri(uriWithPipe)
            val uris = repository.videoUris.first()
            assertThat(uris).containsExactly(uriWithPipe)
        }
    }

    @Test
    fun addVideoUri_uriWithSpecialChars_storedCorrectly() = runBlocking {
        val uriWithQuotes = "content://com.provider/doc\"name"
        val uriWithBackslash = "content://com.provider/doc\\path"
        val uriWithUnicode = "content://com.provider/видео"
        repository.addVideoUris(listOf(uriWithQuotes, uriWithBackslash, uriWithUnicode))
        val uris = repository.videoUris.first()
        assertThat(uris).containsExactly(uriWithQuotes, uriWithBackslash, uriWithUnicode).inOrder()
    }

    @Test
    fun migration_fromPipeDelimited_readsOldFormat() {
        // Simulate reading legacy pipe-delimited data
        val legacy = "content://video/1|content://video/2|content://video/3"
        val uris = repository.deserialize(legacy)
        assertThat(uris).containsExactly(
            "content://video/1",
            "content://video/2",
            "content://video/3"
        ).inOrder()
    }

    @Test
    fun migration_afterWrite_dataIsJsonFormat() {
        // Verify that deserialize reads legacy pipe-delimited format
        val legacy = "content://video/1|content://video/2"
        val uris = repository.deserialize(legacy)
        assertThat(uris).containsExactly("content://video/1", "content://video/2").inOrder()

        // Verify that serialize produces JSON array format
        val merged = uris + "content://video/3"
        val jsonOutput = repository.serialize(merged)
        assertThat(jsonOutput.trimStart()).startsWith("[")

        // Verify the JSON output round-trips through deserialize correctly
        val roundTripped = repository.deserialize(jsonOutput)
        assertThat(roundTripped).containsExactly(
            "content://video/1",
            "content://video/2",
            "content://video/3"
        ).inOrder()
    }
}
