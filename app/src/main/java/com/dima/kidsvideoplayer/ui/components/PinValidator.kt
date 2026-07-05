package com.dima.kidsvideoplayer.ui.components

sealed class PinResult {
    data object Correct : PinResult()
    data object Incorrect : PinResult()
    data object LockedOut : PinResult()
}

class PinValidator(
    private val correctPin: String,
    private val maxAttempts: Int = 3
) {
    private var failedAttempts = 0
    private var isLockedOut = false

    fun validate(input: String): PinResult {
        if (isLockedOut) return PinResult.LockedOut
        return if (input == correctPin) {
            failedAttempts = 0
            PinResult.Correct
        } else {
            failedAttempts++
            if (failedAttempts >= maxAttempts) {
                isLockedOut = true
                PinResult.LockedOut
            } else {
                PinResult.Incorrect
            }
        }
    }

    fun resetLockout() {
        isLockedOut = false
        failedAttempts = 0
    }

    fun isLockedOut(): Boolean = isLockedOut

    fun remainingAttempts(): Int = (maxAttempts - failedAttempts).coerceAtLeast(0)
}
