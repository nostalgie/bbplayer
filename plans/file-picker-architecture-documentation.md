# File Picker Architecture Documentation

## Two-Phase Selection Process

### Overview
The File Picker implements a two-phase selection process to improve user experience and performance:

1. **Phase 1: Отбор файлов** (Preliminary File Selection)
2. **Phase 2: Подтверждение добавления** (Confirmation of Addition)

### Phase 1: Отбор файлов (Preliminary File Selection)

**Definition**: The initial phase where users browse through directories and temporarily select files for potential addition to the video library.

**Characteristics**:
- **Purpose**: Allow users to quickly browse and mark files without immediate processing
- **Performance**: Lightweight selection with minimal compatibility checks
- **State Management**: Temporary selection stored in `preliminarySelection`
- **UI Feedback**: Real-time visual indicators for selected items
- **Cancellation**: Easy to modify or cancel selections

**Implementation Details**:
```kotlin
data class FileSelectionState(
    val preliminarySelection: Set<String> = emptySet(),  // Отбор файлов
    val confirmedSelection: Set<String> = emptySet()     // Подтвержденное добавление
)
```

**Performance Optimizations**:
- Deferred compatibility checking
- Virtual scrolling for large directories
- Background preloading of folder metadata
- Minimal UI recomposition during selection

### Phase 2: Подтверждение добавления (Confirmation of Addition)

**Definition**: The final phase where users confirm their preliminary selections and the system processes the files for permanent addition to the video library.

**Characteristics**:
- **Purpose**: Process and permanently add selected files to the library
- **Performance**: Comprehensive compatibility checking and metadata processing
- **State Management**: Final selection stored in `confirmedSelection`
- **UI Feedback**: Progress indicators and batch processing
- **Irreversibility**: Once confirmed, files are permanently added

**Implementation Details**:
```kotlin
// In ParentDashboardScreen
val confirmedFiles = preliminarySelection.filter { path ->
    compatibilityCache.value[path]?.isFullySupported == true
}
```

**Performance Optimizations**:
- Batch compatibility checking
- Parallel file processing
- Memory-efficient metadata storage
- Progress tracking for large operations

## Performance Architecture

### Core Components

#### 1. Virtual Scrolling System
- **Purpose**: Handle large file lists without UI freezing
- **Implementation**: Optimized LazyColumn with key-based item identification
- **Benefits**: Smooth scrolling, reduced memory usage, fast initial load

#### 2. Non-Blocking Directory Loading
- **Purpose**: Prevent UI freezing during directory scanning
- **Implementation**: Coroutine-based loading with proper cancellation
- **Benefits**: Responsive UI, background processing, user feedback

#### 3. Efficient Caching Strategy
- **Purpose**: Reduce redundant file system operations
- **Implementation**: LRU cache for file metadata and compatibility results
- **Benefits**: Faster subsequent loads, reduced I/O operations

#### 4. Batch Processing
- **Purpose**: Optimize compatibility checking and file processing
- **Implementation**: Process files in chunks rather than individually
- **Benefits**: Better memory usage, faster processing, parallel execution

### Performance Metrics

| Component | Current Performance | Target Performance | Improvement |
|-----------|-------------------|-------------------|-------------|
| UI Response Time | 5-10 seconds | < 500ms | 90-95% |
| Memory Usage | 100MB+ | < 50MB | 50%+ |
| Directory Loading | 3-8 seconds | < 1 second | 70-90% |
| Scrolling Performance | Laggy | Smooth | 80-95% |
| Compatibility Checks | Sequential | Batched | 60-80% faster |

## State Management

### File Selection States
```kotlin
sealed class SelectionState {
    data class Preliminary(
        val selectedFiles: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val progress: Float = 0f
    ) : SelectionState()
    
    data class Confirmed(
        val files: List<String> = emptyList(),
        val processing: Boolean = false,
        val progress: Float = 0f
    ) : SelectionState()
    
    object Idle : SelectionState()
}
```

### Loading States
```kotlin
sealed class LoadingState {
    object Idle : LoadingState()
    object Loading : LoadingState()
    data class Loaded(val items: List<FileSystemItem>) : LoadingState()
    data class Error(val message: String) : LoadingState()
}
```

## UI Components

### FilePickerScreen Components
- **OptimizedFileList**: Virtual scrolling list with minimal recomposition
- **FolderItemOptimized**: Enhanced folder item with loading states
- **VideoFileItemOptimized**: Optimized file item with compatibility indicators
- **LoadingIndicator**: Progress feedback for long operations

### ParentDashboardScreen Components
- **SelectionSummary**: Display confirmed selections with progress
- **BatchProcessor**: Handle batch file processing
- **ErrorHandling**: Graceful error management for failed operations

## Implementation Guidelines

### Code Organization
1. **Separate concerns** between preliminary and confirmed selection phases
2. **Use coroutines** for all background operations
3. **Implement proper cancellation** for long-running tasks
4. **Add comprehensive logging** for performance monitoring

### Performance Best Practices
1. **Debounce rapid user interactions** to prevent excessive operations
2. **Use lazy loading** for large directory structures
3. **Implement pagination** for recursive folder scanning
4. **Cache frequently accessed data** to reduce I/O operations

### Testing Requirements
1. **Test with 100+ video files** to validate performance improvements
2. **Measure UI responsiveness** during operations
3. **Validate memory usage** with large directories
4. **Test edge cases** and error scenarios

## References

- **FilePickerScreen.kt**: Main file picker implementation
- **FileSystemService.kt**: File system operations and caching
- **PerformanceMonitor.kt**: Performance tracking and metrics
- **FilePickerComponents.kt**: UI components for file selection

## Future Enhancements

1. **Smart caching** based on user behavior patterns
2. **Background preloading** of frequently accessed folders
3. **Adaptive loading** based on device capabilities
4. **Advanced error handling** for corrupted files
5. **User preference learning** for selection patterns

---

*This document serves as the authoritative reference for the File Picker architecture and should be updated as the system evolves.*