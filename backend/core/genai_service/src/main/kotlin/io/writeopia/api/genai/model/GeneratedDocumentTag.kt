package io.writeopia.api.genai.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A tag the cloud AI made up for a document: its name and the explanation of what it stands for. */
data class GeneratedDocumentTag(
    val name: String,
    val description: String,
)

/**
 * Reads the tag out of what the model answered. The model is asked for JSON, and gets it most of
 * the time; a code fence around it, or a plain "Name: explanation" line, are read too.
 */
object DocumentTagParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val MAX_NAME_LENGTH = 40
    const val MAX_DESCRIPTION_LENGTH = 300

    fun parse(answer: String?): GeneratedDocumentTag? {
        val text = answer?.trim()?.removeFence() ?: return null
        if (text.isBlank()) return null

        // A JSON answer without a usable name is no tag; only prose falls back to a line.
        return if (text.startsWith("{")) parseJson(text) else parseJson(text) ?: parseLine(text)
    }

    private fun parseJson(text: String): GeneratedDocumentTag? = runCatching {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null

        val element = json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        val name = element["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val description = element["description"]?.jsonPrimitive?.content?.trim().orEmpty()

        tag(name, description)
    }.getOrNull()

    private fun parseLine(text: String): GeneratedDocumentTag? {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val separator = line.indexOfAny(charArrayOf(':', '-', '—'))

        return if (separator > 0) {
            tag(line.substring(0, separator), line.substring(separator + 1))
        } else {
            tag(line, "")
        }
    }

    private fun tag(name: String, description: String): GeneratedDocumentTag? {
        val cleanName = name.trim().trim('"', '\'', '#', '*', ' ').take(MAX_NAME_LENGTH).trim()
        if (cleanName.isEmpty()) return null

        return GeneratedDocumentTag(
            name = cleanName,
            description = description.trim().trim('"', '*', ' ').take(MAX_DESCRIPTION_LENGTH).trim(),
        )
    }

    private fun String.removeFence(): String {
        if (!startsWith("```")) return this
        return lines().drop(1).takeWhile { !it.trimStart().startsWith("```") }.joinToString("\n")
    }
}
