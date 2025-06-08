package io.writeopia.api.documents.documents.dto

import io.writeopia.sdk.serialization.data.DocumentApi
import kotlinx.serialization.Serializable

@Serializable
data class SendDocumentsRequest(val documents: List<DocumentApi>)
