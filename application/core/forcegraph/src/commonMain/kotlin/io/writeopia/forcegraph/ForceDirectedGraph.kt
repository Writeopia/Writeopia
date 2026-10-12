package io.writeopia.forcegraph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.max
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
    selected: Boolean,
    /** A hub that stands for a tag of the workspace, drawn in its own colour. */
    val isTag: Boolean = false,
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

/**
 * How the graph is looked at: moved by [offset] and scaled by [scale] around the centre of the
 * viewport. Hoisted so a screen can add zoom buttons: Compose Desktop never receives the trackpad
 * pinch, so the wheel with Command or Control held and these buttons are the ways to zoom there.
 */
class GraphViewState {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun zoomIn() = zoomBy(BUTTON_ZOOM_STEP)

    fun zoomOut() = zoomBy(1 / BUTTON_ZOOM_STEP)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

@Composable
fun rememberGraphViewState(): GraphViewState = remember { GraphViewState() }

@Composable
fun BoxWithConstraintsScope.ForceDirectedGraph(
    modifier: Modifier = Modifier,
    nodes: List<Node>,
    links: List<Link>,
    onNodeSelected: (String) -> Unit,
    viewState: GraphViewState = rememberGraphViewState(),
) {
    val textMeasurer = rememberTextMeasurer()
    // Every name is drawn, faded; the hovered or selected ones come forward.
    val fadedTextStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = Color.White.copy(alpha = 0.75f),
            background = Color.Blue.copy(alpha = 0.45f),
            fontSize = 8.sp
        )
    val highlightedTextStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = Color.White,
            background = Color.Blue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

    val nodeRadius = if (nodes.size < 15) (25 - nodes.size).toFloat() else 8f
    var hoveredNodeId by remember { mutableStateOf<String?>(null) }

    // The whole drawing is scaled around the centre of the viewport; pointer positions are
    // mapped back to graph space before any hit test.
    var scale by viewState::scale
    var offset by viewState::offset
    val density = LocalDensity.current
    val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
    val center = Offset(viewport.width / 2, viewport.height / 2)

    fun toGraphSpace(position: Offset): Offset = (position - offset - center) / scale + center

    fun zoomBy(factor: Float) = viewState.zoomBy(factor)

    // The label boxes, measured once per set of nodes, so a hover over a name counts too.
    val labelSizes = remember(nodes, textMeasurer) {
        nodes.associate { node -> node.id to textMeasurer.measure(" ${node.label} ", fadedTextStyle).size }
    }

    fun labelRect(node: Node): Rect? {
        if (node.label.isBlank()) return null
        val size = labelSizes[node.id] ?: return null
        return Rect(labelTopLeft(node, viewport), Size(size.width.toFloat(), size.height.toFloat()))
    }

    fun nodeAt(position: Offset, radius: Float): Node? {
        val point = toGraphSpace(position)
        return nodes.firstOrNull { node ->
            val dx = node.x - point.x
            val dy = node.y - point.y

            sqrt(dx * dx + dy * dy) < radius
        }
    }

