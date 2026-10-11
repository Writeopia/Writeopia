package io.writeopia.writingagent.model

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope

data class WritingSuggestion(
    val action: WritingSuggestionAction,
    val probability: Double,
)

/** A folder the document belongs to, by Jev's judgment. */
data class FolderSuggestion(
    val folderId: String,
    val name: String,
    val probability: Double,
)

/**
 * What the writing agent offers right now. [storyStepId] is the block the suggestions are about:
 * the paragraph being written, or the title when they are about the whole document. The ID is
 * kept instead of the position because the actions insert blocks, which moves every position.
 */
data class WritingSuggestionsState(
    val storyStepId: String? = null,
    val scope: WritingSuggestionScope? = null,
    val suggestions: List<WritingSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    /** The folders a document created in the root could move to. */
    val folderSuggestions: List<FolderSuggestion> = emptyList(),
    /** True when the agent offers to make a new folder, named by the AI, and move the document there. */
    val offerNewFolder: Boolean = false,
) {
    val hasSuggestions: Boolean
        get() = suggestions.isNotEmpty() || folderSuggestions.isNotEmpty() || offerNewFolder

    companion object {
        fun empty() = WritingSuggestionsState()
    }
}
