package io.writeopia.ui.components.multiselection

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

fun doBoxesOverlap(box1: SelectionBox, box2: SelectionBox): Boolean = box1.x < box2.x + box2.width &&
    box1.x + box1.width > box2.x &&
    box1.y < box2.y + box2.height &&
    box1.y + box1.height > box2.y

@Composable
fun SelectableByDrag(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(inBound: Boolean?) -> Unit
) {
    val dragInfo: DragSelectionInfo = LocalDragSelectionInfo.current
    var isSelecting by remember { mutableStateOf<Boolean?>(null) }

    Box(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            layoutCoordinates.boundsInWindow().let { rect ->
                val selection = dragInfo.selectionBox
                if (dragInfo.isDragging && selection != null) {
                    val inside = doBoxesOverlap(
                        selection,
                        SelectionBox(rect.left, rect.top, rect.width, rect.height)
                    )

                    isSelecting = inside
                }
            }
        }
    ) {
        content(isSelecting)
    }
}