    Canvas(
        modifier = modifier
            // Two fingers zoom (opening or closing) and move the graph; one finger is left to the
            // drag below.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                zoomBy(zoom)
                                offset += pan
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(nodes) {
                // The mouse over a node, or over its name, brings the name forward. Scrolling
                // moves the graph.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                if (delta == Offset.Zero) continue
                                // Compose Desktop gets no pinch from the trackpad: the wheel, or a
                                // two-finger scroll, with Command or Control held zooms instead.
                                val pinching =
                                    event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                                if (pinching) {
                                    zoomBy(if (delta.y < 0) WHEEL_ZOOM_STEP else 1 / WHEEL_ZOOM_STEP)
                                } else {
                                    offset -= delta * SCROLL_PIXELS_PER_LINE
                                }
                                event.changes.forEach { it.consume() }
                            }

                            PointerEventType.Move, PointerEventType.Enter -> {
                                val position = event.changes.firstOrNull()?.position ?: continue
                                val point = toGraphSpace(position)
                                hoveredNodeId = (
                                    nodeAt(position, HOVER_RADIUS) ?: nodes.firstOrNull { node ->
                                        labelRect(node)?.contains(point) == true
                                    }
                                )?.id
                            }

                            PointerEventType.Exit -> hoveredNodeId = null

                            else -> {}
                        }
                    }
                }
            }
            .pointerInput(nodes) {
                detectTapGestures(
                    onTap = { offset ->
                        nodeAt(offset, 20F)?.also { onNodeSelected(it.id) }
                    }
                )
            }
            .pointerInput(nodes) {
                var draggingNode: Node? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingNode = nodeAt(offset, 12f)?.also { it.isDragged = true }
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
                        val node = draggingNode
                        if (node != null) {
                            node.x += dragAmount.x / scale
                            node.y += dragAmount.y / scale
                            node.vx = 0f
                            node.vy = 0f
                        } else {
                            // A drag on empty space moves the whole graph.
                            offset += dragAmount
                        }
                    }
                )
            }
    ) {
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = center)
        }) {
            links.forEach { link ->
                drawLine(
                    color = Color.LightGray,
                    start = Offset(
                        min(link.source.x, viewport.width),
                        min(link.source.y, viewport.height)
                    ),
                    end = Offset(
                        min(link.target.x, viewport.width),
                        min(link.target.y, viewport.height)
                    ),
                    strokeWidth = 2f
                )
            }

            nodes.forEach { node ->
                val hovered = node.id == hoveredNodeId
                val radius = if (node.isFolder) nodeRadius else nodeRadius / 2

                drawCircle(
                    color = if (node.isDragged) Color.Red else nodeColor(node),
                    center = Offset(
                        max(min(node.x, viewport.width - nodeRadius / 2), 0F + nodeRadius / 2),
                        max(min(node.y, viewport.height - nodeRadius / 2), 0F + nodeRadius / 2)
                    ),
                    radius = if (hovered) radius * 1.4f else radius
                )
            }

            // Faded names first, then the ones brought forward, so those stay readable on top.
            val (highlighted, faded) = nodes.partition { node -> node.id == hoveredNodeId || node.showName }

            faded.forEach { node -> drawNodeLabel(textMeasurer, node, fadedTextStyle, viewport) }
            highlighted.forEach { node -> drawNodeLabel(textMeasurer, node, highlightedTextStyle, viewport) }
        }
    }
}

private fun nodeColor(node: Node): Color =
    when {
        node.isTag -> TAG_COLOR
        node.isFolder -> FOLDER_COLOR
        else -> Color.Gray
    }

/** Where the name of [node] is drawn: just under the circle, kept inside the viewport. */
private fun labelTopLeft(node: Node, viewport: Size): Offset =
    Offset(
        max(min(node.x - 30, viewport.width - 50), 0F + 50),
        max(min(node.y + 20, viewport.height - 50), 0F + 50)
    )

private fun DrawScope.drawNodeLabel(
    textMeasurer: TextMeasurer,
    node: Node,
    style: TextStyle,
    viewport: Size,
) {
    if (node.label.isBlank()) return
    if (node.x < 0 || node.y < 0 || node.x > viewport.width || node.y > viewport.height) return

    drawText(
        textMeasurer = textMeasurer,
        text = " ${node.label} ",
        topLeft = labelTopLeft(node, viewport),
        style = style
    )
}

private val TAG_COLOR = Color(0xFFFFC107)
private val FOLDER_COLOR = Color(0xFF2196F3)
private const val HOVER_RADIUS = 18f
private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 4f
private const val WHEEL_ZOOM_STEP = 1.1f
private const val BUTTON_ZOOM_STEP = 1.25f
private const val SCROLL_PIXELS_PER_LINE = 20f
