package com.dima.kidsvideoplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Seek button with tap (±10s) and long-press (progressive acceleration) support.
 *
 * Visual styling matches [BounceButton] — spring animation, rounded shape, border.
 *
 * ## How it works
 * - **Single tap** (press & release within 400 ms): seeks 10 seconds.
 * - **Long press** (hold past 400 ms): starts continuous seeking with progressive
 *   acceleration via [SeekAccelerator]. The seek offset label is shown below the icon.
 *
 * ## Why no flickering
 * The previous implementation used `withTimeoutOrNull` around `tryAwaitRelease()`,
 * which cancelled the press gesture and caused `detectTapGestures` to restart
 * immediately (finger still down → new press → rapid toggle → flicker).
 *
 * The fix: launch continuous seeking in a **separate coroutine** and let
 * `tryAwaitRelease()` suspend naturally until the finger lifts. No cancellation
 * of the press gesture means no restart loop.
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
    val scope = rememberCoroutineScope()
    val accelerator = remember { SeekAccelerator() }

    // Keep a reference to the latest onSeek callback so the coroutine always
    // invokes the current lambda even after recomposition.
    val currentOnSeek by rememberUpdatedState(onSeek)

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
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        accelerator.reset()

                        // Launch continuous seeking in a separate coroutine so
                        // that tryAwaitRelease() is never cancelled by a timeout.
                        val seekJob = scope.launch {
                            delay(400L) // grace period before continuous mode
                            seekLabel = "${accelerator.currentOffset}s"
                            while (isActive) {
                                val offsetMs = accelerator.nextOffsetMs()
                                currentOnSeek(offsetMs)
                                seekLabel = "${accelerator.currentOffset}s"
                                delay(300L)
                            }
                        }

                        try {
                            // Suspend until the user lifts their finger.
                            // This is NEVER cancelled by a timeout, so the gesture
                            // handler stays alive and no restart loop occurs.
                            tryAwaitRelease()

                            // If the job is still active, the user released before
                            // the 400 ms grace period → treat as a single tap.
                            if (seekJob.isActive) {
                                currentOnSeek(10_000L)
                            }
                        } finally {
                            seekJob.cancel()
                            isPressed = false
                            seekLabel = null
                        }
                    }
                )
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
