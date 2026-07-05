/**
 * Tests for [VideoRepository] — watched folders, selection, and legacy migration.
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
        runBlocking { repository.clearAll() }
    }

    @Test
    fun watchedFolders_emitsEmptyListInitially() {
        runBlocking {
            assertThat(repository.watchedFolders.first()).isEmpty()
        }
    }

    @Test
    fun addWatchedFolder_addsASingleFolder() {
        runBlocking {
            repository.addWatchedFolder("/storage/emulated/0/Movies")
            assertThat(repository.watchedFolders.first())
                .containsExactly("/storage/emulated/0/Movies")
        }
    }

    @Test
    fun addWatchedFolders_addsMultipleWithDeduplication() {
        runBlocking {
            repository.addWatchedFolder("/storage/emulated/0/Movies")
            repository.addWatchedFolders(
                listOf("/storage/emulated/0/Movies", "/storage/emulated/0/Anime")
            )
            assertThat(repository.watchedFolders.first()).containsExactly(
                "/storage/emulated/0/Movies",
                "/storage/emulated/0/Anime"
            ).inOrder()
        }
    }

    @Test
    fun removeWatchedFolder_removesFolderAndSelection() {
        runBlocking {
            val folder = "/storage/emulated/0/Movies"
            repository.addWatchedFolder(folder)
            repository.toggleFolderSelection(folder)
            repository.removeWatchedFolder(folder)
            assertThat(repository.watchedFolders.first()).isEmpty()
            assertThat(repository.selectedFolders.first()).isEmpty()
        }
    }

    @Test
    fun clearAll_removesFoldersAndSelection() {
        runBlocking {
            repository.addWatchedFolder("/storage/emulated/0/Movies")
            repository.toggleFolderSelection("/storage/emulated/0/Movies")
            repository.clearAll()
            assertThat(repository.watchedFolders.first()).isEmpty()
            assertThat(repository.selectedFolders.first()).isEmpty()
        }
    }

    @Test
    fun selectedFolders_emitsEmptyInitially() {
        runBlocking {
            assertThat(repository.selectedFolders.first()).isEmpty()
        }
    }

    @Test
    fun toggleFolderSelection_addsAndRemoves() {
        runBlocking {
            val folder = "/storage/emulated/0/Movies"
            repository.toggleFolderSelection(folder)
            assertThat(repository.selectedFolders.first()).containsExactly(folder)
            repository.toggleFolderSelection(folder)
            assertThat(repository.selectedFolders.first()).isEmpty()
        }
    }

    @Test
    fun saveSelectedFolders_persistsSelection() {
        runBlocking {
            val folders = setOf("/storage/emulated/0/Movies", "/storage/emulated/0/Anime")
            repository.saveSelectedFolders(folders)
            assertThat(repository.selectedFolders.first()).containsExactlyElementsIn(folders)
        }
    }

    @Test
    fun expandedFolders_emitsEmptyInitially() {
        runBlocking {
            assertThat(repository.expandedFolders.first()).isEmpty()
        }
    }

    @Test
    fun saveExpandedFolders_persistsFolders() {
        runBlocking {
            repository.saveExpandedFolders(setOf("SD1/Movies", "SD1/Anime"))
            assertThat(repository.expandedFolders.first())
                .containsExactly("SD1/Movies", "SD1/Anime")
        }
    }

    @Test
    fun migration_fromLegacyVideoUris_createsWatchedFolders() {
        runBlocking {
            repository.setLegacyDataForMigration(
                videoUris = listOf(
                    "file:///storage/emulated/0/Movies/a.mp4",
                    "file:///storage/emulated/0/Movies/b.mp4",
                    "file:///storage/emulated/0/Anime/c.mp4"
                ),
                selectedVideos = setOf(
                    "file:///storage/emulated/0/Movies/a.mp4",
                    "file:///storage/emulated/0/Anime/c.mp4"
                )
            )

            repository.ensureMigrated()

            assertThat(repository.watchedFolders.first()).containsExactly(
                "/storage/emulated/0/Movies",
                "/storage/emulated/0/Anime"
            )
            assertThat(repository.selectedFolders.first()).containsExactly(
                "/storage/emulated/0/Movies",
                "/storage/emulated/0/Anime"
            )
        }
    }

    @Test
    fun migration_legacyPipeDelimited_readsOldFormat() {
        val legacy = "content://video/1|content://video/2|content://video/3"
        val uris = repository.deserialize(legacy)
        assertThat(uris).containsExactly(
            "content://video/1",
            "content://video/2",
            "content://video/3"
        ).inOrder()
    }

    @Test
    fun serialize_deserialize_roundTrip() {
        val paths = listOf("/storage/a", "/storage/b with space")
        val json = repository.serialize(paths)
        assertThat(repository.deserialize(json)).containsExactlyElementsIn(paths)
    }
}
