package io.writeopia.sdk.serialization.json

import io.writeopia.sdk.serialization.data.DocumentApi
import kotlinx.serialization.Serializable

@Serializable
data class SendDocumentsRequest(val documents: List<DocumentApi>, val workspaceId: String)
