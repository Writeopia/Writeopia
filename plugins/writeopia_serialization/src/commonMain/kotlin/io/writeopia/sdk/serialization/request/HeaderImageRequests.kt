package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

/**
 * What the backend needs to draw the header of a document: the workspace that keeps the picture,
 * and the title and text of the document, so the picture fits its subject and tone.
 */
@Serializable
data class HeaderImageGenerateRequest(
    val workspaceId: String,
    val documentTitle: String = "",
    /** The text of the whole document, as context. The backend cuts it when too long. */
    val documentText: String = "",
)

/** A generated header the user approved, to keep with the pictures of the workspace. */
@Serializable
data class HeaderImageSaveRequest(
    val imageUrl: String,
)
