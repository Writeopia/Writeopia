package io.writeopia.app.dto.ai

import kotlinx.serialization.Serializable

/** What a free command to the AI does with the text it was given. */
@Serializable
enum class AiCommandIntent {
    /** The answer takes the place of the target text: a rewrite, a fix, a translation. */
    REPLACE,

    /** The answer is new content, added after the target text, which stays as it is. */
    ADD,
}

/**
 * A free command typed by the writer, with the text it is about and the document around it.
 * The same request asks for the intent (what to do with the text) and for the answer itself.
 */
@Serializable
data class AiCommandRequest(
    /** What the writer typed, like "make it shorter" or "give three examples". */
    val command: String,
    /** The selected lines, or the paragraph at the cursor. Empty when the command is about the whole document. */
    val targetText: String = "",
    val documentTitle: String = "",
    /** The document before [targetText]: all of it when short, otherwise the closest paragraphs. */
    val textBefore: String = "",
    /** The document after [targetText], cut the same way. */
    val textAfter: String = "",
    /** What to generate. Required by the generation endpoint, ignored by the intent one. */
    val intent: AiCommandIntent? = null,
    val model: String? = null,
    val stream: Boolean = false,
)

@Serializable
data class AiCommandIntentResponse(
    val intent: AiCommandIntent? = null,
    /** The probability, from 0 to 1, that the writer wants the text replaced. */
    val replaceProbability: Double = 0.0,
    val error: String? = null,
)
