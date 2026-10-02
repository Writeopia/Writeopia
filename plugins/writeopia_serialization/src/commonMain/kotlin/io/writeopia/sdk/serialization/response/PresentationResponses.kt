package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.PresentationApi
import kotlinx.serialization.Serializable

@Serializable
data class GeneratePresentationResponse(
    val presentation: PresentationApi? = null,
    val error: String? = null
)

@Serializable
data class PresentationsResponse(
    val presentations: List<PresentationApi> = emptyList()
)
