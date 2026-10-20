package io.writeopia.app.dto.ai

import kotlinx.serialization.Serializable

/** What a free command to the AI does with the lines the writer selected. */
@Serializable
enum class AiCommandIntent {
    /** The answer takes the place of the selected lines: a rewrite, a fix, a translation. */
    REPLACE,

    /** The answer is new content, added to the document, which stays as it is. */
    ADD,
}

/**
 * A free command typed by the writer, with the document it is about and the lines selected when
 * it was typed, if any. The same request asks for the intent (what to do with the selection)
 * and for the answer itself.
 */
@Serializable
data class AiCommandRequest(
    /** What the writer typed, like "make it shorter" or "give three examples". */
    val command: String,
    /** The lines selected when the command was typed. Empty when nothing was selected. */
    val selectedText: String = "",
    val documentTitle: String = "",
    /** The text of the whole document. The backend cuts it when too long, around the selection. */
    val documentText: String = "",
    /** What to generate. Required by the generation endpoint, ignored by the intent one. */
    val intent: AiCommandIntent? = null,
    val model: String? = null,
    val stream: Boolean = false,
) {
    /**
     * [documentText] cut to [limit] characters. A long document is cut around the selected
     * lines, so the AI always sees them in their context; without a selection, or when the
     * selection is not found, the beginning of the document is kept.
     */
    fun documentAround(limit: Int): String {
        if (documentText.length <= limit) return documentText

        val anchor = selectedText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.let { firstLine -> documentText.indexOf(firstLine) }
            ?.takeIf { index -> index >= 0 }
            ?: return documentText.take(limit)

        val start = (anchor - limit / 2).coerceIn(0, documentText.length - limit)

        return documentText.substring(start, start + limit)
    }
}

@Serializable
data class AiCommandIntentResponse(
    val intent: AiCommandIntent? = null,
    /** The probability, from 0 to 1, that the writer wants the selected lines replaced. */
    val replaceProbability: Double = 0.0,
    val error: String? = null,
)
