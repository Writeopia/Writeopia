@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.models.document

import io.writeopia.sdk.models.id.GenerateId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A tag of the workspace catalogue: a short name and the explanation of what it stands for.
 * Documents refer to tags by id (see [Document.tagIds]); many documents share one tag.
 *
 * Not to be confused with [io.writeopia.sdk.models.story.Tag], which is the formatting of a block.
 */
data class DocumentTag(
    val id: String = GenerateId.generate(),
    val workspaceId: String,
    val name: String,
    val description: String = "",
    val createdAt: Instant,
    val lastUpdatedAt: Instant,
    val lastSyncedAt: Instant? = null,
    /** A tombstone: the tag was removed and should not be offered, but the removal still syncs. */
    val deleted: Boolean = false,
) {
    /** True when the local copy changed since it was last sent to the backend. */
    val isOutdated: Boolean
        get() = lastSyncedAt == null || lastUpdatedAt > lastSyncedAt

    companion object {
        fun create(name: String, description: String, workspaceId: String): DocumentTag {
            val now = Clock.System.now()

            return DocumentTag(
                workspaceId = workspaceId,
                name = name.trim(),
                description = description.trim(),
                createdAt = now,
                lastUpdatedAt = now,
            )
        }
    }
}
