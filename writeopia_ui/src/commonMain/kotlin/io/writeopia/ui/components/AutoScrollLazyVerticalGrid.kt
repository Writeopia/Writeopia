package io.writeopia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
fun AutoScrollLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    edgeThreshold: Dp = 80.dp,
    scrollSpeed: Float = 15f,
    content: LazyGridScope.() -> Unit
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
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
