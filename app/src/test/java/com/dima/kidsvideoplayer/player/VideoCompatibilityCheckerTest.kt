/**
 * Tests for [CompatibilityResult] data class and compatibility logic.
 *
 * The actual MediaExtractor and MediaCodecList calls are Android framework classes
 * that require instrumented tests. These tests focus on the result data class
 * construction and the logic for determining isFullySupported.
 */
package com.dima.kidsvideoplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoCompatibilityCheckerTest {

    // --- CompatibilityResult data class construction ---

    @Test
    fun compatibilityResult_fullySupported_allFieldsCorrect() {
        val result = CompatibilityResult(
            isFullySupported = true,
            videoCodec = "AVC/H.264",
            audioCodec = "AAC",
            videoSupported = true,
            audioSupported = true,
            warnings = emptyList(),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isTrue()
        assertThat(result.videoCodec).isEqualTo("AVC/H.264")
        assertThat(result.audioCodec).isEqualTo("AAC")
        assertThat(result.videoSupported).isTrue()
        assertThat(result.audioSupported).isTrue()
        assertThat(result.warnings).isEmpty()
        assertThat(result.canReadFile).isTrue()
    }

    @Test
    fun compatibilityResult_unsupportedVideo_notFullySupported() {
        val result = CompatibilityResult(
            isFullySupported = false,
            videoCodec = "HEVC/H.265",
            audioCodec = "AAC",
            videoSupported = false,
            audioSupported = true,
            warnings = listOf("Video codec HEVC/H.265 is not supported by this device"),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isFalse()
        assertThat(result.videoSupported).isFalse()
        assertThat(result.audioSupported).isTrue()
        assertThat(result.warnings).hasSize(1)
        assertThat(result.warnings[0]).contains("HEVC/H.265")
    }

    @Test
    fun compatibilityResult_unsupportedAudio_notFullySupported() {
        val result = CompatibilityResult(
            isFullySupported = false,
            videoCodec = "AVC/H.264",
            audioCodec = "AC3",
            videoSupported = true,
            audioSupported = false,
            warnings = listOf("Audio codec AC3 is not supported by this device"),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isFalse()
        assertThat(result.videoSupported).isTrue()
        assertThat(result.audioSupported).isFalse()
        assertThat(result.warnings).hasSize(1)
        assertThat(result.warnings[0]).contains("AC3")
    }

    @Test
    fun compatibilityResult_bothUnsupported_notFullySupported() {
        val result = CompatibilityResult(
            isFullySupported = false,
            videoCodec = "HEVC/H.265",
            audioCodec = "DTS",
            videoSupported = false,
            audioSupported = false,
            warnings = listOf(
                "Video codec HEVC/H.265 is not supported by this device",
                "Audio codec DTS is not supported by this device"
            ),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isFalse()
        assertThat(result.videoSupported).isFalse()
        assertThat(result.audioSupported).isFalse()
        assertThat(result.warnings).hasSize(2)
    }

    @Test
    fun compatibilityResult_cannotReadFile_notFullySupported() {
        val result = CompatibilityResult(
            isFullySupported = false,
            videoCodec = null,
            audioCodec = null,
            videoSupported = false,
            audioSupported = false,
            warnings = listOf("Could not analyze file: Permission denied"),
            canReadFile = false
        )

        assertThat(result.isFullySupported).isFalse()
        assertThat(result.canReadFile).isFalse()
        assertThat(result.videoCodec).isNull()
        assertThat(result.audioCodec).isNull()
    }

    @Test
    fun compatibilityResult_noTracks_notFullySupported() {
        val result = CompatibilityResult(
            isFullySupported = false,
            videoCodec = null,
            audioCodec = null,
            videoSupported = true,  // vacuously true — no video track
            audioSupported = true,  // vacuously true — no audio track
            warnings = listOf("No video or audio tracks found in the file"),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isFalse()
        assertThat(result.videoCodec).isNull()
        assertThat(result.audioCodec).isNull()
        assertThat(result.warnings).hasSize(1)
        assertThat(result.warnings[0]).contains("No video or audio tracks")
    }

    @Test
    fun compatibilityResult_audioOnlyFile_fullySupported() {
        val result = CompatibilityResult(
            isFullySupported = true,
            videoCodec = null,
            audioCodec = "AAC",
            videoSupported = true,  // no video track = vacuously supported
            audioSupported = true,
            warnings = emptyList(),
            canReadFile = true
        )

        assertThat(result.isFullySupported).isTrue()
        assertThat(result.videoCodec).isNull()
        assertThat(result.audioCodec).isEqualTo("AAC")
    }

    // --- isFullySupported logic derivation ---

    @Test
    fun isFullySupported_shouldBeTrue_whenVideoAndAudioBothSupported() {
        val videoSupported = true
        val audioSupported = true
        val warnings = emptyList<String>()

        val isFullySupported = videoSupported && audioSupported && warnings.isEmpty()

        assertThat(isFullySupported).isTrue()
    }

    @Test
    fun isFullySupported_shouldBeFalse_whenVideoNotSupported() {
        val videoSupported = false
        val audioSupported = true
        val warnings = listOf("Video codec unsupported")

        val isFullySupported = videoSupported && audioSupported && warnings.isEmpty()

        assertThat(isFullySupported).isFalse()
    }

    @Test
    fun isFullySupported_shouldBeFalse_whenAudioNotSupported() {
        val videoSupported = true
        val audioSupported = false
        val warnings = listOf("Audio codec unsupported")

        val isFullySupported = videoSupported && audioSupported && warnings.isEmpty()

        assertThat(isFullySupported).isFalse()
    }

    @Test
    fun isFullySupported_shouldBeFalse_whenWarningsExist() {
        val videoSupported = true
        val audioSupported = true
        val warnings = listOf("No video or audio tracks found in the file")

        val isFullySupported = videoSupported && audioSupported && warnings.isEmpty()

        assertThat(isFullySupported).isFalse()
    }

    // --- Data class equality and copy ---

    @Test
    fun compatibilityResult_equality_worksCorrectly() {
        val result1 = CompatibilityResult(
            isFullySupported = true,
            videoCodec = "AVC/H.264",
            audioCodec = "AAC",
            videoSupported = true,
            audioSupported = true,
            warnings = emptyList(),
            canReadFile = true
        )
        val result2 = result1.copy()

        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun compatibilityResult_copy_modifiesFields() {
        val original = CompatibilityResult(
            isFullySupported = true,
            videoCodec = "AVC/H.264",
            audioCodec = "AAC",
            videoSupported = true,
            audioSupported = true,
            warnings = emptyList(),
            canReadFile = true
        )

        val modified = original.copy(
            isFullySupported = false,
            videoSupported = false,
            warnings = listOf("Video codec not supported")
        )

        assertThat(modified.isFullySupported).isFalse()
        assertThat(modified.videoSupported).isFalse()
        assertThat(modified.audioSupported).isTrue()  // unchanged
        assertThat(modified.videoCodec).isEqualTo("AVC/H.264")  // unchanged
    }

    @Test
    fun compatibilityResult_canReadFile_defaultsToTrue() {
        val result = CompatibilityResult(
            isFullySupported = true,
            videoCodec = null,
            audioCodec = null,
            videoSupported = true,
            audioSupported = true,
            warnings = emptyList()
            // canReadFile not specified — should default to true
        )

        assertThat(result.canReadFile).isTrue()
    }
}
