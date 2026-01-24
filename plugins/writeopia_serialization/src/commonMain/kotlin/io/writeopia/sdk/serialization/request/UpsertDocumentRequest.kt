package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.DocumentApi
import kotlinx.serialization.Serializable

@Serializable
data class UpsertDocumentRequest(
    val document: DocumentApi
)
