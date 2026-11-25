package io.writeopia.ai

import io.writeopia.app.dto.ai.EquationResponse
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.sdk.models.utils.ResultData

/** An equation: its LaTeX and the picture of it, rendered by the backend and served by the CDN. */
data class Equation(val latex: String, val imageUrl: String)

/**
 * Reads equations in drawings and renders LaTeX to pictures. Only the cloud does either: the
 * model reads the strokes, and the backend renders, so there is no local implementation.
 */
interface EquationAi {
    /** The equation in the strokes of [drawingJson] (the `DrawingData` JSON of a `DRAWING` step). */
    suspend fun recognize(drawingJson: String): ResultData<Equation>

    /** The picture of [latex]. */
    suspend fun render(latex: String): ResultData<Equation>
}

/** The Writeopia backend reading with Gemini and rendering with JLaTeXMath. */
class CloudEquationAi(private val repository: GenAiRepository) : EquationAi {
    override suspend fun recognize(drawingJson: String): ResultData<Equation> =
        repository.recognizeEquation(drawingJson).toEquation()

    override suspend fun render(latex: String): ResultData<Equation> =
        repository.renderEquation(latex).toEquation()

    private fun ResultData<EquationResponse>.toEquation(): ResultData<Equation> =
        when (this) {
            is ResultData.Complete -> {
                val latex = data.latex
                val url = data.imageUrl
                if (latex != null && url != null) {
                    ResultData.Complete(Equation(latex, url))
                } else {
                    ResultData.Error(Exception(data.error ?: "The equation could not be read"))
                }
            }
            is ResultData.Error -> ResultData.Error(exception)
            else -> ResultData.Error()
        }
}
