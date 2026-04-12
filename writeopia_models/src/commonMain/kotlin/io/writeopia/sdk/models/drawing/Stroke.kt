package io.writeopia.sdk.models.drawing

import io.writeopia.sdk.models.id.GenerateId
import kotlinx.serialization.Serializable

@Serializable
data class Stroke(
    val id: String = GenerateId.generate(),
    val points: List<DrawPoint> = emptyList(),
    val color: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 4f,
    val tool: DrawingTool = DrawingTool.PEN
)
