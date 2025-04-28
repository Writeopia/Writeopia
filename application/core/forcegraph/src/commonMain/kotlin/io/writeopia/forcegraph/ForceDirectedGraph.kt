package io.writeopia.forcegraph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node

@Composable
fun ForceDirectedGraph(modifier: Modifier = Modifier) {
    val nodes = remember { mutableStateListOf<Node>() }
    val links = remember { mutableStateListOf<Link>() }

    // Initialize nodes and links
    LaunchedEffect(Unit) {
        repeat(10) {
            nodes += Node(
                x = (100..800).random().toFloat(),
                y = (100..600).random().toFloat()
            )
        }
        // Randomly link some nodes
        repeat(15) {
            val source = nodes.random()
            val target = nodes.random()
            if (source != target) {
                links += Link(source, target)
            }
        }
    }

    // Physics animation loop
    LaunchedEffect(nodes) {
//        while (isActive) {
//            tick(nodes, links, dt = 0.016f) // ~60fps
//            delay(16)
//        }
    }

    Canvas(modifier = modifier
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
        }
    }
}
