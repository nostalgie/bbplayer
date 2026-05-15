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
import com.dima.kidsvideoplayer.ui.theme.OrangeAccent
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
    storageRootPaths: Set<String>,
    onNavigateUp: () -> Unit
) {
    val isAtStorageRoot = currentPath == STORAGE_ROOT
    val canNavigateUp = !isAtStorageRoot

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
                // Up button
                IconButton(onClick = onNavigateUp, enabled = canNavigateUp) {
                    Text("⬆", fontSize = 20.sp, color = if (canNavigateUp) Color.White else Color.Gray)
                }

                // Path display
                val displayText = when {
                    isAtStorageRoot -> "Хранилища"
                    currentPath in storageRootPaths -> {
                        // Show just the storage name
                        if (currentPath == INTERNAL_STORAGE_PATH) "Внутренняя память" else currentPath.removePrefix("$STORAGE_ROOT/")
                    }
                    else -> {
                        // Show relative path from storage root
                        val storageRoot = storageRootPaths.find { currentPath.startsWith(it) }
                        if (storageRoot != null) {
                            currentPath.removePrefix(storageRoot).ifEmpty { "/" }
                        } else {
                            currentPath
                        }
                    }
                }
                Text(
                    text = displayText,
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
 * Storage volume item (internal storage or SD card).
 */
@Composable
fun StorageVolumeItem(
    volume: StorageVolume,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (volume.isRemovable) "💾" else "📱",
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = volume.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = volume.path,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * Folder item in the file list.
 *
 * @param item The folder's file system info
 * @param videoCount Total number of videos in the folder (null if still counting)
 * @param supportedVideoCount Number of supported videos (null if still checking compatibility)
 * @param selectedCount Number of videos currently selected in this folder
 * @param toggleableState The tri-state for the checkbox (Off / Indeterminate / On)
 * @param onSelect Called when the folder checkbox is toggled
 * @param onClick Called when the folder body is clicked (navigates into folder)
 */
@Composable
fun FolderItem(
    item: FileSystemItem,
    videoCount: Int?,
    supportedVideoCount: Int?,
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
            Spacer(modifier = Modifier.width(8.dp))

            // Folder content (clickable to navigate)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
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
                    // Secondary line: selection count or supported video count
                    Text(
                        text = when {
                            videoCount == null -> "Подсчёт..."
                            supportedVideoCount == null -> if (videoCount == 0) "Нет видео" else "$videoCount видео"
                            supportedVideoCount == 0 && videoCount == 0 -> "Нет видео"
                            selectedCount > 0 -> "Выбрано: $selectedCount из $supportedVideoCount"
                            else -> "$supportedVideoCount видео"
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
 *
 * @param item The file's file system info
 * @param isSelected Whether the file is currently selected
 * @param isSupported null = still checking, true = fully supported, false = unsupported
 * @param onSelect Called when the file checkbox/row is toggled
 */
@Composable
fun VideoFileItem(
    item: FileSystemItem,
    isSelected: Boolean,
    isSupported: Boolean?,
    onSelect: () -> Unit
) {
    val isSelectable = isSupported != false

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            !isSelectable -> CardSurface.copy(alpha = 0.5f)
            isSelected -> GreenPrimary.copy(alpha = 0.15f)
            else -> CardSurface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelectable) Modifier.clickable(onClick = onSelect) else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = if (isSelectable) {{ onSelect() }} else null,
                enabled = isSelectable,
                colors = CheckboxDefaults.colors(
                    checkedColor = GreenPrimary,
                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                    disabledUncheckedColor = Color.White.copy(alpha = 0.2f),
                    disabledCheckedColor = GreenPrimary.copy(alpha = 0.3f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isSupported == false) "⚠️" else "🎬",
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelectable) Color.White else Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSupported == false) {
                    Text(
                        text = "не поддерживается",
                        fontSize = 12.sp,
                        color = OrangeAccent
                    )
                } else {
                    Text(
                        text = formatFileSize(item.size),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
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
