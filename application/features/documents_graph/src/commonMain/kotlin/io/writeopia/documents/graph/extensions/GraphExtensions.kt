package io.writeopia.documents.graph.extensions

import io.writeopia.documents.graph.ItemData
import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node
import io.writeopia.forcegraph.model.Graph

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
                initialX = (150..300).random().toFloat() / 1000 * maxWidth * 2,
                initialY = (200..350).random().toFloat() / 1000 * maxHeight * 2,
                isFolder = item.isFolder,
                selected = item.selected,
                isTag = item.isTag,
            )
        }
    }

    val links = flatMap { (id, children) ->
        val sourceNode = nodesMap[id] ?: return@flatMap emptyList()

        children.mapNotNull { child -> nodesMap[child.id]?.let { target -> Link(sourceNode, target) } }
    }

    return Graph(nodesMap.values.toList(), links)
}
