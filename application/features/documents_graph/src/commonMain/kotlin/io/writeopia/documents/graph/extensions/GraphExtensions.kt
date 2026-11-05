package io.writeopia.documents.graph.extensions

import io.writeopia.documents.graph.ItemData
import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node
import io.writeopia.forcegraph.model.Graph
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The adjacency list as nodes and links. An item listed under several hubs (a document with
 * several tags) becomes one node with one link per hub.
 */
internal fun Map<String, List<ItemData>>.toGraph(maxWidth: Float, maxHeight: Float): Graph {
    val nodesMap = linkedMapOf<String, Node>()

    values.flatten().forEach { item ->
        nodesMap.getOrPut(item.id) {
            Node(
                id = item.id,
                label = item.title,
                initialX = 0f,
                initialY = 0f,
                isFolder = item.isFolder,
                selected = item.selected,
                isTag = item.isTag,
            )
        }
    }

    spreadOverViewport(nodesMap.values, maxWidth, maxHeight)

    val links = flatMap { (id, children) ->
        val sourceNode = nodesMap[id] ?: return@flatMap emptyList()

        children.mapNotNull { child -> nodesMap[child.id]?.let { target -> Link(sourceNode, target) } }
    }

    return Graph(nodesMap.values.toList(), links)
}

/**
 * Spreads the nodes over 90% of the viewport on a golden-angle spiral: the first frame shows the
 * whole graph instead of a clump in the middle, and close nodes don't start on top of each other.
 * The simulation then pulls the graph together as before.
 */
internal fun spreadOverViewport(nodes: Collection<Node>, width: Float, height: Float) {
    val count = nodes.size
    if (count == 0) return

    val centerX = width / 2
    val centerY = height / 2
    if (count == 1) {
        nodes.first().x = centerX
        nodes.first().y = centerY
        return
    }

    nodes.forEachIndexed { index, node ->
        val angle = index * GOLDEN_ANGLE
        val radius = sqrt((index + 0.5f) / count)
        node.x = centerX + cos(angle) * radius * width * 0.45f
        node.y = centerY + sin(angle) * radius * height * 0.45f
    }
}

/** π(3 − √5): the angle between neighbours on a sunflower spiral. */
private const val GOLDEN_ANGLE = 2.3999632f
