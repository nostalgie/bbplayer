/**
 * Tests for [SeekAccelerator] — verifies progressive seek acceleration logic:
 * initial offset, exponential growth with 1.5× factor, capping at 600 s, and reset.
 */
package com.dima.kidsvideoplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SeekAcceleratorTest {

    private lateinit var accelerator: SeekAccelerator

    @Before
    fun setUp() {
        accelerator = SeekAccelerator()
    }

    // --- Initial state ---

    @Test
    fun currentOffset_initialValue_is10Seconds() {
        assertThat(accelerator.currentOffset).isEqualTo(10L)
    }

    @Test
    fun nextOffsetMs_firstCall_returns10SecondsInMs() {
        val offsetMs = accelerator.nextOffsetMs()
        assertThat(offsetMs).isEqualTo(10_000L)
    }

    // --- Acceleration over repeated calls ---

    @Test
    fun nextOffsetMs_acceleratesWithGrowthFactor() {
        // Sequence: 10 → 15 → 22 → 33 → 49 → 73 → … (×1.5 each step)
        assertThat(accelerator.nextOffsetMs()).isEqualTo(10_000L)
        assertThat(accelerator.currentOffset).isEqualTo(15L)

        assertThat(accelerator.nextOffsetMs()).isEqualTo(15_000L)
        assertThat(accelerator.currentOffset).isEqualTo(22L)

        assertThat(accelerator.nextOffsetMs()).isEqualTo(22_000L)
        assertThat(accelerator.currentOffset).isEqualTo(33L)

        assertThat(accelerator.nextOffsetMs()).isEqualTo(33_000L)
        assertThat(accelerator.currentOffset).isEqualTo(49L)

        assertThat(accelerator.nextOffsetMs()).isEqualTo(49_000L)
        assertThat(accelerator.currentOffset).isEqualTo(73L)
    }

    @Test
    fun nextOffsetMs_eachCallReturnsGreaterOrEqualOffset() {
        var previous = accelerator.nextOffsetMs()
        repeat(20) {
            val current = accelerator.nextOffsetMs()
            assertThat(current).isAtLeast(previous)
            previous = current
        }
    }

    // --- Capping at maximum ---

    @Test
    fun nextOffsetMs_capsAt600Seconds() {
        // Drive the accelerator until it reaches the cap
        val offsets = mutableListOf<Long>()
        repeat(15) {
            offsets.add(accelerator.nextOffsetMs())
        }
        // The last offset should be the max: 600 000 ms
        assertThat(offsets.last()).isEqualTo(600_000L)
    }

    @Test
    fun nextOffsetMs_staysAt600SecondsAfterCap() {
        // Drive past the cap
        repeat(15) { accelerator.nextOffsetMs() }
        // Subsequent calls should still return 600 000 ms
        assertThat(accelerator.nextOffsetMs()).isEqualTo(600_000L)
        assertThat(accelerator.nextOffsetMs()).isEqualTo(600_000L)
        assertThat(accelerator.currentOffset).isEqualTo(600L)
    }

    // --- Reset behavior ---

    @Test
    fun reset_returnsOffsetToInitial10Seconds() {
        // Advance several steps
        repeat(5) { accelerator.nextOffsetMs() }
        assertThat(accelerator.currentOffset).isGreaterThan(10L)

        // Reset
        accelerator.reset()
        assertThat(accelerator.currentOffset).isEqualTo(10L)
    }

    @Test
    fun reset_thenNextOffsetMs_startsAccelerationOver() {
        // Advance, reset, then verify the sequence restarts
        repeat(3) { accelerator.nextOffsetMs() }
        accelerator.reset()

        assertThat(accelerator.nextOffsetMs()).isEqualTo(10_000L)
        assertThat(accelerator.nextOffsetMs()).isEqualTo(15_000L)
        assertThat(accelerator.currentOffset).isEqualTo(22L)
    }

    // --- Edge cases ---

    @Test
    fun currentOffset_reflectsNextValueNotYetReturned() {
        // After calling nextOffsetMs(), currentOffset shows the *next* offset
        accelerator.nextOffsetMs() // returns 10_000, currentOffset becomes 15
        assertThat(accelerator.currentOffset).isEqualTo(15L)
    }

    @Test
    fun multipleResets_alwaysReturnTo10Seconds() {
        repeat(3) {
            repeat(5) { accelerator.nextOffsetMs() }
            accelerator.reset()
            assertThat(accelerator.currentOffset).isEqualTo(10L)
        }
    }

    @Test
    fun nextOffsetMs_alwaysReturnsMultiplesOf1000() {
        // Every returned value should be a whole number of seconds
        repeat(15) {
            val offsetMs = accelerator.nextOffsetMs()
            assertThat(offsetMs % 1000L).isEqualTo(0L)
        }
    }
}
