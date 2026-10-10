package io.writeopia.app.dto.writingagent

import kotlinx.serialization.Serializable

/**
 * What the writing agent can offer. Each action maps to something the editor can do: insert a
 * block, change a block, or generate text with the AI the user picked.
 */
@Serializable
enum class WritingSuggestionAction {
    /** A code block right below the paragraph. */
    CODE_BLOCK,

    /** A bullet list right below the paragraph. */
    LIST,

    /** A checklist right below the paragraph. */
    CHECK_LIST,

    /** A new document, linked right below the paragraph. */
    NEW_DOCUMENT_LINK,

    /** An image right below the paragraph. */
    IMAGE,

    /** A free drawing. */
    DRAWING,

    /** A table filled with the data of the paragraph. */
    SPREADSHEET,

    /** A section heading (like a Markdown `#`, `##` or `###` line) above the paragraph. */
    SECTION_HEADING,

    /**
     * A title for the whole document: the title block drawn in the header. Decided by the app,
     * never asked to Jev: it is offered whenever the document has text but no title.
     */
    DOCUMENT_TITLE,

    /** The paragraph highlighted as a callout or a quote. */
    CALLOUT,

    /** A TL;DR at the top of the document. */
    TLDR,

    /** A conclusion at the end of the document. */
    CONCLUSION;

    companion object {
        /** The actions offered while the user writes a paragraph. */
        fun whileWriting(): List<WritingSuggestionAction> = listOf(
            CODE_BLOCK,
            LIST,
            CHECK_LIST,
            NEW_DOCUMENT_LINK,
            IMAGE,
            DRAWING,
            SPREADSHEET,
            SECTION_HEADING,
            CALLOUT,
        )

        /** The actions offered when the user opens a document. */
        fun onDocumentOpened(): List<WritingSuggestionAction> = listOf(TLDR, CONCLUSION)

        /** The actions the app decides on its own, from the state of the document. */
        fun decidedLocally(): List<WritingSuggestionAction> = listOf(DOCUMENT_TITLE)

        fun forScope(scope: WritingSuggestionScope): List<WritingSuggestionAction> =
            when (scope) {
                WritingSuggestionScope.WRITING -> whileWriting()
                WritingSuggestionScope.DOCUMENT_OPENED -> onDocumentOpened()
            }
    }
}

/** When the suggestions are asked for: while writing a paragraph, or when a document opens. */
@Serializable
enum class WritingSuggestionScope {
    WRITING,
    DOCUMENT_OPENED,
}

@Serializable
data class WritingSuggestionsRequest(
    val scope: WritingSuggestionScope,
    /** The paragraph being written. Empty when [scope] is [WritingSuggestionScope.DOCUMENT_OPENED]. */
    val text: String = "",
    /** The type of the block being written, like "message" or "check_item". */
    val blockType: String? = null,
    val documentTitle: String? = null,
    /** The text of the whole document, possibly truncated. */
    val documentText: String? = null,
)

@Serializable
data class WritingSuggestionDto(
    val action: WritingSuggestionAction,
    /** The probability, from 0 to 1, that the writer wants this action. */
    val probability: Double,
)

@Serializable
data class WritingSuggestionsResponse(
    /** The winning suggestions, best first. Empty when nothing passes the threshold. */
    val suggestions: List<WritingSuggestionDto> = emptyList(),
    val error: String? = null,
)
