package io.writeopia.forcegraph.model

data class Link<T>(
    val source: Node<T>,
    val target: Node<T>,
)
