package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The questions that file a document: one Noul per folder, "does this document belong in this
 * folder?". A document may fit more than one folder, and none at all, so each folder is judged
 * on its own instead of picking one with a Choice.
 */
object FolderSuggestionQuestions {

    const val DOCUMENT_TEXT_LIMIT = WritingSuggestionQuestions.DOCUMENT_TEXT_LIMIT

    /** The folders asked about: the first [FolderSuggestionsRequest.MAX_FOLDERS] with a name. */
    fun candidates(request: FolderSuggestionsRequest): List<FolderCandidate> =
        request.folders
            .filter { folder -> folder.id.isNotBlank() && folder.name.isNotBlank() }
            .take(FolderSuggestionsRequest.MAX_FOLDERS)

    fun stateFor(request: FolderSuggestionsRequest): JsonObject =
        JsonObject(
            mapOf(
                "document_title" to JsonPrimitive(request.documentTitle),
                "document" to JsonPrimitive(request.documentText.take(DOCUMENT_TEXT_LIMIT)),
                "folders" to JsonArray(
                    candidates(request).map { folder ->
                        JsonObject(mapOf("name" to JsonPrimitive(folder.name)))
                    }
                ),
            )
        )

    /** One question per folder, keyed by [questionId] so the answer finds its folder again. */
    fun questionsFor(request: FolderSuggestionsRequest): Map<String, SystemOneQuestion> =
        candidates(request).withIndex().associate { (index, folder) ->
            questionId(index) to questionFor(folder, index)
        }

    fun questionId(index: Int): String = "folder_$index"

    /** The index in [candidates] that [questionId] stands for, or null for an unknown id. */
    fun indexOf(questionId: String): Int? = questionId.removePrefix("folder_").toIntOrNull()

    fun questionFor(folder: FolderCandidate, index: Int): SystemOneQuestion =
        SystemOneQuestion.noul(
            "`document` (titled `document_title`) belongs in the folder named " +
                "`folders[$index].name` (\"${folder.name}\"): someone organizing this workspace " +
                "would file the document in that folder because of its subject.",
            whenTrue = "The subject, project or kind of the document matches what the folder " +
                "name says it holds, like meeting notes in a 'Meetings' folder or a recipe in " +
                "'Cooking'.",
            whenFalse = "The folder is about something else, is too specific for this document, " +
                "or the folder name says nothing that relates to the document.",
        )
}
