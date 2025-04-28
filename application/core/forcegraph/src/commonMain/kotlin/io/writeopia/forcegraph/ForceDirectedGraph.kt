package io.writeopia.forcegraph

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node

@Composable
fun <T> ForceDirectedGraph(
    modifier: Modifier = Modifier,
    links: List<Link<T>>,
    nodes: List<Node<T>>,
    getLabel: (T) -> String
) {
    val textMeasurer = rememberTextMeasurer()

    val textStyle =
        MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground)

    Canvas(
        modifier = modifier
//        .pointerInput(Unit) {
//            var draggingNode: Node? = null
//
//            detectDragGestures(
//                onDragStart = { offset ->
//                    // Find if clicked on a node
//                    draggingNode = nodes.find { node ->
//                        val dx = node.x - offset.x
//                        val dy = node.y - offset.y
//                        sqrt(dx * dx + dy * dy) < 20f // 20px radius hit area
//                    }?.also {
//                        println("isDragged")
//                        it.isDragged = true
//                    }
//                },
//                onDragEnd = {
//                    draggingNode?.isDragged = false
//                    draggingNode = null
//                },
//                onDragCancel = {
//                    draggingNode?.isDragged = false
//                    draggingNode = null
//                },
//                onDrag = { change, dragAmount ->
//                    draggingNode?.let { node ->
//                        node.x += dragAmount.x
//                        node.y += dragAmount.y
//                        node.vx = 0f
//                        node.vy = 0f
//                    }
//                }
//            )
//        }
    ) {
        // Draw links
        links.forEach { link ->
            drawLine(
                color = Color.LightGray,
                start = Offset(link.source.x, link.source.y),
                end = Offset(link.target.x, link.target.y),
                strokeWidth = 2f
            )
        }

        // Draw nodes
        nodes.forEach { node ->
            drawCircle(
                color = if (node.isDragged) Color.Red else Color(0xFF2196F3),
                center = Offset(node.x, node.y),
                radius = 8f
            )

            drawText(
                textMeasurer,
                text = getLabel(node.data),
                topLeft = Offset(node.x, node.y + 8F),
                style = textStyle
            )
        }
    }
}
