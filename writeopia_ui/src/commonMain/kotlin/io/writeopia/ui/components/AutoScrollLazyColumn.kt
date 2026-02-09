package io.writeopia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.ui.draganddrop.target.DragTargetInfo
import io.writeopia.ui.draganddrop.target.LocalDragTargetInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * A LazyColumn that automatically scrolls when the user is dragging an item
 * and approaches the top or bottom edges.
 *
 * @param modifier The modifier to be applied to the LazyColumn
 * @param state The LazyListState used to control scrolling
 * @param contentPadding Padding around the content
 * @param edgeThreshold The distance from the edge at which auto-scrolling begins
 * @param scrollSpeed The speed at which to scroll (pixels per frame)
 * @param content The LazyListScope content builder
 */
@Composable
fun AutoScrollLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    edgeThreshold: Dp = 80.dp,
    scrollSpeed: Float = 15f,
    content: LazyListScope.() -> Unit
) {
    val dragInfo: DragTargetInfo = LocalDragTargetInfo.current

    var columnTop by remember { mutableStateOf(0f) }
    var columnBottom by remember { mutableStateOf(0f) }

    AutoScrollEffect(
        dragInfo = dragInfo,
        listState = state,
        columnTop = columnTop,
        columnBottom = columnBottom,
        edgeThreshold = edgeThreshold,
        scrollSpeed = scrollSpeed
    )

    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            columnTop = bounds.top
            columnBottom = bounds.bottom
        }
    ) {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun AutoScrollEffect(
    dragInfo: DragTargetInfo,
    listState: LazyListState,
    columnTop: Float,
    columnBottom: Float,
    edgeThreshold: Dp,
    scrollSpeed: Float
) {
    LaunchedEffect(dragInfo, listState, columnTop, columnBottom, edgeThreshold, scrollSpeed) {
        snapshotFlow {
            AutoScrollData(
                isDragging = dragInfo.isDragging,
                dragY = (dragInfo.dragPosition + dragInfo.dragOffset).y
            )
        }.collectLatest { data ->
            if (data.isDragging && columnBottom > columnTop) {
                val thresholdPx = edgeThreshold.value * 2.5f // Approximate px conversion

                while (isActive && dragInfo.isDragging) {
                    val currentDragY = (dragInfo.dragPosition + dragInfo.dragOffset).y
                    val distanceFromTop = currentDragY - columnTop
                    val distanceFromBottom = columnBottom - currentDragY

                    val scrollAmount = when {
                        distanceFromTop < thresholdPx -> {
                            // Near top - scroll up (negative)
                            // Intensity goes from 0 (at threshold) to 1 (at edge)
                            val intensity = 1f - (distanceFromTop / thresholdPx).coerceIn(0f, 1f)
                            // Accelerate: scroll much faster when very close to the edge
                            val accelerated = intensity * (1f + intensity * intensity * 4f)
                            -scrollSpeed * accelerated
                        }
                        distanceFromBottom < thresholdPx -> {
                            // Near bottom - scroll down (positive)
                            val intensity = 1f - (distanceFromBottom / thresholdPx).coerceIn(0f, 1f)
                            val accelerated = intensity * (1f + intensity * intensity * 4f)
                            scrollSpeed * accelerated
                        }
                        else -> 0f
                    }

                    if (scrollAmount != 0f) {
                        listState.scrollBy(scrollAmount)
                    }

                    delay(16) // ~60fps
                }
            }
        }
    }
}

private data class AutoScrollData(
    val isDragging: Boolean,
    val dragY: Float
)

private suspend fun LazyListState.scrollBy(amount: Float) {
    dispatchRawDelta(amount)
}
