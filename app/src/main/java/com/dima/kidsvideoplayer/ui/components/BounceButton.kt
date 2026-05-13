package com.dima.kidsvideoplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/**
 * Kid-friendly button with spring bounce animation on press.
 * No standard Material styling — playful, rounded, colorful.
 *
 * @param text Button label
 * @param onClick Click handler
 * @param backgroundColor Background color
 * @param textColor Text color
 * @param icon Optional emoji/icon character displayed above text
 * @param size Button size (width & height)
 * @param fontSize Text size for the icon
 * @param modifier Optional modifier
 */
@Composable
fun BounceButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color = Color.White,
    icon: String = "",
    size: Dp = 80.dp,
    fontSize: TextUnit = 28.sp,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    // Spring-based scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )

    // Subtle idle pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Reset bounce after short delay
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(250)
            isPressed = false
        }
    }

    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale * pulseScale
                scaleY = scale * pulseScale
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

/**
 * Navigation button variant for prev/next video controls.
 */
@Composable
fun NavBounceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF42A5F5),
    textColor: Color = Color.White,
    icon: String = ""
) {
    BounceButton(
        text = text,
        onClick = onClick,
        backgroundColor = backgroundColor,
        textColor = textColor,
        icon = icon,
        size = 90.dp,
        fontSize = 32.sp,
        modifier = modifier
    )
}
