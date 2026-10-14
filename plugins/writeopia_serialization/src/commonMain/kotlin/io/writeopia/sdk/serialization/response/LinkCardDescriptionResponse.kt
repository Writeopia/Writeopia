package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

/**
 * The answer to a [io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest].
 *
 * @param storyStep The link card as saved in the backend, with the description as its text.
 * @param error Why there is no description, when there is none.
 */
@Serializable
data class LinkCardDescriptionResponse(
    val storyStep: StoryStepApi? = null,
    val error: String? = null
)
