package io.writeopia.ui.draganddrop.target

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.writeopia.sdk.model.draganddrop.DropInfo

internal class DragTargetInfo {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)

    /**
     * Window position of the top-left corner of the dragged element when the drag started. When
     * set, the floating copy is drawn from it so it starts exactly over the original element,
     * while [dragPosition] keeps tracking the pointer for drop detection.
     */
    var ghostPosition by mutableStateOf<Offset?>(null)
    var draggableComposable by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<DropInfo?>(null)
}
