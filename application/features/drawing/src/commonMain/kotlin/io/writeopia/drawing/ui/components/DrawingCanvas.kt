package io.writeopia.drawing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.drawing.Stroke

@Composable
expect fun DrawingCanvas(
    modifier: Modifier = Modifier,
    strokes: List<Stroke>,
    currentTool: DrawingTool,
    currentColor: Int,
    strokeWidth: Float,
    onStrokeAdded: (Stroke) -> Unit
)
