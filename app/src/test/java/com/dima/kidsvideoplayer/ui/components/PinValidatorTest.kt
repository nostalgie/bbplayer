package com.dima.kidsvideoplayer.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class PinValidatorTest {

    private lateinit var validator: PinValidator

    @Before
    fun setUp() {
        validator = PinValidator("1111", maxAttempts = 3)
    }

    @Test
    fun validate_correctPin_returnsCorrect() {
        assertThat(validator.validate("1111")).isEqualTo(PinResult.Correct)
    }

    @Test
    fun validate_incorrectPin_returnsIncorrect() {
        assertThat(validator.validate("0000")).isEqualTo(PinResult.Incorrect)
    }

    @Test
    fun validate_threeFailures_locksOut() {
        assertThat(validator.validate("0000")).isEqualTo(PinResult.Incorrect)
        assertThat(validator.validate("0000")).isEqualTo(PinResult.Incorrect)
        assertThat(validator.validate("0000")).isEqualTo(PinResult.LockedOut)
    }

    @Test
    fun validate_whileLockedOut_staysLockedOut() {
        repeat(3) { validator.validate("0000") }
        assertThat(validator.validate("1111")).isEqualTo(PinResult.LockedOut)
    }

    @Test
    fun resetLockout_allowsRetry() {
        repeat(3) { validator.validate("0000") }
        validator.resetLockout()
        assertThat(validator.validate("1111")).isEqualTo(PinResult.Correct)
    }

    @Test
    fun remainingAttempts_decreasesOnFailure() {
        assertThat(validator.remainingAttempts()).isEqualTo(3)
        validator.validate("0000")
        assertThat(validator.remainingAttempts()).isEqualTo(2)
    }

    @Test
    fun correctPin_resetsFailedAttempts() {
        validator.validate("0000")
        validator.validate("1111")
        assertThat(validator.remainingAttempts()).isEqualTo(3)
    }

    @Test
    fun isLockedOut_reflectsState() {
        assertThat(validator.isLockedOut()).isFalse()
        repeat(3) { validator.validate("0000") }
        assertThat(validator.isLockedOut()).isTrue()
    }
}
