package io.writeopia.forcegraph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

class Node(
    val id: String,
    val label: String,
    initialX: Float,
    initialY: Float,
    initialVx: Float = 0f,
    initialVy: Float = 0f,
    val isFolder: Boolean,
) {
    var x by mutableStateOf(initialX)
    var y by mutableStateOf(initialY)
    var vx by mutableStateOf(initialVx)
    var vy by mutableStateOf(initialVy)
    var isDragged by mutableStateOf(false)
}

data class Link(
    val source: Node,
    val target: Node,
)

@Composable
fun BoxWithConstraintsScope.ForceDirectedGraph(nodes: List<Node>, links: List<Link>) {
    // Physics animation loop
    LaunchedEffect(nodes, links) {
        while (isActive) {
            tick(nodes, links, dt = 0.016f) // ~60fps
            delay(16) // Changed from 200 to 16 for smoother animation
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val textStyle =
        MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
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
                    onDrag = { change, dragAmount ->
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
        // Draw links
        links.forEach { link ->
            drawLine(
                color = Color.LightGray,
                start = Offset(link.source.x, link.source.y),
                end = Offset(link.target.x, link.target.y),
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

        // Draw nodes
        nodes.forEach { node ->
            drawCircle(
                color = if (node.isDragged) Color.Red else nodeColor(node.isFolder),
                center = Offset(node.x, node.y),
                radius = if (node.isFolder) 12f else 6f
            )

//            if (node.isFolder) {
//                drawText(
//                    textMeasurer,
//                    text = node.label,
//                    topLeft = Offset(node.x, node.y),
//                    style = textStyle
//                )
//            }
        }
    }
}

fun BoxWithConstraintsScope.tick(nodes: List<Node>, links: List<Link>, dt: Float) {
    applyLinkForce(links)
    applyChargeForce(nodes)
    applyCenteringForce(nodes)
    updatePositions(nodes, dt)
}

fun applyLinkForce(links: List<Link>) {
    val linkDistance = 150f
    val strength = 0.15f

    for (link in links) {
        val dx = link.target.x - link.source.x
        val dy = link.target.y - link.source.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val force = (distance - linkDistance) * strength
        val fx = force * dx / distance
        val fy = force * dy / distance

        link.source.vx += fx
        link.source.vy += fy
        link.target.vx -= fx
        link.target.vy -= fy
    }
}

fun applyChargeForce(nodes: List<Node>) {
    val chargeStrength = 3000f // Reduced from 5000f

    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val nodeA = nodes[i]
            val nodeB = nodes[j]
            val dx = nodeB.x - nodeA.x
            val dy = nodeB.y - nodeA.y
            val distanceSq = (dx * dx + dy * dy).coerceAtLeast(0.01f)
            val force = chargeStrength / distanceSq
            val fx = force * dx / sqrt(distanceSq)
            val fy = force * dy / sqrt(distanceSq)

            nodeA.vx -= fx
            nodeA.vy -= fy
            nodeB.vx += fx
            nodeB.vy += fy
        }
    }
}

fun BoxWithConstraintsScope.applyCenteringForce(nodes: List<Node>) {
    val centerX = this.maxWidth.value / 2
    val centerY = this.maxHeight.value / 2
    val strength = 0.001f // Increased from 0.005f

    for (node in nodes) {
        node.vx += (centerX - node.x) * strength
        node.vy += (centerY - node.y) * strength
    }
}

fun updatePositions(nodes: List<Node>, dt: Float) {
    val damping = 0.95f // Increased from 0.9f for more stability

    for (node in nodes) {
        node.vx *= damping
        node.vy *= damping
        node.x += node.vx * dt // Changed from /dt to *dt
        node.y += node.vy * dt // Changed from /dt to *dt
    }
}
