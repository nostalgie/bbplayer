package com.dima.kidsvideoplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VideoCompatibilityCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: VideoCompatibilityChecker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        checker = VideoCompatibilityChecker(context)
    }

    @Test
    fun isPlayable_returnsFalseForNonVideoExtension() = runBlocking {
        val file = File(context.cacheDir, "test.txt")
        file.writeText("not a video")
        assertThat(checker.isPlayable(file)).isFalse()
    }

    @Test
    fun cacheKey_includesPathSizeAndMtime() {
        val file = File("/tmp/video.mp4")
        val key = checker.cacheKey(file)
        assertThat(key).startsWith("/tmp/video.mp4:")
    }
}
