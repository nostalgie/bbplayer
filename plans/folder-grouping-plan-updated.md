# Plan: Group Files by Folders in Parent Dashboard (Updated)

## Goal
In the parent dashboard (`ParentDashboardScreen`), group the flat video list by folders. Show folder headers with abbreviated storage volume names (SD1, SD2, VOL1, etc.) and full hierarchical paths. Files are displayed without indentation as before.

## Updated Visual Layout

```
SD1/Мультики/Богатыри
-- Мультик 1.avi
-- Мультик 2.avi
SD2/Видео
-- Фильм 1.mp4
VOL1/Контент
-- Видео 3.mkv
```

## Key Changes
- **Storage Volume Abbreviation**: 
  - `/storage/emulated/0/` → `SD1/`
  - `/storage/XXXX-XXXX/` → `SD2/` (SD cards)
  - `/mnt/media/0/` → `VOL1/` (other volumes)
- **Full Path Display**: Shows complete hierarchical path like `SD1/Мультики/Богатыри`
- **File Display**: No indentation, same as current behavior

## Implementation Details

### 1. Enhanced Folder Extraction
Updated `extractFolderInfo()` to use `abbreviateFolderPath()`:

```kotlin
private fun extractFolderInfo(uriString: String): Pair<String, String>? {
    val uri = Uri.parse(uriString)
    return when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: return null
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) {
                val folderPath = path.substring(0, lastSlash)
                val fileName = uri.lastPathSegment.orEmpty()
                val displayPath = abbreviateFolderPath(folderPath)
                displayPath to fileName
            } else null
        }
        "content" -> {
            // Similar logic with URL decoding
            val displayPath = abbreviateFolderPath(folder)
            displayPath to fileName
        }
        else -> null
    }
}
```

### 2. Path Abbreviation Logic
New `abbreviateFolderPath()` function:

```kotlin
private fun abbreviateFolderPath(fullPath: String): String {
    val storagePattern = Regex("""^/storage/([^/]+)(?:/(.+))?$""")
    val matchResult = storagePattern.find(fullPath)
    
    return if (matchResult != null) {
        val volumeName = matchResult.groupValues[1]
        val relativePath = matchResult.groupValues[2].takeIf { it.isNotEmpty() }
        
        val abbreviatedVolume = when (volumeName) {
            "emulated" -> "SD1"  // Internal storage
            else -> {
                if (volumeName.matches(Regex("""^[A-F0-9]{4}-[A-F0-9]{4}$"""))) {
                    "SD${volumeName.take(2)}"  // SD2, SD3, etc.
                } else {
                    "VOL${volumeName.take(2)}"  // VOL1, VOL2, etc.
                }
            }
        }
        
        if (relativePath != null) {
            "$abbreviatedVolume/$relativePath"
        } else {
            abbreviatedVolume
        }
    } else {
        // Fallback for non-standard paths
        val parts = fullPath.split("/").filter { it.isNotEmpty() }
        when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> {
                val volume = parts[0].take(4)
                val pathParts = parts.subList(1, parts.size)
                "$volume/${pathParts.joinToString("/")}"
            }
        }
    }
}
```

### 3. Examples of Path Conversion

| Original Path | Abbreviated Path |
|---------------|------------------|
| `/storage/emulated/0/Movies` | `SD1/Movies` |
| `/storage/emulated/0/Movies/Animation` | `SD1/Movies/Animation` |
| `/storage/1234-5678/Video` | `SD12/Video` |
| `/storage/ABCD-EFGH/Films/Action` | `SDAB/Films/Action` |
| `/mnt/media/0/Movies` | `VOL1/Movies` |
| `/mnt/media/0/Movies/Animation` | `VOL1/Movies/Animation` |

## Files Modified

| File | Changes |
|------|---------|
| `ParentDashboardScreen.kt` | Updated `extractFolderInfo()` and added `abbreviateFolderPath()` |
| `plans/folder-grouping-plan-updated.md` | This updated documentation |

## Testing Strategy

1. **URI Testing**: Test with both `file://` and `content://` schemes
2. **Storage Testing**: Test with internal storage, SD cards, and other volumes
3. **Path Testing**: Test various nesting levels and path structures
4. **Edge Cases**: Test empty paths, malformed URIs, and non-standard storage paths

## Benefits

- ✅ **Shorter Paths**: Uses abbreviated storage names instead of long volume names
- ✅ **Clear Organization**: Shows complete folder hierarchy
- ✅ **Consistent Naming**: Standardized naming for all storage types
- ✅ **Backward Compatible**: Maintains all existing functionality
- ✅ **Flexible**: Handles various storage volume formats