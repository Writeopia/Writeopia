package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class SlideApi(
    val title: String,
    val content: List<StoryStepApi> = emptyList()
)

@Serializable
data class PresentationApi(
    val id: String,
    val documentId: String,
    val title: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val slides: List<SlideApi> = emptyList()
)
