package io.writeopia.writingagent.tags

import io.writeopia.app.dto.writingagent.TagCandidate
import io.writeopia.sdk.models.document.DocumentTag

/**
 * What the agent needs to tag the open document: which tags it has, which it could have, and how
 * to add them. The screen's ViewModel provides this; the agent decides when to offer it.
 */
interface DocumentTags {
    /** The ids of the tags the open document carries right now. */
    fun currentTagIds(): Set<String>

    /** The workspace tags the document could carry, at most [limit], by name. */
    suspend fun candidates(limit: Int): List<TagCandidate>

    /** Adds the tags with [tagIds] to the open document. */
    suspend fun addTags(tagIds: Set<String>)

    /**
     * Makes a tag for the document, named and explained by the cloud AI from its title and text,
     * stores it in the catalogue and adds it to the document. The result is the new tag.
     */
    suspend fun createTagWithAi(documentTitle: String, documentText: String): Result<DocumentTag>
}
