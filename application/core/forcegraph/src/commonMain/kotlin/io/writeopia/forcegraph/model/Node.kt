package io.writeopia.forcegraph.model

data class Node(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val isDragged: Boolean = false
)
