package io.writeopia.api.equation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the LaTeX out of what the model answered: JSON like `{"latex": "..."}` when it obeyed,
 * else the raw text; then code fences and math delimiters (`$`, `$$`, `\[ \]`, `\( \)`) are
 * stripped, since the picture renders math already.
 */
object EquationLatexParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** The LaTeX in [answer], or null when there is none. */
    fun parse(answer: String?): String? {
        if (answer.isNullOrBlank()) return null

        val unfenced = stripCodeFences(answer.trim())
        val fromJson = latexFromJson(unfenced)
        val text = (fromJson ?: unfenced).trim()

        return stripDelimiters(text).trim().takeIf { it.isNotEmpty() }
    }

    private fun latexFromJson(text: String): String? {
        if (!text.startsWith("{")) return null
        return try {
            val element = json.parseToJsonElement(text) as? JsonObject ?: return null
            element["latex"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun stripCodeFences(text: String): String {
        if (!text.startsWith("```")) return text
        val withoutStart = text.removePrefix("```").substringAfter("\n", missingDelimiterValue = "")
        return withoutStart.removeSuffix("```").trim()
    }

    private fun stripDelimiters(text: String): String {
        var result = text
        var changed = true
        while (changed) {
            changed = false
            DELIMITERS.forEach { (start, end) ->
                if (result.length >= start.length + end.length && result.startsWith(start) && result.endsWith(end)) {
                    result = result.substring(start.length, result.length - end.length).trim()
                    changed = true
                }
            }
        }
        return result
    }

    private val DELIMITERS = listOf(
        "$$" to "$$",
        "\\[" to "\\]",
        "\\(" to "\\)",
        "$" to "$",
        "\\begin{equation}" to "\\end{equation}",
        "\\begin{equation*}" to "\\end{equation*}",
    )
}
