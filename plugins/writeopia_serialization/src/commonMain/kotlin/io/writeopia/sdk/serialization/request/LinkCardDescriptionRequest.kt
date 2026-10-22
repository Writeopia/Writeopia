package io.writeopia.sdk.serialization.request

import io.writeopia.sdk.serialization.data.StoryStepApi
import kotlinx.serialization.Serializable

/**
 * Asks the backend to describe the page of a link card and to save the description in the card.
 *
 * @param storyStep The link card: its url is the address to describe. The backend uses it to
 * create the card when the sync did not deliver it yet.
 * @param position Where the card is in the document, for the same reason.
 * @param requestTimestamp The client's time (epoch milliseconds). The backend stamps the updated
 * card with it, so a sync from the same time on returns the card.
 */
@Serializable
data class LinkCardDescriptionRequest(
    val storyStep: StoryStepApi,
    val position: String,
    val requestTimestamp: Long
)
