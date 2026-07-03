package com.dima.kidsvideoplayer.player

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks whether a video file's codecs are supported by the device before adding
 * it to the playlist. Uses [MediaExtractor] for lightweight track probing,
 * [MediaCodecList] for hardware decoder checks, and [FfmpegLibrary] for
 * software audio decoder fallback.
 */
class VideoCompatibilityChecker(private val context: Context) {

    companion object {
        private const val TAG = "VideoCompatChecker"

        /** Map of MIME type substrings to human-readable video codec names */
        private val VIDEO_CODEC_NAMES = mapOf(
            "avc1" to "AVC/H.264",
            "avc" to "AVC/H.264",
            "hevc" to "HEVC/H.265",
            "vp8" to "VP8",
            "vp9" to "VP9",
            "av01" to "AV1",
            "mp4v" to "MPEG-4",
            "mpeg2" to "MPEG-2",
            "s263" to "H.263",
            "3gpp" to "H.263"
        )

        /** Map of MIME type substrings to human-readable audio codec names */
        private val AUDIO_CODEC_NAMES = mapOf(
            "mp4a" to "AAC",
            "aac" to "AAC",
            "ac-3" to "AC3",
            "ac3" to "AC3",
            "ec-3" to "E-AC3",
            "eac3" to "E-AC3",
            "opus" to "Opus",
            "vorbis" to "Vorbis",
            "flac" to "FLAC",
            "mp3" to "MP3",
            "mpeg" to "MP3",
            "dts" to "DTS",
            "truehd" to "TrueHD",
            "amr" to "AMR",
            "raw" to "PCM",
            "alac" to "ALAC"
        )
    }

    /**
     * Check if a video file at the given URI is fully playable on this device.
     *
     * Handles both `content://` and `file://` URI schemes.
     * Runs on [Dispatchers.IO] since [MediaExtractor] performs I/O.
     *
     * @param uri The URI of the video file to check
     * @return A [CompatibilityResult] with codec details and support status
     */
    suspend fun checkCompatibility(uri: Uri): CompatibilityResult =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                // Set data source — different methods for content:// vs file://
                when (uri.scheme) {
                    "content" -> extractor.setDataSource(context, uri, null)
                    "file" -> extractor.setDataSource(uri.path ?: "")
                    else -> return@withContext CompatibilityResult(
                        isFullySupported = false,
                        videoCodec = null,
                        audioCodec = null,
                        videoSupported = false,
                        audioSupported = false,
                        warnings = listOf("Unknown URI scheme: ${uri.scheme}"),
                        canReadFile = false
                    )
                }

                var videoCodec: String? = null
                var audioCodec: String? = null
                var videoSupported = true  // default: no video track = vacuously supported
                var audioSupported = false // true if at least one audio track is playable
                var hasAudioTrack = false
                var videoMime: String? = null
                var audioMime: String? = null
                val warnings = mutableListOf<String>()

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                    when {
                        mime.startsWith("video/") -> {
                            videoMime = mime
                            videoCodec = videoCodecName(mime)
                            val hwSupported = isHardwareDecoderAvailable(mime)
                            videoSupported = hwSupported
                            if (!hwSupported) {
                                warnings.add(
                                    "Video codec $videoCodec is not supported by this device"
                                )
                            }
                        }
                        mime.startsWith("audio/") -> {
                            hasAudioTrack = true
                            audioMime = mime
                            audioCodec = audioCodecName(mime)
                            val hwSupported = isHardwareDecoderAvailable(mime)
                            val swSupported = isFfmpegDecoderAvailable(mime)
                            val trackSupported = hwSupported || swSupported
                            audioSupported = audioSupported || trackSupported
                        }
                    }
                }

                if (videoMime == null && audioMime == null) {
                    warnings.add("No video or audio tracks found in the file")
                }
                if (!hasAudioTrack) {
                    audioSupported = true // no audio track is fine
                } else if (!audioSupported) {
                    warnings.add("No playable audio track found on this device")
                }

                CompatibilityResult(
                    isFullySupported = videoSupported && audioSupported && warnings.isEmpty(),
                    videoCodec = videoCodec,
                    audioCodec = audioCodec,
                    videoSupported = videoSupported,
                    audioSupported = audioSupported,
                    warnings = warnings,
                    canReadFile = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check compatibility for $uri", e)
                CompatibilityResult(
                    isFullySupported = false,
                    videoCodec = null,
                    audioCodec = null,
                    videoSupported = false,
                    audioSupported = false,
                    warnings = listOf("Could not analyze file: ${e.message}"),
                    canReadFile = false
                )
            } finally {
                try {
                    extractor.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing MediaExtractor", e)
                }
            }
        }

    /**
     * Check if any hardware decoder on the device supports the given MIME type.
     * Uses [MediaCodecList.REGULAR_CODECS] which returns only stable, non-experimental decoders.
     */
    private fun isHardwareDecoderAvailable(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.contains(mimeType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query MediaCodecList for $mimeType", e)
            false
        }
    }

    /**
     * Check if FFmpeg software decoder supports the given audio MIME type.
     * First checks if the native library is available, then queries format support.
     */
    private fun isFfmpegDecoderAvailable(mimeType: String): Boolean {
        return try {
            FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mimeType)
        } catch (e: Exception) {
            Log.w(TAG, "FFmpeg library check failed for $mimeType", e)
            false
        }
    }

    /**
     * Map a raw video MIME type to a human-readable codec name.
     * Examples: "video/avc" → "AVC/H.264", "video/hevc" → "HEVC/H.265"
     */
    private fun videoCodecName(mimeType: String): String {
        val lower = mimeType.lowercase()
        for ((key, name) in VIDEO_CODEC_NAMES) {
            if (lower.contains(key)) return name
        }
        // Fallback: return the part after "video/"
        return mimeType.removePrefix("video/")
    }

    /**
     * Map a raw audio MIME type to a human-readable codec name.
     * Examples: "audio/mp4a-latm" → "AAC", "audio/ac3" → "AC3"
     */
    private fun audioCodecName(mimeType: String): String {
        val lower = mimeType.lowercase()
        for ((key, name) in AUDIO_CODEC_NAMES) {
            if (lower.contains(key)) return name
        }
        // Fallback: return the part after "audio/"
        return mimeType.removePrefix("audio/")
    }
}
