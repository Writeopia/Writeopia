package io.writeopia.requests

import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponse(val models: List<Model>)

@Serializable
data class Model(
    val name: String,
    val model: String,
    val modified_at: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: Details? = null
)

@Serializable
data class Details(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = listOf(),
    val parameterSize: String? = null,
    val quantizationLevel: String? = null
)
