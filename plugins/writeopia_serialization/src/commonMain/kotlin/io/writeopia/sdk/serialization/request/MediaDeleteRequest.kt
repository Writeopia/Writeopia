package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

/** Asks the media service to delete one of the user's pictures, by the URL it was stored at. */
@Serializable
data class MediaDeleteRequest(val imageUrl: String)
