package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.PresentationApi
import kotlinx.serialization.Serializable

/** What the search of a workspace finds: the documents, and the presentations without their slides. */
@Serializable
data class SearchResponse(
    val documents: List<DocumentApi> = emptyList(),
    val presentations: List<PresentationApi> = emptyList()
)
