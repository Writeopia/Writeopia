package io.writeopia.ui.components.multiselection

data class DragSelectionInfo(
    val selectionBox: SelectionBox? = null,
    val isDragging: Boolean = false
)

data class SelectionBox(
    val x: Float = 0F,
    val y: Float = 0F,
    val width: Float = 0F,
    val height: Float = 0F
)
