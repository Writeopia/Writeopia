package io.writeopia.sdk.models.drawing

import io.writeopia.sdk.models.id.GenerateId
import kotlinx.serialization.Serializable

@Serializable
data class DrawingData(
    val id: String = GenerateId.generate(),
    val strokes: List<Stroke> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val backgroundColor: Int? = null
)
