package io.writeopia.writingagent.model

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope

data class WritingSuggestion(
    val action: WritingSuggestionAction,
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
) {
    val hasSuggestions: Boolean get() = suggestions.isNotEmpty()

    companion object {
        fun empty() = WritingSuggestionsState()
    }
}
