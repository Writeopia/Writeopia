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
 * The dialog that files a document sitting in the root folder, opened from the box. It shows a
 * spinner while the backend judges the folders, then the ones that fit to pick from, and the
 * option of a new folder named by the AI.
 */
data class FolderDialogState(
    /** True while the backend ranks the folders. */
    val isLoading: Boolean = false,
    /** The folders above the threshold, best first. Empty when none fits, the workspace has none, or the backend failed. */
    val options: List<FolderSuggestion> = emptyList(),
    /** True while the AI names and creates the new folder. */
    val isCreating: Boolean = false,
)

/** A tag of the workspace catalogue the document should carry, by Jev's judgment. */
data class TagSuggestion(
    val tagId: String,
    val name: String,
    val description: String,
    val probability: Double,
)

/**
 * The dialog that tags a document without tags, opened from the box. It shows a spinner while the
 * backend judges the catalogue, then the tags that fit, pre-selected, to add at once, and the
 * option of a new tag made up by the AI.
 */
data class TagDialogState(
    /** True while the backend ranks the catalogue. */
    val isLoading: Boolean = false,
    /** The tags above the threshold, best first. Empty when none fits, the catalogue is empty, or the backend failed. */
    val options: List<TagSuggestion> = emptyList(),
    /** The ids of the options the user keeps selected; all of them to start with. */
    val selected: Set<String> = emptySet(),
    /** True while the AI makes up and adds the new tag. */
    val isCreating: Boolean = false,
    /** True when the workspace has no tag to offer at all: the dialog only offers a new one. */
    val catalogueIsEmpty: Boolean = false,
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
    /** True when the box offers to move the document, which sits in the root, into a folder. */
    val offerFolder: Boolean = false,
    /** The dialog filing the document, open after the user took the folder offer; null otherwise. */
    val folderDialog: FolderDialogState? = null,
    /** True when the box offers to tag the document, which has no tags yet. */
    val offerTags: Boolean = false,
    /** The dialog tagging the document, open after the user took the tag offer; null otherwise. */
    val tagDialog: TagDialogState? = null,
) {
    /** True when the box has something to show. The dialogs are shown apart from the box. */
    val hasSuggestions: Boolean get() = suggestions.isNotEmpty() || offerFolder || offerTags

    /** How many rows the box offers: the badge of the collapsed box shows this. */
    val count: Int get() = suggestions.size + (if (offerFolder) 1 else 0) + (if (offerTags) 1 else 0)

    companion object {
        fun empty() = WritingSuggestionsState()
    }
}
