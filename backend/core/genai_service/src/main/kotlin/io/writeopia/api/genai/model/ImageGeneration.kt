package io.writeopia.api.genai.model

/** The bytes of a picture the model drew, with their media type (png unless the model says). */
class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String
)

/**
 * The answer of the image model: the picture, or why there is none, and the tokens it cost.
 * The tokens are reported even when the picture is missing, since the request was made.
 */
class ImageGeneration(
    val image: GeneratedImage? = null,
    val error: String? = null,
    val tokenUsage: TokenUsage = TokenUsage(0, 0, 0)
)
