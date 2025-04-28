package io.writeopia.forcegraph

import io.writeopia.forcegraph.model.Link
import io.writeopia.forcegraph.model.Node

data class Graph<T>(val nodes: List<Node<T>> = emptyList(), val links: List<Link<T>> = emptyList())
