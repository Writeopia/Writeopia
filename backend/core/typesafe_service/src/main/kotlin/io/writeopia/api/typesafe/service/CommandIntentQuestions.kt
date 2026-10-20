package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.ai.AiCommandRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The question Jev answers about a free command: does the writer want the text they pointed at
 * rewritten, or something new added after it? One Choice, since the two exclude each other.
 */
object CommandIntentQuestions {

    const val QUESTION_ID = "intent"
    const val REPLACE = "replace"
    const val ADD = "add"

    /** The text around the target is cut here to keep the request small. The target is never cut. */
    const val CONTEXT_LIMIT = 3_000

    fun stateFor(request: AiCommandRequest): JsonObject =
        JsonObject(
            mapOf(
                "command" to JsonPrimitive(request.command),
                "target_text" to JsonPrimitive(request.targetText),
                "document_title" to JsonPrimitive(request.documentTitle),
                "text_before" to JsonPrimitive(request.textBefore.takeLast(CONTEXT_LIMIT)),
                "text_after" to JsonPrimitive(request.textAfter.take(CONTEXT_LIMIT)),
            )
        )

    fun question(): SystemOneQuestion =
        SystemOneQuestion.choice(
            instructions = "The writer of a document selected `target_text` and typed `command` " +
                "for an AI assistant. `text_before` and `text_after` are the parts of the " +
                "document right before and after the selection, and `document_title` is its " +
                "title. Decide what the writer wants the assistant to do with `target_text`.",
            options = mapOf(
                REPLACE to "The command transforms `target_text` itself, so the answer takes " +
                    "its place: rewrite, fix the grammar or spelling, shorten, expand, " +
                    "simplify, translate, change the tone or the person, turn it into a list " +
                    "or a table, reorder it, or produce any new version of the same text.",
                ADD to "The command asks for something new that leaves `target_text` as it is, " +
                    "so the answer goes after it: continue the text, write the next paragraph " +
                    "or section, answer a question about it, explain it, give examples, list " +
                    "ideas, draft a reply, or create related content.",
            )
        )
}
