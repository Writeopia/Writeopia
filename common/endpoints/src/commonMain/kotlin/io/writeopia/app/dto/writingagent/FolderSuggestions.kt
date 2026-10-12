package io.writeopia.app.dto.writingagent

import kotlinx.serialization.Serializable

/**
 * A folder the document could move to. The id is for the app; the rest is the profile Jev reads:
 * the name, where the folder sits, what is already in it and how its documents are tagged.
 */
@Serializable
data class FolderCandidate(
    val id: String,
    val name: String,
    /** The titles of the folders above it, root first, joined by " / ". Empty for a root folder. */
    val path: String = "",
    /** The titles of the documents most recently updated in it, a few at most. */
    val recentTitles: List<String> = emptyList(),
    /** The names of the tags its documents carry, a few at most. */
    val tags: List<String> = emptyList(),
)

/**
 * A document that sits in the root folder, and the folders of the workspace it could belong to.
 * With a [workspaceId], the backend picks the candidates itself: the folders of the workspace
 * closest to the document by their embeddings, profiled from what they hold. [folders] is what
 * the app would offer, used when the backend cannot rank. The backend asks Jev about each
 * candidate and answers with the ones that fit.
 */
@Serializable
data class FolderSuggestionsRequest(
    val documentTitle: String = "",
    val documentText: String = "",
    val folders: List<FolderCandidate> = emptyList(),
    val workspaceId: String = "",
) {
    companion object {
        /** How many folders are asked about at most; the first ones sent are kept. */
        const val MAX_FOLDERS = 10
    }
}

@Serializable
data class FolderSuggestionDto(
    val folderId: String,
    val probability: Double,
    /** The folder's name, so the app can show a folder it did not send itself. */
    val folderName: String = "",
)

@Serializable
data class FolderSuggestionsResponse(
    /** The folders the document belongs to, most likely first. Empty when none fits. */
    val suggestions: List<FolderSuggestionDto> = emptyList(),
    val error: String? = null,
)
