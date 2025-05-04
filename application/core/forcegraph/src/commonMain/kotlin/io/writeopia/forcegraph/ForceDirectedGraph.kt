package io.writeopia.forcegraph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sqrt

class Node(
    val id: String,
    val label: String,
    initialX: Float,
    initialY: Float,
    initialVx: Float = 0f,
    initialVy: Float = 0f,
    val isFolder: Boolean,
    selected: Boolean
) {
    var x by mutableStateOf(initialX)
    var y by mutableStateOf(initialY)
    var vx by mutableStateOf(initialVx)
    var vy by mutableStateOf(initialVy)
    var isDragged by mutableStateOf(false)
    var showName by mutableStateOf(selected || isFolder)
}

data class Link(
    val source: Node,
    val target: Node,
)

@Composable
fun BoxWithConstraintsScope.ForceDirectedGraph(
    nodes: List<Node>,
    links: List<Link>,
    onNodeSelected: (String) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = Color.White,
            background = Color.Blue,
            fontSize = 10.sp
        )

    val nodeRadius = if (nodes.size < 15) (25 - nodes.size).toFloat() else 8f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(nodes) {
                detectTapGestures(
                    onTap = { offset ->
                        nodes.find { node ->
                            val dx = node.x - offset.x
                            val dy = node.y - offset.y

                            sqrt(dx * dx + dy * dy) < 20F
                        }?.also {
                            onNodeSelected(it.id)
                        }
                    }
                )
            }
            .pointerInput(nodes) {
                var draggingNode: Node? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingNode = nodes.find { node ->
                            val dx = node.x - offset.x
                            val dy = node.y - offset.y

                            sqrt(dx * dx + dy * dy) < 12f
                        }?.also {
                            it.isDragged = true
                        }
                    },
                    onDragEnd = {
                        draggingNode?.isDragged = false
                        draggingNode = null
                    },
                    onDragCancel = {
                        draggingNode?.isDragged = false
                        draggingNode = null
                    },
                    onDrag = { _, dragAmount ->
                        draggingNode?.let { node ->
                            node.x += dragAmount.x
                            node.y += dragAmount.y
                            node.vx = 0f
                            node.vy = 0f
                        }
                    }
                )
            }
    ) {
        links.forEach { link ->
            drawLine(
                color = Color.LightGray,
                start = Offset(
                    min(link.source.x, maxWidth.value),
                    min(link.source.y, maxHeight.value)
                ),
                end = Offset(
                    min(link.target.x, maxWidth.value),
                    min(link.target.y, maxHeight.value)
                ),
                strokeWidth = 2f
            )
        }

        val nodeColor: (Boolean) -> Color = { isFolder ->
            if (isFolder) {
                Color(0xFF2196F3)
            } else {
                Color.Gray
            }
        }

        nodes.forEach { node ->
            drawCircle(
                color = if (node.isDragged) Color.Red else nodeColor(node.isFolder),
                center = Offset(min(node.x, maxWidth.value), min(node.y, maxHeight.value)),
                radius = if (node.isFolder) nodeRadius else nodeRadius / 2
            )
        }

        nodes.forEach { node ->
            if (node.showName) {
                val x = node.x
                val y = node.y

                if (x >= 0 && y >= 0 && x <= maxWidth.value && y <= maxHeight.value) {
                    drawText(
                        textMeasurer,
                        text = " ${node.label} ",
                        topLeft = Offset(node.x - 30, node.y + 20),
                        style = textStyle
                    )
                }
            }
        }
    }
}
