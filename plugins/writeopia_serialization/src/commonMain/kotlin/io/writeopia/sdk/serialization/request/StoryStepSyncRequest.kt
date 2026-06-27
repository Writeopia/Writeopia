package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.DocumentMetadataApi
import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

@Serializable
data class StoryStepSyncRequest(
    val documentId: String,
    val workspaceId: String,
    val lastSyncTimestamp: Long,
    val modifiedSteps: List<StoryStepApi>,
    val deletedStepIds: List<String> = emptyList(),
    val metadataUpdate: DocumentMetadataApi? = null
)
