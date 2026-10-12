package io.writeopia.app.dto.writingagent

import kotlinx.serialization.Serializable

/** A tag the document could carry. The name and the description are what Jev reads; the id is for the app. */
@Serializable
data class TagCandidate(
    val id: String,
    val name: String,
    val description: String = "",
)

/**
 * A document without tags, and the tags of the workspace catalogue it could carry. The backend asks
 * Jev about each tag and answers with the ones that fit.
 */
@Serializable
data class TagSuggestionsRequest(
    val documentTitle: String = "",
    val documentText: String = "",
    val tags: List<TagCandidate> = emptyList(),
) {
    companion object {
        /** How many tags are asked about at most; the first ones sent are kept. */
        const val MAX_TAGS = 30
    }
}

@Serializable
data class TagSuggestionDto(
    val tagId: String,
    val probability: Double,
)

@Serializable
data class TagSuggestionsResponse(
    /** The tags the document should carry, most likely first. Empty when none fits. */
    val suggestions: List<TagSuggestionDto> = emptyList(),
    val error: String? = null,
)

/** A document for which the cloud AI makes up a new tag, avoiding the names the catalogue already has. */
@Serializable
data class TagCreateRequest(
    val documentTitle: String = "",
    val documentText: String = "",
    val existingTagNames: List<String> = emptyList(),
)

@Serializable
data class TagCreateResponse(
    val name: String = "",
    val description: String = "",
    val error: String? = null,
)
