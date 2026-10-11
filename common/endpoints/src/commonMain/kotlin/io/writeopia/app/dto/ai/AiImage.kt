package io.writeopia.app.dto.ai

import kotlinx.serialization.Serializable

/**
 * What the backend needs to illustrate a paragraph: the paragraph itself, and the whole
 * document around it, so the picture matches the text, not only one sentence of it. The backend
 * writes the prompt of the image model from these.
 */
@Serializable
data class AiImageRequest(
    /** The paragraph the image is for. */
    val paragraph: String,
    val documentTitle: String = "",
    /** The text of the whole document, as context. The backend cuts it when too long. */
    val documentText: String = "",
)

/** The generated image, uploaded to the media storage, or why there is none. */
@Serializable
data class AiImageResponse(
    val imageUrl: String? = null,
    val error: String? = null,
)
