package io.writeopia.api.ai.service

import io.writeopia.api.ai.equation.DrawingRasterizer
import io.writeopia.api.ai.equation.EquationLatexParser
import io.writeopia.api.ai.equation.InvalidLatexException
import io.writeopia.api.ai.equation.LatexRenderer
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.backend.models.EquationImageStorage
import io.writeopia.connection.logger
import java.security.MessageDigest

/** What came out of reading or rendering an equation. */
sealed class EquationResult {
    abstract val tokenUsage: TokenUsage

    /** The LaTeX and the URL of its picture. */
    data class Equation(val latex: String, val imageUrl: String, override val tokenUsage: TokenUsage) : EquationResult()

    /** The drawing holds nothing the model could read as mathematics. */
    data class NotFound(override val tokenUsage: TokenUsage) : EquationResult()

    /** The LaTeX, from the model or the client, does not parse. */
    data class InvalidLatex(val latex: String, val message: String, override val tokenUsage: TokenUsage) :
        EquationResult()

    /** The model or the storage failed. */
    data class Failed(val message: String, override val tokenUsage: TokenUsage) : EquationResult()
}

/**
 * Turns a drawing into an equation and an equation into a picture. Reading: the strokes are drawn
 * into a PNG, the model transcribes it as LaTeX (asked once more when the LaTeX does not parse),
 * and the LaTeX is rendered and stored. Rendering alone serves edited equations. The picture is
 * stored once per LaTeX, under the hash of the LaTeX.
 */
class EquationService(
    private val genAiService: GenAiService,
    private val storage: EquationImageStorage,
    private val debugMode: Boolean,
    private val rasterize: (String) -> ByteArray? = DrawingRasterizer::toPng,
    private val renderPng: (String) -> ByteArray = LatexRenderer::toPng,
) {

    suspend fun recognize(drawingJson: String, modelName: String? = null): EquationResult {
        val picture = rasterize(drawingJson)
            ?: return EquationResult.NotFound(NO_USAGE)

        val first = ask(EquationPrompt.text, picture, modelName)
        var usage = first.second
        val firstAnswer = first.first
        val firstError = firstAnswer.error
        if (firstError != null) return EquationResult.Failed(firstError, usage)

        val firstLatex = EquationLatexParser.parse(firstAnswer.response)
            ?: return EquationResult.NotFound(usage)

        return when (val rendered = renderAndStore(firstLatex, usage)) {
            is EquationResult.InvalidLatex -> {
                logger.info("Equation LaTeX did not parse, asking again: {}", rendered.message)
                val second = ask(EquationPrompt.retry(firstLatex), picture, modelName)
                usage = usage + second.second
                val secondAnswer = second.first
                val secondError = secondAnswer.error
                if (secondError != null) return EquationResult.Failed(secondError, usage)

                val secondLatex = EquationLatexParser.parse(secondAnswer.response)
                    ?: return EquationResult.InvalidLatex(firstLatex, rendered.message, usage)
                renderAndStore(secondLatex, usage)
            }
            else -> rendered
        }
    }

    suspend fun render(latex: String): EquationResult = renderAndStore(latex.trim(), NO_USAGE)

    private suspend fun ask(prompt: String, picture: ByteArray, modelName: String?) =
        genAiService.generateWithImageAndUsage(
            prompt = prompt,
            imageBytes = picture,
            mimeType = "image/png",
            modelName = modelName,
            jsonResponse = true
        )

    private suspend fun renderAndStore(latex: String, usage: TokenUsage): EquationResult {
        if (latex.isBlank()) return EquationResult.InvalidLatex(latex, "The equation is empty", usage)

        val png = try {
            renderPng(latex)
        } catch (e: InvalidLatexException) {
            return EquationResult.InvalidLatex(latex, e.message ?: "Invalid LaTeX", usage)
        }

        val url = try {
            storage.store(png, LatexRenderer.CONTENT_TYPE, objectNameOf(latex), debugMode)
        } catch (e: Exception) {
            logger.error("Failed to store the picture of an equation", e)
            return EquationResult.Failed("The equation could not be stored", usage)
        }

        return EquationResult.Equation(latex, url, usage)
    }

    companion object {
        private val NO_USAGE = TokenUsage(0, 0, 0)

        private const val FOLDER = "equations"

        /** The object name of the picture of [latex]: the same LaTeX always has the same name. */
        fun objectNameOf(latex: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(latex.toByteArray())
            return "$FOLDER/${digest.joinToString("") { byte -> "%02x".format(byte) }}.png"
        }

        private operator fun TokenUsage.plus(other: TokenUsage) = TokenUsage(
            inputTokens = inputTokens + other.inputTokens,
            outputTokens = outputTokens + other.outputTokens,
            totalTokens = totalTokens + other.totalTokens
        )
    }
}
