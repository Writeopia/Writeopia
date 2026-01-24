package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class DeleteDocumentsRequest(
    val documentIds: List<String>
)
