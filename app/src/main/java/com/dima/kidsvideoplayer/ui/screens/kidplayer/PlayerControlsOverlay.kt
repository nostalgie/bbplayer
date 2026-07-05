package com.dima.kidsvideoplayer.ui.screens.kidplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.SeekButton
import com.dima.kidsvideoplayer.ui.theme.BlueButton
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import kotlin.math.min

private val SliderReservedHeight = 72.dp
private const val NaturalColumnHeightDp = 490f
private const val ButtonHorizontalPaddingDp = 12f
private const val WidestButtonDp = 90f

internal fun playerControlsButtonScale(availableHeightDp: Float): Float =
    min(1f, availableHeightDp / NaturalColumnHeightDp)

internal fun playerControlsSliderStartPadding(scale: Float) =
    ((ButtonHorizontalPaddingDp + WidestButtonDp) * scale).dp

@Composable
fun BoxScope.PlayerControlsOverlay(
    visible: Boolean,
    filteredVideoCount: Int,
    isPlaying: Boolean,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBackward: (Long) -> Unit,
    onSeekForward: (Long) -> Unit
) {
    val hasMultipleVideos = filteredVideoCount > 1
    val fadeSpec = tween<Float>(500)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = fadeSpec),
        exit = fadeOut(animationSpec = fadeSpec),
        modifier = Modifier.align(Alignment.CenterStart)
    ) {
        BoxWithConstraints {
            val scale = playerControlsButtonScale((maxHeight - SliderReservedHeight).value)
            val bounceSmall = (80 * scale).dp
            val bouncePlay = (90 * scale).dp
            val seekSize = (80 * scale).dp
            val spacing = (12 * scale).dp
            val fontSize = (36 * scale).sp
            val horizontalPadding = (ButtonHorizontalPaddingDp * scale).dp
            val verticalPadding = (16 * scale).dp

            Column(
                modifier = Modifier.padding(
                    start = horizontalPadding,
                    top = verticalPadding,
                    bottom = verticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BounceButton(
                    text = "⏮",
                    onClick = { if (hasMultipleVideos) onPrevious() },
                    backgroundColor = BlueButton,
                    size = bounceSmall,
                    fontSize = fontSize,
                    modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                )

                SeekButton(
                    icon = Icons.Default.FastRewind,
                    contentDescription = "Seek backward",
                    onSeek = onSeekBackward,
                    size = seekSize
                )

                BounceButton(
                    text = if (isPlaying) "⏸" else "▶",
                    onClick = onPlayPause,
                    backgroundColor = GreenPrimary,
                    size = bouncePlay,
                    fontSize = fontSize
                )

                SeekButton(
                    icon = Icons.Default.FastForward,
                    contentDescription = "Seek forward",
                    onSeek = onSeekForward,
                    size = seekSize
                )

                BounceButton(
                    text = "⏭",
                    onClick = { if (hasMultipleVideos) onNext() },
                    backgroundColor = BlueButton,
                    size = bounceSmall,
                    fontSize = fontSize,
                    modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                )
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = fadeSpec),
        exit = fadeOut(animationSpec = fadeSpec),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val scale = playerControlsButtonScale((maxHeight - SliderReservedHeight).value)
            val sliderStartPadding = playerControlsSliderStartPadding(scale)

            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = sliderStartPadding,
                        end = 24.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
    }
}
