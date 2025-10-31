package io.writeopia.common.utils

interface Node {
    val id: String
    var depth: Int

    fun addNotes(nodes: List<Node>)

    /**
     * Get the next notes. Only the next notes, not the whole graph
     */
    fun getNodes(): List<Node>

    companion object {
        fun <T : Node> anyNode(node: T, predicate: (T) -> Boolean): Boolean {
            val match = predicate(node)

            return match || node.getNodes()
                .map { it as T }
                .any { innerNode -> anyNode(innerNode, predicate) }
        }

        fun <T : Node> toList(node: T): List<T> =
            listOf(node) + node.getNodes().map { it as T }.flatMap(::toList)
    }
}

fun <T : Node> T.anyNode(predicate: (T) -> Boolean): Boolean =
    Node.anyNode(this, predicate)

/**
 * Get all notes as list. Expands all nodes.
 */
fun <T : Node> T.toList(): List<T> = Node.toList(this)

/**
 * Creates a node tree of Node<T>. The head of the tree should be provided.
 */
internal fun <T : Node> createNodeTree(
    map: Map<String, List<T>>,
    node: T,
    depth: Int = -1,
    filterPredicate: (T) -> Boolean = { true }
): T {
    val nextNodes = map[node.id]

    nextNodes.takeIf { it?.isNotEmpty() == true }
        ?.map { nextNode ->
            nextNode.apply {
                this.depth = depth + 1
            }
        }?.let(node::addNotes)

    nextNodes?.filter(filterPredicate)
        ?.forEach { nextCode -> createNodeTree(map, nextCode,depth + 1) }

    return node
}
