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
fun DropTargetVerticalDivision(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(inBound: InBounds, data: DropInfo?) -> Unit,
) {
    val dragInfo: DragTargetInfo = LocalDragTargetInfo.current
    val dragPosition = dragInfo.dragPosition
    val dragOffset = dragInfo.dragOffset
    var isCurrentDropTarget by remember { mutableStateOf(InBounds.OUTSIDE) }

    Box(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            layoutCoordinates.boundsInWindow().let { rect ->
                val position = dragPosition + dragOffset
                if (dragInfo.isDragging) {
                    isCurrentDropTarget = if (rect.contains(position)) {
                        if (position.y >= rect.top + rect.height / 2) {
                            InBounds.INSIDE_DOWN
                        } else {
                            InBounds.INSIDE_UP
                        }
                    } else {
                        InBounds.OUTSIDE
                    }
                }
            }
        }
    ) {
        val data = if (isCurrentDropTarget.isInside() && !dragInfo.isDragging) {
            dragInfo.dataToDrop
        } else {
            null
        }

        if (data != null) {
            dragInfo.dataToDrop = null
        }

        content(isCurrentDropTarget, data)

        if (data != null) {
            isCurrentDropTarget = InBounds.OUTSIDE
        }
    }
}

enum class InBounds {
    OUTSIDE, INSIDE_UP, INSIDE_DOWN;

    fun isInside() = this == INSIDE_UP || this == INSIDE_DOWN
}
