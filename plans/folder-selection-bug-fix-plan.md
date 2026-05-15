# Folder Selection Bug Fix Plan

## Problem Analysis

The issue occurs in the FilePickerScreen when selecting folders that are still being scanned for video files:

1. **Initial State**: When a folder like DCIM is first loaded, `folderVideoPaths.value[item.path]` is `null`, indicating video scanning is in progress
2. **UI Display**: The folder shows "no video files" because the scan hasn't completed yet
3. **User Navigation**: User navigates into the folder and sees subfolders
4. **Return and Click**: When returning and clicking the checkbox, nothing happens due to a race condition

## Root Cause

The bug is in the `onSelect` callback for folders (lines 594-614 in FilePickerScreen.kt):

```kotlin
onSelect = {
    val paths = cachedPaths
    if (paths != null) {
        // Normal selection logic
    } else {
        // Still loading — find videos asynchronously
        coroutineScope.launch {
            val videos = withContext(Dispatchers.IO) {
                findVideosRecursively(File(item.path))
            }
            val foundPaths = videos.map { it.absolutePath }
            selectedFiles = selectedFiles + foundPaths
        }
    }
}
```

**Problems**:
1. When `cachedPaths == null`, it triggers a new scan even if one is already in progress
2. No visual feedback that the folder is being processed
3. The checkbox state doesn't reflect the actual processing status

## Solution Plan

### 1. Fix Race Condition
- Add a state to track which folders are currently being scanned
- Prevent multiple simultaneous scans of the same folder
- Only trigger new scan if no scan is in progress

### 2. Improve User Experience
- Add visual indicators for folders being scanned
- Show "Scanning..." instead of "No video files" during processing
- Make checkbox disabled during scanning to prevent user confusion

### 3. State Management
- Add `scanningFolders` state to track folders being processed
- Update `toggleableState` logic to account for scanning state
- Ensure proper state transitions when scanning completes

### 4. Code Changes

#### FilePickerScreen.kt Changes:

1. **Add scanning state tracking**:
```kotlin
// Track folders currently being scanned
var scanningFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
```

2. **Update folder video path loading logic**:
```kotlin
// Add folder to scanning set when starting scan
scanningFolders = scanningFolders + item.path

// Remove from scanning set when scan completes
scanningFolders = scanningFolders - item.path
```

3. **Modify onSelect callback**:
```kotlin
onSelect = {
    // Disable selection if folder is being scanned
    if (item.path in scanningFolders) return@FolderItem
    
    val paths = cachedPaths
    if (paths != null) {
        // Normal selection logic
    } else {
        // Start scanning if not already in progress
        if (item.path !in scanningFolders) {
            coroutineScope.launch {
                scanningFolders = scanningFolders + item.path
                try {
                    val videos = withContext(Dispatchers.IO) {
                        findVideosRecursively(File(item.path))
                    }
                    val foundPaths = videos.map { it.absolutePath }
                    selectedFiles = selectedFiles + foundPaths
                } finally {
                    scanningFolders = scanningFolders - item.path
                }
            }
        }
    }
}
```

4. **Update toggleableState logic**:
```kotlin
val toggleableState = when {
    item.path in scanningFolders -> ToggleableState.Off
    cachedPaths == null -> ToggleableState.Off
    // ... rest of existing logic
}
```

#### FilePickerComponents.kt Changes:

1. **Modify FolderItem to show scanning state**:
```kotlin
@Composable
fun FolderItem(
    item: FileSystemItem,
    videoCount: Int?,
    supportedVideoCount: Int?,
    selectedCount: Int,
    toggleableState: ToggleableState,
    isScanning: Boolean,  // New parameter
    onSelect: () -> Unit,
    onClick: () -> Unit
) {
    // Show scanning indicator instead of video count
    val displayText = when {
        isScanning -> "Сканирование..."
        videoCount == null -> "Подсчёт..."
        // ... existing logic
    }
}
```

2. **Disable checkbox during scanning**:
```kotlin
TriStateCheckbox(
    state = toggleableState,
    onClick = if (!isScanning) onSelect else null,
    enabled = !isScanning,
    // ... rest of properties
)
```

### 5. Implementation Steps

1. **Add scanning state tracking** in FilePickerScreen
2. **Modify folder loading logic** to track scanning progress
3. **Update onSelect callback** to handle scanning state
4. **Update toggleableState logic** to include scanning state
5. **Modify FolderItem component** to show scanning status
6. **Test with DCIM folder scenario** to verify fix

### 6. Expected Behavior After Fix

1. **Initial Load**: Folder shows "Scanning..." instead of "No video files"
2. **During Scan**: Checkbox is disabled, preventing user interaction
3. **Scan Complete**: Folder shows actual video count and checkbox becomes functional
4. **User Click**: Checkbox works properly after scan completes
5. **No Race Conditions**: Multiple clicks don't trigger multiple scans

### 7. Testing Scenarios

1. **DCIM Folder Test**:
   - Load folder with subfolders but no immediate video files
   - Navigate into folder and back
   - Click checkbox after scan completes
   - Verify selection works properly

2. **Large Folder Test**:
   - Test with folders containing many subfolders
   - Verify scanning progress indicators work
   - Ensure UI remains responsive during scanning

3. **Multiple Clicks Test**:
   - Click checkbox multiple times during scan
   - Verify only one scan is triggered
   - Ensure proper state management