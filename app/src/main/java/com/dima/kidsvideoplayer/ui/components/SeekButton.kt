package com.dima.kidsvideoplayer.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.kidsvideoplayer.player.SeekAccelerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SeekButton(
    icon: ImageVector,
    contentDescription: String,
    onSeek: (offsetMs: Long) -> Unit,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var seekLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val accelerator = remember { SeekAccelerator() }
    val combinedScale = rememberKidButtonScale(isPressed)
    val currentOnSeek by rememberUpdatedState(onSeek)

    val backgroundColor = Color(0xFF42A5F5)
    val textColor = Color.White

    val iconSize = (size.value * 36 / 80).dp

    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        accelerator.reset()

                        val seekJob = scope.launch {
                            delay(400L)
                            seekLabel = "${accelerator.currentOffset}s"
                            while (isActive) {
                                val offsetMs = accelerator.nextOffsetMs()
                                currentOnSeek(offsetMs)
                                seekLabel = "${accelerator.currentOffset}s"
                                delay(300L)
                            }
                        }

                        try {
                            tryAwaitRelease()
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
                modifier = Modifier.size(iconSize)
            )

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
