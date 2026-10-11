package io.writeopia.app.dto.writingagent

import kotlinx.serialization.Serializable

/** A folder the document could move to. The name is what Jev reads; the id is for the app. */
@Serializable
data class FolderCandidate(
    val id: String,
    val name: String,
)

/**
 * A document that sits in the root folder, and the folders of the workspace it could belong to.
 * The backend asks Jev about each folder and answers with the ones that fit.
 */
@Serializable
data class FolderSuggestionsRequest(
    val documentTitle: String = "",
    val documentText: String = "",
    val folders: List<FolderCandidate> = emptyList(),
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
)

@Serializable
data class FolderSuggestionsResponse(
    /** The folders the document belongs to, most likely first. Empty when none fits. */
    val suggestions: List<FolderSuggestionDto> = emptyList(),
    val error: String? = null,
)
