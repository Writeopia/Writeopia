package io.writeopia.drawing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.drawing.DrawingTool

@Composable
fun DrawingToolbar(
    currentTool: DrawingTool,
    currentColor: Int,
    strokeWidth: Float,
    canUndo: Boolean,
    colors: List<Int>,
    strokeWidths: List<Float>,
    onToolSelected: (DrawingTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthSelected: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // Tool selection row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = { Icon(WrIcons.file, contentDescription = "Pen", tint = it) },
                label = "Pen",
                isSelected = currentTool == DrawingTool.PEN,
                onClick = { onToolSelected(DrawingTool.PEN) }
            )

            ToolButton(
                icon = { Icon(WrIcons.highlight, contentDescription = "Highlighter", tint = it) },
                label = "Highlight",
                isSelected = currentTool == DrawingTool.HIGHLIGHTER,
                onClick = { onToolSelected(DrawingTool.HIGHLIGHTER) }
            )

            ToolButton(
                icon = { Icon(WrIcons.transparent, contentDescription = "Eraser", tint = it) },
                label = "Eraser",
                isSelected = currentTool == DrawingTool.ERASER,
                onClick = { onToolSelected(DrawingTool.ERASER) }
            )

            Spacer(modifier = Modifier.width(16.dp))

            ToolButton(
                icon = { Icon(WrIcons.undo, contentDescription = "Undo", tint = it) },
                label = "Undo",
                isSelected = false,
                enabled = canUndo,
                onClick = onUndo
            )

            ToolButton(
                icon = { Icon(WrIcons.delete, contentDescription = "Clear", tint = it) },
                label = "Clear",
                isSelected = false,
                onClick = onClear
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color palette
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { color ->
                ColorButton(
                    color = color,
                    isSelected = currentColor == color,
                    onClick = { onColorSelected(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stroke width selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            strokeWidths.forEach { width ->
                StrokeWidthButton(
                    strokeWidth = width,
                    isSelected = strokeWidth == width,
                    currentColor = currentColor,
                    onClick = { onStrokeWidthSelected(width) }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(modifier = Modifier.size(24.dp)) {
            icon(contentColor)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
private fun ColorButton(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }

    val borderWidth = if (isSelected) 3.dp else 1.dp

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun StrokeWidthButton(
    strokeWidth: Float,
    isSelected: Boolean,
    currentColor: Int,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(strokeWidth.dp.coerceAtLeast(4.dp))
                .clip(CircleShape)
                .background(Color(currentColor))
        )
    }
}
