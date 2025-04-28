package io.writeopia.documents.graph.extensions

import io.writeopia.forcegraph.Graph
import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node
import io.writeopia.sdk.models.document.MenuItem

internal fun Map<String, List<MenuItem>>.toGraph(): Graph<MenuItem> {
    val toNodes = this.mapValues { (id, menuItems) ->
        menuItems.map { item ->
            Node(
                data = item,
                x = (100..800).random().toFloat(),
                y = (100..600).random().toFloat()
            )
        }
    }

    val nodes = toNodes.values.flatten()
    val nodesMap = nodes.associateBy { it.data.parentId }

    val links = toNodes.mapValues { (id, nodes) ->
        val sourceNode = nodesMap[id]!!

        nodes.map { node ->
            Link(sourceNode, node)
        }
    }.values
        .flatten()

    return Graph(nodes, links)
}
