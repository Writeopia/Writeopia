package io.writeopia.forcegraph.model

import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node

data class Graph(val nodes: List<Node> = emptyList(), val links: List<Link> = emptyList())
