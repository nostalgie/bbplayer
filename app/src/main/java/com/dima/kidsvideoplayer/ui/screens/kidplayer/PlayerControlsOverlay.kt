package com.dima.kidsvideoplayer.ui.screens.kidplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.dima.kidsvideoplayer.player.VideoPlayerManager
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.components.SeekButton
import com.dima.kidsvideoplayer.ui.theme.BlueButton
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary

@Composable
fun PlayerControlsOverlay(
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
    onSeekForward: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(bottom = 16.dp)
        ) {
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasMultipleVideos = filteredVideoCount > 1

                BounceButton(
                    text = "⏮",
                    onClick = { if (hasMultipleVideos) onPrevious() },
                    backgroundColor = BlueButton,
                    size = 80.dp,
                    fontSize = 36.sp,
                    modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                )

                SeekButton(
                    icon = Icons.Default.FastRewind,
                    contentDescription = "Seek backward",
                    onSeek = onSeekBackward
                )

                BounceButton(
                    text = if (isPlaying) "⏸" else "▶",
                    onClick = onPlayPause,
                    backgroundColor = GreenPrimary,
                    size = 90.dp,
                    fontSize = 36.sp
                )

                SeekButton(
                    icon = Icons.Default.FastForward,
                    contentDescription = "Seek forward",
                    onSeek = onSeekForward
                )

                BounceButton(
                    text = "⏭",
                    onClick = { if (hasMultipleVideos) onNext() },
                    backgroundColor = BlueButton,
                    size = 80.dp,
                    fontSize = 36.sp,
                    modifier = if (!hasMultipleVideos) Modifier.alpha(0.4f) else Modifier
                )
            }
        }
    }
}
