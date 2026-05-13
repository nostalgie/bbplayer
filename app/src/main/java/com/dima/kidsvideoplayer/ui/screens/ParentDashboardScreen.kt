package com.dima.kidsvideoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dima.kidsvideoplayer.data.VideoRepository

/**
 * Parent Dashboard Screen — placeholder for Step 4.
 * Will be fully implemented with SAF video picker, video list, and settings.
 */
@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    onBackToKidMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔒 Parent Dashboard Screen\n(Step 4 — coming soon)",
            color = Color.White
        )
    }
}
