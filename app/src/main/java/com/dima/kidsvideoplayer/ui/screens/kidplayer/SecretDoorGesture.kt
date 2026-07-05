package com.dima.kidsvideoplayer.ui.screens.kidplayer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val SECRET_DOOR_TOUCH_SIZE = 72.dp
private const val SECRET_DOOR_HOLD_MS = 3000L
private const val HOLD_PROGRESS_STEP_MS = 100L

@Composable
fun SecretDoorGesture(
    onActivated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSettingsPressed by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isSettingsPressed) {
        if (!isSettingsPressed) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        holdProgress = 0f
        val steps = (SECRET_DOOR_HOLD_MS / HOLD_PROGRESS_STEP_MS).toInt()
        repeat(steps) { step ->
            delay(HOLD_PROGRESS_STEP_MS)
            if (!isSettingsPressed) return@LaunchedEffect
            holdProgress = (step + 1).toFloat() / steps
        }
        if (isSettingsPressed) {
            onActivated()
            isSettingsPressed = false
            holdProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .size(SECRET_DOOR_TOUCH_SIZE)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isSettingsPressed = true
                    waitForUpOrCancellation()
                    isSettingsPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.White.copy(
                alpha = if (isSettingsPressed) {
                    0.4f + 0.5f * holdProgress
                } else {
                    0.4f
                }
            )
        )
    }
}
