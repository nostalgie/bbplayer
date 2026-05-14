package com.dima.kidsvideoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = TextWhite,
    secondary = OrangeAccent,
    onSecondary = TextWhite,
    tertiary = BlueButton,
    background = BackgroundDark,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextGray,
    error = RedButton,
    onError = TextWhite,
    outline = DotBorder
)

@Composable
fun KidsVideoPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
