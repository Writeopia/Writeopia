package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

/**
 * Asks the backend to generate a presentation of a document. The document is the one of the
 * path; the backend reads its content, so the client sends no text.
 */
@Serializable
data class GeneratePresentationRequest(
    val model: String? = null
)
