package io.writeopia.drawing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.input.pointer.pointerInput
import io.writeopia.sdk.models.drawing.DrawPoint
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.drawing.Stroke
import io.writeopia.sdk.models.id.GenerateId
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@Composable
actual fun DrawingCanvas(
    modifier: Modifier,
    strokes: List<Stroke>,
    currentTool: DrawingTool,
    currentColor: Int,
    strokeWidth: Float,
    onStrokeAdded: (Stroke) -> Unit
) {
    var currentPoints by remember { mutableStateOf<List<DrawPoint>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .background(Color.White)
            .pointerInput(currentTool, currentColor, strokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDrawing = true
                        currentPoints = listOf(
                            DrawPoint(
                                x = offset.x,
                                y = offset.y,
                                timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPoints = currentPoints + DrawPoint(
                            x = change.position.x,
                            y = change.position.y,
                            timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                        )
                    },
                    onDragEnd = {
                        isDrawing = false
                        if (currentPoints.isNotEmpty()) {
                            val stroke = Stroke(
                                id = GenerateId.generate(),
                                points = currentPoints,
                                color = currentColor,
                                strokeWidth = strokeWidth,
                                tool = currentTool
                            )
                            onStrokeAdded(stroke)
                            currentPoints = emptyList()
                        }
                    },
                    onDragCancel = {
                        isDrawing = false
                        currentPoints = emptyList()
                    }
                )
            }
    ) {
        // Draw completed strokes
        strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                val path = Path().apply {
                    stroke.points.forEachIndexed { index, point ->
                        if (index == 0) {
                            moveTo(point.x, point.y)
                        } else {
                            lineTo(point.x, point.y)
                        }
                    }
                }

                val strokeStyle = when (stroke.tool) {
                    DrawingTool.HIGHLIGHTER -> ComposeStroke(
                        width = stroke.strokeWidth * 3,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    else -> ComposeStroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                }

                val strokeColor = when (stroke.tool) {
                    DrawingTool.HIGHLIGHTER -> Color(stroke.color).copy(alpha = 0.4f)
                    DrawingTool.ERASER -> Color.White
                    else -> Color(stroke.color)
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = strokeStyle
                )
            }
        }

        // Draw current stroke being drawn
        if (currentPoints.size > 1) {
            val path = Path().apply {
                currentPoints.forEachIndexed { index, point ->
                    if (index == 0) {
                        moveTo(point.x, point.y)
                    } else {
                        lineTo(point.x, point.y)
                    }
                }
            }

            val strokeStyle = when (currentTool) {
                DrawingTool.HIGHLIGHTER -> ComposeStroke(
                    width = strokeWidth * 3,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                else -> ComposeStroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            }

            val strokeColor = when (currentTool) {
                DrawingTool.HIGHLIGHTER -> Color(currentColor).copy(alpha = 0.4f)
                DrawingTool.ERASER -> Color.White
                else -> Color(currentColor)
            }

            drawPath(
                path = path,
                color = strokeColor,
                style = strokeStyle
            )
        }
    }
}
