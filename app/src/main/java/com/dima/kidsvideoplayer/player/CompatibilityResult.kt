package com.dima.kidsvideoplayer.player

/**
 * Result of a video compatibility check.
 *
 * @property isFullySupported  True if both video and audio tracks are playable on this device
 * @property videoCodec         Human-readable video codec name (e.g. "AVC/H.264", "HEVC/H.265"), null if no video track
 * @property audioCodec         Human-readable audio codec name (e.g. "AAC", "AC3", "E-AC3"), null if no audio track
 * @property videoSupported     True if the device has a hardware decoder for the video codec
 * @property audioSupported     True if the device has a hardware OR FFmpeg software decoder for the audio codec
 * @property warnings           Human-readable warning messages for unsupported tracks
 * @property canReadFile        False if MediaExtractor failed to open the file
 */
data class CompatibilityResult(
    val isFullySupported: Boolean,
    val videoCodec: String?,
    val audioCodec: String?,
    val videoSupported: Boolean,
    val audioSupported: Boolean,
    val warnings: List<String>,
    val canReadFile: Boolean = true
)
