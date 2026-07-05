package com.dima.kidsvideoplayer.ui.screens.kidplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
private const val NaturalButtonSizeDp = 80f
private const val ButtonHorizontalPaddingDp = 12f
private val MenuVerticalOffsetUp = 20.dp
private val SliderHorizontalPadding = 24.dp
private val SliderTopPadding = 8.dp
private val SliderBottomPadding = 8.dp

internal fun playerControlsButtonScale(availableHeightDp: Float): Float =
    min(1f, availableHeightDp / NaturalColumnHeightDp)

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
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset(y = -MenuVerticalOffsetUp)
    ) {
        BoxWithConstraints {
            val scale = playerControlsButtonScale((maxHeight - SliderReservedHeight).value)
            val buttonSize = (NaturalButtonSizeDp * scale).dp
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
                    size = buttonSize,
                    fontSize = fontSize,
                    modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                )

                SeekButton(
                    icon = Icons.Default.FastRewind,
                    contentDescription = "Seek backward",
                    onSeek = onSeekBackward,
                    size = buttonSize
                )

                BounceButton(
                    text = "",
                    imageIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = onPlayPause,
                    backgroundColor = if (isPlaying) BlueButton else GreenPrimary,
                    size = buttonSize,
                    fontSize = fontSize
                )

                SeekButton(
                    icon = Icons.Default.FastForward,
                    contentDescription = "Seek forward",
                    onSeek = onSeekForward,
                    size = buttonSize
                )

                BounceButton(
                    text = "⏭",
                    onClick = { if (hasMultipleVideos) onNext() },
                    backgroundColor = BlueButton,
                    size = buttonSize,
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
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SliderHorizontalPadding,
                        end = SliderHorizontalPadding,
                        top = SliderTopPadding,
                        bottom = SliderBottomPadding
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
