package com.dima.kidsvideoplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val BOUNCE_RESET_DELAY_MS = 250L

@Composable
fun BounceButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color = Color.White,
    icon: String = "",
    size: Dp = 80.dp,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    fontSize: TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    val buttonWidth = if (width != Dp.Unspecified) width else size
    val buttonHeight = if (height != Dp.Unspecified) height else size

    var isPressed by remember { mutableStateOf(false) }
    val combinedScale = rememberKidButtonScale(isPressed)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(BOUNCE_RESET_DELAY_MS)
            isPressed = false
        }
    }

    Surface(
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .graphicsLayer {
                scaleX = combinedScale
                scaleY = combinedScale
            },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, textColor.copy(alpha = 0.3f)),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        onClick = {
            isPressed = true
            onClick()
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = icon,
                        fontSize = (fontSize.value * 0.8).sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = text,
                        fontSize = (fontSize.value * 0.35).sp,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = text,
                    fontSize = fontSize,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
