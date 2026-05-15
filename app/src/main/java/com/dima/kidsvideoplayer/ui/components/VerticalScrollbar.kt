package com.dima.kidsvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast

/**
 * Vertical scrollbar indicator for LazyColumn.
 * Shows a thumb whose size and position reflect the current scroll state.
 *
 * @param state The LazyListState of the associated LazyColumn
 * @param modifier Modifier for positioning (use inside BoxScope)
 * @param thumbColor Color of the scrollbar thumb
 * @param thumbWidth Width of the thumb
 */
@Composable
fun BoxScope.VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White.copy(alpha = 0.3f),
    thumbWidth: Dp = 4.dp
) {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItemsCount = layoutInfo.visibleItemsInfo.size

    // Don't show scrollbar if there's nothing to scroll
    if (totalItems == 0 || visibleItemsCount >= totalItems) return

    BoxWithConstraints(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(thumbWidth + 4.dp)
    ) {
        val trackHeight = maxHeight
        val thumbFraction = visibleItemsCount.toFloat() / totalItems
        val thumbHeight = (trackHeight * thumbFraction).coerceAtLeast(16.dp)

        val firstIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        val maxIndex = (totalItems - visibleItemsCount).coerceAtLeast(1)
        val scrollFraction = firstIndex.toFloat() / maxIndex

        val scrollRange = trackHeight - thumbHeight
        val thumbOffset = scrollRange * scrollFraction

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .offset(y = thumbOffset)
                .background(thumbColor, RoundedCornerShape(2.dp))
        )
    }
}
