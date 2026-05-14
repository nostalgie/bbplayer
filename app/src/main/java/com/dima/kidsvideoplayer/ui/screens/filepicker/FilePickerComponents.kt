package com.dima.kidsvideoplayer.ui.screens.filepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dima.kidsvideoplayer.ui.theme.CardSurface
import com.dima.kidsvideoplayer.ui.theme.DashboardBackground
import com.dima.kidsvideoplayer.ui.theme.GreenPrimary
import com.dima.kidsvideoplayer.ui.theme.RedButton

/**
 * Permission request screen shown when storage access is not granted.
 */
@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🔒",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Требуется доступ к файлам",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Для просмотра и выбора видеофайлов приложению необходим доступ к хранилищу устройства.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Предоставить доступ",
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Назад", color = Color.White.copy(alpha = 0.5f))
        }
    }
}

/**
 * Top bar showing current path and navigation controls.
 */
@Composable
fun FilePickerTopBar(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = CardSurface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                TextButton(onClick = onBack) {
                    Text("← Назад", color = Color.White, fontSize = 14.sp)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Up button
                IconButton(onClick = onNavigateUp, enabled = currentPath != ROOT_PATH) {
                    Text("⬆", fontSize = 20.sp, color = if (currentPath != ROOT_PATH) Color.White else Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Path display
                Text(
                    text = currentPath.removePrefix("/storage/emulated/0").ifEmpty { "/" },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Divider(color = Color.White.copy(alpha = 0.1f))
        }
    }
}

/**
 * Folder item in the file list.
 *
 * @param item The folder's file system info
 * @param videoCount Total number of videos in the folder (null if still counting)
 * @param selectedCount Number of videos currently selected in this folder
 * @param toggleableState The tri-state for the checkbox (Off / Indeterminate / On)
 * @param onSelect Called when the folder checkbox is toggled
 * @param onClick Called when the folder body is clicked (navigates into folder)
 */
@Composable
fun FolderItem(
    item: FileSystemItem,
    videoCount: Int?,
    selectedCount: Int,
    toggleableState: ToggleableState,
    onSelect: () -> Unit,
    onClick: () -> Unit
) {
    val hasSelection = selectedCount > 0

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (hasSelection) GreenPrimary.copy(alpha = 0.15f) else CardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tri-state checkbox for selection
            TriStateCheckbox(
                state = toggleableState,
                onClick = onSelect,
                colors = CheckboxDefaults.colors(
                    checkedColor = GreenPrimary,
                    uncheckedColor = Color.White.copy(alpha = 0.5f)
                )
            )

            // Folder content (clickable to navigate)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📁", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Secondary line: selection count or video count
                    Text(
                        text = when {
                            videoCount == null -> "Подсчёт..."
                            videoCount == 0 -> "Нет видео"
                            selectedCount > 0 -> "Выбрано: $selectedCount из $videoCount"
                            else -> "$videoCount видео"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Video file item in the file list.
 */
@Composable
fun VideoFileItem(
    item: FileSystemItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) GreenPrimary.copy(alpha = 0.15f) else CardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = GreenPrimary,
                    uncheckedColor = Color.White.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "🎬", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(item.size),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Bottom bar with selection summary and action buttons.
 */
@Composable
fun FilePickerBottomBar(
    selectedFileCount: Int,
    selectedFolderCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = CardSurface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Divider(color = Color.White.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection count
                Column {
                    Text(
                        text = "Выбрано: $selectedFileCount файлов",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (selectedFolderCount > 0) {
                        Text(
                            text = "из $selectedFolderCount папок",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = selectedFileCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            disabledContainerColor = GreenPrimary.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("Добавить", color = Color.White)
                    }
                }
            }
        }
    }
}
