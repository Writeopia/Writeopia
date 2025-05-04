package io.writeopia.documents.graph.extensions

import io.writeopia.documents.graph.ItemData
import io.writeopia.forcegraph.Graph
import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node

internal fun Map<String, List<ItemData>>.toGraph(maxWidth: Float, maxHeight: Float): Graph {
    val toNodes = this.mapValues { (id, menuItems) ->
        menuItems.map { item ->
            Node(
                id = item.id,
                label = item.title,
                initialX = (150..300).random().toFloat() / 1000 * maxWidth * 2,
                initialY = (200..350).random().toFloat() / 1000 * maxHeight * 2,
                isFolder = item.isFolder,
                selected = item.selected
            )
        }
    }

    val nodes = toNodes.values.flatten()
    val nodesMap = nodes.associateBy { it.id }

    val links = toNodes.mapValues { (id, nodes) ->
        val sourceNode = nodesMap[id] ?: return@mapValues emptyList()

        nodes.map { node ->
            Link(sourceNode, node)
        }
    }.values.flatten()

    return Graph(nodes, links)
}
