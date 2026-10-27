package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
fun DropTarget(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(inBound: Boolean, data: DropInfo?) -> Unit,
) {
    val dragInfo: DragTargetInfo = LocalDragTargetInfo.current
    val dragPosition = dragInfo.dragPosition
    val dragOffset = dragInfo.dragOffset
    var isCurrentDropTarget by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            layoutCoordinates.boundsInWindow().let { rect ->
                if (dragInfo.isDragging) {
                    isCurrentDropTarget = rect.contains(dragPosition + dragOffset)
                }
            }
        }
    ) {
        val data = if (isCurrentDropTarget && !dragInfo.isDragging) dragInfo.dataToDrop else null

        if (data != null) {
            dragInfo.dataToDrop = null
        }

        content(isCurrentDropTarget, data)

        if (data != null) {
            isCurrentDropTarget = false
        }
    }
}

/**
 * The data being dragged, while a drag is in progress. [DropTarget] only hands the data over when
 * it is dropped, so targets that react to what hovers over them read it from here.
 */
@Composable
fun draggedData(): DropInfo? {
    val dragInfo = LocalDragTargetInfo.current
    return if (dragInfo.isDragging) dragInfo.dataToDrop else null
}
