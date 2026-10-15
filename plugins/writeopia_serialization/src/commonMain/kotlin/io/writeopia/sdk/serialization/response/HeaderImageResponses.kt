package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * The drawn header, already on the CDN, or why there is none. It is only a candidate: the user
 * approves it before it joins the pictures of the workspace.
 */
@Serializable
data class HeaderImageGenerateResponse(
    val imageUrl: String? = null,
    val error: String? = null,
)

/** A header picture of a workspace, that any document of the workspace can use. */
@Serializable
data class HeaderImageApi(
    val id: String,
    val url: String,
    val createdAt: Long,
)

@Serializable
data class HeaderImagesResponse(
    val images: List<HeaderImageApi> = emptyList(),
)
