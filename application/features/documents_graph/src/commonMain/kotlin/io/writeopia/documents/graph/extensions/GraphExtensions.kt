package io.writeopia.documents.graph.extensions

import io.writeopia.documents.graph.ItemData
import io.writeopia.forcegraph.Graph
import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node

internal fun Map<String, List<ItemData>>.toGraph(): Graph<ItemData> {
    val range = (30..900)

    val toNodes = this.mapValues { (id, menuItems) ->
        menuItems.map { item ->
            Node(
                data = item,
                x = (30..900).random().toFloat().div(1000),
                y = (30..970).random().toFloat().div(1000),
                isFolder = item.isFolder
            )
        }
    }

    val nodes = toNodes.values.flatten()
    val nodesMap = nodes.associateBy { it.data.id }

    val links = toNodes.mapValues { (id, nodes) ->
        val sourceNode = nodesMap[id] ?: return@mapValues emptyList()

        nodes.map { node ->
            Link(sourceNode, node)
        }
    }.values
        .flatten()

    return Graph(nodes, links)
}
