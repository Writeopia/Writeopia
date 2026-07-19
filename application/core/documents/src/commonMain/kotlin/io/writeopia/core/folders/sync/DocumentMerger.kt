@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import kotlin.time.ExperimentalTime

/**
 * Merges two versions of a document at the StoryStep level, preferring newer StorySteps
 * based on their lastUpdatedAt timestamps.
 */
class DocumentMerger {

    /**
     * Merges a local document with a backend document.
     *
     * The merge logic:
     * - StorySteps only in local → keep local
     * - StorySteps only in backend → keep backend
     * - StorySteps in both → compare lastUpdatedAt, keep newer (local wins on tie or null timestamps)
     * - Document metadata uses the newer document's metadata
     *
     * @param localDocument The document from the local database (can be null)
     * @param backendDocument The document from the backend (can be null)
     * @return The merged document, or null if both inputs are null
     */
    fun merge(localDocument: Document?, backendDocument: Document?): Document? {
        return when {
            localDocument == null && backendDocument == null -> null
            localDocument == null -> backendDocument
            backendDocument == null -> localDocument
            else -> mergeDocuments(localDocument, backendDocument)
        }
    }

    private fun mergeDocuments(localDocument: Document, backendDocument: Document): Document {
        val mergedContent = mergeContent(localDocument.content, backendDocument.content)

        // Use the metadata from the newer document
        val baseDocument = if (localDocument.lastUpdatedAt >= backendDocument.lastUpdatedAt) {
            localDocument
        } else {
            backendDocument
        }

        return baseDocument.copy(content = mergedContent)
    }

    private fun mergeContent(
        localContent: Map<Double, StoryStep>,
        backendContent: Map<Double, StoryStep>
    ): Map<Double, StoryStep> {
        // Build maps from StoryStep ID to (position, StoryStep) for lookup
        val localById = localContent.entries.associate { (pos, step) -> step.id to (pos to step) }
        val backendById = backendContent.entries.associate { (pos, step) -> step.id to (pos to step) }

        val allStepIds = localById.keys + backendById.keys
        val mergedSteps = mutableListOf<Pair<Double, StoryStep>>()

        for (stepId in allStepIds) {
            val localEntry = localById[stepId]
            val backendEntry = backendById[stepId]

            val (position, step) = when {
                localEntry == null && backendEntry != null -> backendEntry
                localEntry != null && backendEntry == null -> localEntry
                localEntry != null && backendEntry != null -> {
                    // Both exist - compare lastUpdatedAt, local wins on tie or null
                    val localTimestamp = localEntry.second.lastUpdatedAt ?: Long.MAX_VALUE
                    val backendTimestamp = backendEntry.second.lastUpdatedAt ?: 0L

                    if (localTimestamp >= backendTimestamp) {
                        localEntry
                    } else {
                        backendEntry
                    }
                }
                else -> continue // Both null, shouldn't happen
            }

            mergedSteps.add(position to step)
        }

        // Sort by position and rebuild the map
        return mergedSteps.sortedBy { it.first }.toMap()
    }
}
