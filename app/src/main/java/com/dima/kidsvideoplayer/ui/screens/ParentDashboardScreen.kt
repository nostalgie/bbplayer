package com.dima.kidsvideoplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dima.kidsvideoplayer.data.VideoRepository
import com.dima.kidsvideoplayer.player.VideoCompatibilityChecker
import com.dima.kidsvideoplayer.ui.components.BounceButton
import com.dima.kidsvideoplayer.ui.theme.CardSurface
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.ExitRed
import com.dima.kidsvideoplayer.ui.theme.FolderBlue
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.OrangeAccent
import com.dima.kidsvideoplayer.ui.theme.RedButton
import kotlinx.coroutines.launch

/**
 * Parent Dashboard Screen — manage videos and settings.
 *
 * Features:
 * - Add videos via SAF (system file picker)
 * - List of added videos with ability to remove
 * - "Back to Kid Mode" button
 * - takePersistableUriPermission for persistent access
 */
@Composable
fun ParentDashboardScreen(
    videoRepository: VideoRepository,
    videoCompatibilityChecker: VideoCompatibilityChecker,
    onBackToKidMode: () -> Unit,
    onNavigateToFilePicker: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val videoUris by videoRepository.videoUris.collectAsStateWithLifecycle(initialValue = emptyList())

    // SAF video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so URI survives reboot
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Run compatibility check before adding
            coroutineScope.launch {
                val result = videoCompatibilityChecker.checkCompatibility(it)
                if (result.isFullySupported) {
                    videoRepository.addVideoUri(it.toString())
                } else {
                    Toast.makeText(
                        context,
                        "Видео не поддерживается на этом устройстве",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(24.dp)
    ) {
        // ==============================
        // Header
        // ==============================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔒 Родительская панель",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Back to Kid Mode button
            BounceButton(
                text = "Назад",
                onClick = onBackToKidMode,
                backgroundColor = GreenPrimary,
                textColor = Color.White,
                icon = "👶",
                size = 100.dp,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==============================
        // Add Video Buttons
        // ==============================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BounceButton(
                text = "Добавить файл",
                onClick = {
                    videoPickerLauncher.launch(arrayOf("video/*"))
                },
                backgroundColor = OrangeAccent,
                textColor = Color.White,
                icon = "📄",
                size = 120.dp,
                fontSize = 18.sp
            )

            BounceButton(
                text = "Из папки",
                onClick = onNavigateToFilePicker,
                backgroundColor = FolderBlue,
                textColor = Color.White,
                icon = "📂",
                size = 120.dp,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==============================
        // Video List
        // ==============================
        Text(
            text = "Видео (${videoUris.size}):",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (videoUris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет добавленных видео.\nНажмите «Добавить» чтобы выбрать видео.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = videoUris,
                    key = { _, uri -> uri }
                ) { index, uriString ->
                    VideoListItem(
                        index = index,
                        uriString = uriString,
                        onRemove = {
                            coroutineScope.launch {
                                videoRepository.removeVideoUri(uriString)
                            }
                        }
                    )
                }
            }
        }

        // ==============================
        // Clear All Button
        // ==============================
        if (videoUris.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            BounceButton(
                text = "Удалить все",
                onClick = {
                    coroutineScope.launch {
                        videoRepository.clearAll()
                    }
                },
                backgroundColor = RedButton,
                textColor = Color.White,
                size = 100.dp,
                fontSize = 14.sp
            )
        }

        // ==============================
        // Exit App Button
        // ==============================
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            BounceButton(
                text = "Выйти",
                onClick = onExitApp,
                backgroundColor = ExitRed,
                textColor = Color.White,
                icon = "🚪",
                size = 100.dp,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Single video item in the list.
 */
@Composable
private fun VideoListItem(
    index: Int,
    uriString: String,
    onRemove: () -> Unit
) {
    val fileName = remember(uriString) {
        try {
            val uri = Uri.parse(uriString)
            uri.lastPathSegment ?: "Видео ${index + 1}"
        } catch (e: Exception) {
            "Видео ${index + 1}"
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🎬 Видео ${index + 1}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = fileName,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove button
            Surface(
                onClick = onRemove,
                shape = RoundedCornerShape(8.dp),
                color = RedButton.copy(alpha = 0.2f)
            ) {
                Text(
                    text = " ✕ ",
                    fontSize = 18.sp,
                    color = RedButton,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
