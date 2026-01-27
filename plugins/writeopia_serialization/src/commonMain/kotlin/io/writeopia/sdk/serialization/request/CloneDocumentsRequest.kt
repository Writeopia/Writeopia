package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class CloneDocumentsRequest(
    val documentIds: List<String>
)
