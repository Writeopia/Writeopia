package io.writeopia.drawing.model

import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.DrawingTool

data class DrawingState(
    val drawingData: DrawingData = DrawingData(),
    val currentTool: DrawingTool = DrawingTool.PEN,
    val currentColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 4f,
    val canUndo: Boolean = false,
    val isLoading: Boolean = false
)
