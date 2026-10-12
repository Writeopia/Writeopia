@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.document.DocumentTag
import kotlin.time.ExperimentalTime

/**
 * Merges the workspace tag catalogue with the one the backend sent: the copy updated last wins,
 * like folders. A tag deleted locally is never brought back by the backend; its tombstone is sent
 * instead, so the other clients learn about the removal.
 */
class DocumentTagConflictHandler(
    private val documentTagRepository: DocumentTagRepository
) {

    /**
     * @param localTags The outdated local tags, tombstones included
     * @param externalTags The tags from the backend
     * @return The tags to send to the backend: the local winners and the local-only tags
     */
    suspend fun handleConflict(
        localTags: List<DocumentTag>,
        externalTags: List<DocumentTag>,
    ): List<DocumentTag> {
        val localById = localTags.associateBy { it.id }
        val toSend = mutableListOf<DocumentTag>()

        externalTags.forEach { external ->
            val outdatedLocal = localById[external.id]
            val existing = outdatedLocal ?: documentTagRepository.getTagById(external.id)

            when {
                existing == null -> documentTagRepository.upsertTag(external.copy(lastSyncedAt = external.lastUpdatedAt))

                // The local change is newer, deletion included: it goes up, not down.
                existing.lastUpdatedAt >= external.lastUpdatedAt -> {
                    if (outdatedLocal != null) toSend.add(outdatedLocal)
                }

                else -> documentTagRepository.upsertTag(external.copy(lastSyncedAt = external.lastUpdatedAt))
            }
        }

        val externalIds = externalTags.map { it.id }.toSet()
        localTags.filter { tag -> tag.id !in externalIds }.forEach(toSend::add)

        return toSend
    }
}
