package io.writeopia.app.dto.ai

import kotlinx.serialization.Serializable

/**
 * A drawing to read an equation from: the `DrawingData` JSON kept in the text of a `DRAWING`
 * step. The backend draws the strokes into a picture for the model, so no client has to encode
 * images.
 */
@Serializable
data class EquationRecognizeRequest(
    val drawing: String,
)

/** LaTeX to render into a picture, with no delimiters (`$`, `\[`...). */
@Serializable
data class EquationRenderRequest(
    val latex: String,
)

/**
 * An equation: its LaTeX and the picture of it on the CDN (black glyphs on a transparent
 * background, drawn at `EquationImage.SCALE` pixels per point), or why there is none.
 */
@Serializable
data class EquationResponse(
    val latex: String? = null,
    val imageUrl: String? = null,
    val error: String? = null,
)
