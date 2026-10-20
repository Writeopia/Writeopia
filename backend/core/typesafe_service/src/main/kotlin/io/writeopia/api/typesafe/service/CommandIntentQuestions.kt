package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.ai.AiCommandRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The question Jev answers about a free command: does the writer want the lines they selected
 * rewritten, or something new added to the document? One Choice, since the two exclude each
 * other. Only asked when lines are selected: without them there is nothing to replace.
 */
object CommandIntentQuestions {

    const val QUESTION_ID = "intent"
    const val REPLACE = "replace"
    const val ADD = "add"

    /** The document is cut here, around the selection, to keep the request small. */
    const val DOCUMENT_LIMIT = 6_000

    fun stateFor(request: AiCommandRequest): JsonObject =
        JsonObject(
            mapOf(
                "command" to JsonPrimitive(request.command),
                "selected_text" to JsonPrimitive(request.selectedText),
                "document_title" to JsonPrimitive(request.documentTitle),
                "document" to JsonPrimitive(request.documentAround(DOCUMENT_LIMIT)),
            )
        )

    fun question(): SystemOneQuestion =
        SystemOneQuestion.choice(
            instructions = "The writer of `document` (titled `document_title`) selected the lines " +
                "in `selected_text` and typed `command` for an AI assistant. Decide what the " +
                "writer wants the assistant to do with the selected lines.",
            options = mapOf(
                REPLACE to "The command transforms the selected lines themselves, so the answer " +
                    "takes their place: rewrite, fix the grammar or spelling, shorten, expand, " +
                    "simplify, translate, change the tone or the person, turn them into a list " +
                    "or a table, reorder them, or produce any new version of the same text.",
                ADD to "The command asks for something new that leaves the selected lines as " +
                    "they are, so the answer goes after them: continue the text, write the " +
                    "next paragraph or section, answer a question about them, explain them, " +
                    "give examples, list ideas, draft a reply, or create related content.",
            )
        )
}
