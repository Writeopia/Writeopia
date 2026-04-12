package io.writeopia.drawing.ui.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.DrawingTool
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawInfo
import kotlinx.serialization.json.Json

/**
 * Drawer that renders a saved drawing (stored as JSON in StoryStep.text) as a preview.
 */
class DrawingPreviewDrawer(
    private val onDrawingClick: (StoryStep, Int) -> Unit,
    private val onDelete: (Action.DeleteStory) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val drawingData = remember(step.text) {
            step.text?.let { json ->
                try {
                    Json.decodeFromString(DrawingData.serializer(), json)
                } catch (e: Exception) {
                    null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (drawingData != null && drawingData.strokes.isNotEmpty()) {
                DrawingPreview(
                    drawingData = drawingData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onDrawingClick(step, drawInfo.position) }
                )

                // Delete button
                Icon(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(WriteopiaTheme.colorScheme.lightBackground)
                        .clickable {
                            onDelete(Action.DeleteStory(step, drawInfo.position))
                        }
                        .padding(4.dp),
                    imageVector = WrSdkIcons.close,
                    contentDescription = "Delete drawing",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            } else {
                // Empty or invalid drawing placeholder
                EmptyDrawingPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDrawingClick(step, drawInfo.position) }
                )
            }
        }
    }
}

@Composable
private fun DrawingPreview(
    drawingData: DrawingData,
    modifier: Modifier = Modifier
) {
    val canvasBg = MaterialTheme.colorScheme.background
    Canvas(
        modifier = modifier.background(canvasBg)
    ) {
        // Calculate scale to fit the drawing in the preview
        val scaleX = if (drawingData.width > 0) size.width / drawingData.width else 1f
        val scaleY = if (drawingData.height > 0) size.height / drawingData.height else 1f
        val scale = minOf(scaleX, scaleY)

        // Center the drawing
        val offsetX = (size.width - drawingData.width * scale) / 2
        val offsetY = (size.height - drawingData.height * scale) / 2

        drawingData.strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                val path = Path().apply {
                    stroke.points.forEachIndexed { index, point ->
                        val x = point.x * scale + offsetX
                        val y = point.y * scale + offsetY
                        if (index == 0) {
                            moveTo(x, y)
                        } else {
                            lineTo(x, y)
                        }
                    }
                }

                val strokeStyle = when (stroke.tool) {
                    DrawingTool.HIGHLIGHTER -> ComposeStroke(
                        width = stroke.strokeWidth * scale * 3,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    else -> ComposeStroke(
                        width = stroke.strokeWidth * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                }

                val strokeColor = when (stroke.tool) {
                    DrawingTool.HIGHLIGHTER -> Color(stroke.color).copy(alpha = 0.4f)
                    DrawingTool.ERASER -> canvasBg
                    else -> Color(stroke.color)
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = strokeStyle
                )
            }
        }
    }
}

@Composable
private fun EmptyDrawingPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = WrSdkIcons.edit,
            contentDescription = "Empty drawing",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
