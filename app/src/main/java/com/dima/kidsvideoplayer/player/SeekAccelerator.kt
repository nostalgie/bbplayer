package com.dima.kidsvideoplayer.player

import kotlin.math.min

/**
 * Handles progressive seek acceleration logic for long-press seeking.
 *
 * Starts at 10 seconds per seek and grows exponentially with a 1.5x factor:
 * 10s → 15s → 22s → 33s → 50s → ... capped at 600 seconds (10 minutes).
 */
class SeekAccelerator {

    companion object {
        private const val INITIAL_OFFSET_SECONDS = 10L
        private const val MAX_OFFSET_SECONDS = 600L
        private const val GROWTH_FACTOR = 1.5
    }

    private var currentOffsetSeconds: Long = INITIAL_OFFSET_SECONDS

    /**
     * The current seek offset in seconds, for display purposes.
     */
    val currentOffset: Long
        get() = currentOffsetSeconds

    /**
     * Returns the next seek offset in milliseconds and advances the accelerator
     * for the subsequent call.
     */
    fun nextOffsetMs(): Long {
        val offsetMs = currentOffsetSeconds * 1000L
        currentOffsetSeconds = min(
            (currentOffsetSeconds * GROWTH_FACTOR).toLong(),
            MAX_OFFSET_SECONDS
        )
        return offsetMs
    }

    /**
     * Resets the accelerator back to the initial state.
     */
    fun reset() {
        currentOffsetSeconds = INITIAL_OFFSET_SECONDS
    }
}
