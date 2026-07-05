package com.dima.kidsvideoplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*

private const val PRESSED_SCALE = 0.75f
private const val PULSE_SCALE = 1.04f
private const val PULSE_DURATION_MS = 800

/**
 * Shared bounce + idle pulse scale for kid-friendly buttons.
 */
@Composable
fun rememberKidButtonScale(isPressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "kid_button_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "kid_button_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_DURATION_MS, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kid_button_pulse_scale"
    )

    return scale * pulseScale
}
