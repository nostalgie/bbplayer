package com.dima.kidsvideoplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.kidsvideoplayer.player.SeekAccelerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Seek button with tap (±10s) and long-press (progressive acceleration) support.
 *
 * Visual styling matches [BounceButton] — spring animation, rounded shape, border.
 *
 * @param icon Material Icon to display (e.g. FastRewind / FastForward)
 * @param contentDescription Accessibility description
 * @param onSeek Callback invoked with the seek offset in milliseconds
 * @param modifier Optional modifier
 */
@Composable
fun SeekButton(
    icon: ImageVector,
    contentDescription: String,
    onSeek: (offsetMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var seekLabel by remember { mutableStateOf<String?>(null) }

    // Spring-based scale animation on press (matches BounceButton)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "seek_bounce_scale"
    )

    // Subtle idle pulsing animation (matches BounceButton)
    val infiniteTransition = rememberInfiniteTransition(label = "seek_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "seek_pulse_scale"
    )

    val backgroundColor = Color(0xFF42A5F5)
    val textColor = Color.White

    Surface(
        modifier = modifier
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale * pulseScale
                scaleY = scale * pulseScale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true

                    try {
                        // Wait for finger lift or long-press timeout (400ms)
                        val up = withTimeoutOrNull(400L) {
                            waitForUpOrCancellation()
                        }

                        if (up != null) {
                            // Finger lifted quickly — single tap: seek 10 seconds
                            onSeek(10_000L)
                        } else {
                            // Long press: progressively accelerate seeking
                            val accelerator = SeekAccelerator()
                            seekLabel = "${accelerator.currentOffset}s"

                            while (true) {
                                val offsetMs = accelerator.nextOffsetMs()
                                onSeek(offsetMs)
                                seekLabel = "${accelerator.currentOffset}s"
                                delay(400L)
                            }
                        }
                    } finally {
                        isPressed = false
                        seekLabel = null
                    }
                }
            },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, textColor.copy(alpha = 0.3f)),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = textColor,
                modifier = Modifier.size(36.dp)
            )

            // Seek offset label shown during long press
            if (seekLabel != null) {
                Text(
                    text = seekLabel!!,
                    fontSize = 11.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}
