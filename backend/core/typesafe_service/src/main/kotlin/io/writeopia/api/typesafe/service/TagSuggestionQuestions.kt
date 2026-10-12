package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.writingagent.TagCandidate
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The questions that tag a document: one Noul per tag of the workspace catalogue, "should this
 * document carry this tag?". A document may carry several tags, or none, so each tag is judged
 * on its own instead of picking one with a Choice. The explanation of the tag is what tells Jev
 * what the tag means.
 */
object TagSuggestionQuestions {

    const val DOCUMENT_TEXT_LIMIT = WritingSuggestionQuestions.DOCUMENT_TEXT_LIMIT

    /** The tags asked about: the first [TagSuggestionsRequest.MAX_TAGS] with an id and a name. */
    fun candidates(request: TagSuggestionsRequest): List<TagCandidate> =
        request.tags
            .filter { tag -> tag.id.isNotBlank() && tag.name.isNotBlank() }
            .take(TagSuggestionsRequest.MAX_TAGS)

    fun stateFor(request: TagSuggestionsRequest): JsonObject =
        JsonObject(
            mapOf(
                "document_title" to JsonPrimitive(request.documentTitle),
                "document" to JsonPrimitive(request.documentText.take(DOCUMENT_TEXT_LIMIT)),
                "tags" to JsonArray(
                    candidates(request).map { tag ->
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(tag.name),
                                "description" to JsonPrimitive(tag.description),
                            )
                        )
                    }
                ),
            )
        )

    /** One question per tag, keyed by [questionId] so the answer finds its tag again. */
    fun questionsFor(request: TagSuggestionsRequest): Map<String, SystemOneQuestion> =
        candidates(request).withIndex().associate { (index, tag) ->
            questionId(index) to questionFor(tag, index)
        }

    fun questionId(index: Int): String = "tag_$index"

    /** The index in [candidates] that [questionId] stands for, or null for an unknown id. */
    fun indexOf(questionId: String): Int? = questionId.removePrefix("tag_").toIntOrNull()

    fun questionFor(tag: TagCandidate, index: Int): SystemOneQuestion {
        val meaning = if (tag.description.isBlank()) "" else ", which means: ${tag.description}"

        return SystemOneQuestion.noul(
            "`document` (titled `document_title`) should carry the tag `tags[$index].name` " +
                "(\"${tag.name}\"$meaning): someone labelling this workspace would put that tag " +
                "on the document because of what the document is about.",
            whenTrue = "The subject, project, kind or purpose of the document is what the tag " +
                "name and its explanation describe, like meeting notes carrying 'Meetings' or a " +
                "recipe carrying 'Cooking'.",
            whenFalse = "The tag is about something else, applies only to a passing mention, " +
                "or its name and explanation say nothing that relates to the document.",
        )
    }
}
