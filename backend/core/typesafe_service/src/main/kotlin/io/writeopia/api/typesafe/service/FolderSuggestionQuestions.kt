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
 * on its own instead of picking one with a Choice. Each folder comes with its profile: where it
 * sits, which documents it already holds and how they are tagged, so Jev judges more than a name.
 */
object FolderSuggestionQuestions {

    const val DOCUMENT_TEXT_LIMIT = WritingSuggestionQuestions.DOCUMENT_TEXT_LIMIT

    /** The folders asked about: the first [FolderSuggestionsRequest.MAX_FOLDERS] of [folders] with an id and a name. */
    fun candidates(folders: List<FolderCandidate>): List<FolderCandidate> =
        folders
            .filter { folder -> folder.id.isNotBlank() && folder.name.isNotBlank() }
            .take(FolderSuggestionsRequest.MAX_FOLDERS)

    /** The candidates the app sent, when the backend did not pick its own. */
    fun candidates(request: FolderSuggestionsRequest): List<FolderCandidate> = candidates(request.folders)

    fun stateFor(
        request: FolderSuggestionsRequest,
        candidates: List<FolderCandidate> = candidates(request),
    ): JsonObject =
        JsonObject(
            mapOf(
                "document_title" to JsonPrimitive(request.documentTitle),
                "document" to JsonPrimitive(request.documentText.take(DOCUMENT_TEXT_LIMIT)),
                "folders" to JsonArray(candidates.map { folder -> profileOf(folder) }),
            )
        )

    /** What Jev reads about a folder. Empty parts are left out so a bare name stays a bare name. */
    fun profileOf(folder: FolderCandidate): JsonObject =
        JsonObject(
            buildMap {
                put("name", JsonPrimitive(folder.name))
                if (folder.path.isNotBlank()) put("path", JsonPrimitive(folder.path))
                if (folder.recentTitles.isNotEmpty()) {
                    put("recent_documents", JsonArray(folder.recentTitles.map { title -> JsonPrimitive(title) }))
                }
                if (folder.tags.isNotEmpty()) {
                    put("tags", JsonArray(folder.tags.map { tag -> JsonPrimitive(tag) }))
                }
            }
        )

    /** One question per folder, keyed by [questionId] so the answer finds its folder again. */
    fun questionsFor(
        request: FolderSuggestionsRequest,
        candidates: List<FolderCandidate> = candidates(request),
    ): Map<String, SystemOneQuestion> =
        candidates.withIndex().associate { (index, folder) ->
            questionId(index) to questionFor(folder, index)
        }

    fun questionId(index: Int): String = "folder_$index"

    /** The index in [candidates] that [questionId] stands for, or null for an unknown id. */
    fun indexOf(questionId: String): Int? = questionId.removePrefix("folder_").toIntOrNull()

    fun questionFor(folder: FolderCandidate, index: Int): SystemOneQuestion =
        SystemOneQuestion.noul(
            "`document` (titled `document_title`) belongs in the folder `folders[$index]`, named " +
                "\"${folder.name}\": someone organizing this workspace would file the document in " +
                "that folder because of its subject. The folder's `path` says where it sits, " +
                "`recent_documents` what it already holds and `tags` how those documents are tagged.",
            whenTrue = "The subject, project or kind of the document matches what the folder " +
                "holds: its name, the documents already in it or their tags, like meeting notes " +
                "in a 'Meetings' folder, or a recipe in a folder of other recipes.",
            whenFalse = "The folder is about something else, is too specific for this document, " +
                "or nothing about the folder, its name, its documents or its tags, relates to the document.",
        )
}
