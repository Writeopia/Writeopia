package io.writeopia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.ui.draganddrop.target.DragTargetInfo
import io.writeopia.ui.draganddrop.target.LocalDragTargetInfo

@Composable
fun AutoScrollLazyVerticalStaggeredGrid(
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalItemSpacing: Dp = 0.dp,
    edgeThreshold: Dp = 80.dp,
    scrollSpeed: Float = 15f,
    content: LazyStaggeredGridScope.() -> Unit
) {
    val dragInfo: DragTargetInfo = LocalDragTargetInfo.current

    var columnTop by remember { mutableStateOf(0f) }
    var columnBottom by remember { mutableStateOf(0f) }

    AutoScrollEffect(
        dragInfo = dragInfo,
        scrollableState = state,
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
        LazyVerticalStaggeredGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalItemSpacing = verticalItemSpacing,
            content = content
        )
    }
}
