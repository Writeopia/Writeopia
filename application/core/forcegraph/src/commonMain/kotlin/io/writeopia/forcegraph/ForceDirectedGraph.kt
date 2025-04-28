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

    Canvas(modifier = modifier) {
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
            if (node.isFolder) {
                drawCircle(
                    color = if (node.isDragged) Color.Red else Color.Gray,
                    center = Offset(node.x, node.y),
                    radius = 12f
                )

                drawText(
                    textMeasurer,
                    text = getLabel(node.data),
                    topLeft = Offset(node.x, node.y + 8F),
                    style = textStyle
                )
            } else {
                drawCircle(
                    color = if (node.isDragged) Color.Red else Color(0xFF2196F3),
                    center = Offset(node.x, node.y),
                    radius = 6f
                )
            }
        }
    }
}
