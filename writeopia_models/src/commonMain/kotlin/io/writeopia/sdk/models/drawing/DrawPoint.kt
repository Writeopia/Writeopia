package io.writeopia.sdk.models.drawing

import kotlinx.serialization.Serializable

@Serializable
data class DrawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = 0L
)
