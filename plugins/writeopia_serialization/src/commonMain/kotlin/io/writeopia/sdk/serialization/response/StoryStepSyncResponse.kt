package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.DocumentMetadataApi
import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

@Serializable
data class StoryStepSyncResponse(
    val serverTimestamp: Long,
    val updatedSteps: List<StoryStepApi>,
    val deletedStepIds: List<String> = emptyList(),
    val metadataUpdate: DocumentMetadataApi? = null
)
