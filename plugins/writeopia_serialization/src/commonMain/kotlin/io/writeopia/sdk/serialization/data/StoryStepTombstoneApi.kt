package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

/** A deleted [StoryStepApi], by id, and the document it was in. */
@Serializable
data class StoryStepTombstoneApi(
    val stepId: String,
    val documentId: String
)
