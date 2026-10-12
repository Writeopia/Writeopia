package io.writeopia.sdk.serialization.json

import io.writeopia.sdk.serialization.data.DocumentTagApi
import kotlinx.serialization.Serializable

@Serializable
class SendDocumentTagsRequest(val tags: List<DocumentTagApi>, val workspaceId: String)
