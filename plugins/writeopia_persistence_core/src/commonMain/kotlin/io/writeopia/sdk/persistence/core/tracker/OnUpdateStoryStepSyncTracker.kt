@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.manager.StoryStepSyncTracker
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.core.sync.StoryStepSyncBuffer
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implementation of [StoryStepSyncTracker] that syncs StorySteps with a backend
 * using a buffered approach to avoid excessive network requests.
 *
 * @param syncBuffer The buffer for collecting and batching changes.
 * @param syncApi A function that performs the actual sync with the backend.
 * @param onServerUpdate Callback invoked when server updates should be applied locally.
 */
@OptIn(FlowPreview::class)
class OnUpdateStoryStepSyncTracker(
    private val syncBuffer: StoryStepSyncBuffer = StoryStepSyncBuffer(),
    private val syncApi: suspend (StoryStepSyncRequest) -> StoryStepSyncResponse,
    private val onServerUpdate: suspend (List<Pair<Double, StoryStep>>, List<String>) -> Unit = { _, _ -> }
) : StoryStepSyncTracker {

    private var lastSyncTimestamp: Long = 0L

    override suspend fun syncStorySteps(
        documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
        workspaceIdFlow: Flow<String>
    ) {
        // Combine flows to get both story state and workspace context
        val combinedFlow = combine(
            documentEditionFlow,
            workspaceIdFlow
        ) { (storyState, documentInfo), workspaceId ->
            Triple(storyState, documentInfo, workspaceId)
        }

        // Collect changes and add to buffer
        kotlinx.coroutines.coroutineScope {
            // Launch a coroutine to process document changes
            kotlinx.coroutines.launch {
                combinedFlow.collect { (storyState, documentInfo, workspaceId) ->
                    processLastEdit(storyState.lastEdit, documentInfo.id)
                }
            }

            // Launch a coroutine to handle sync triggers
            kotlinx.coroutines.launch {
                syncBuffer.syncTrigger
                    .debounce(syncBuffer.syncInterval)
                    .collect {
                        if (syncBuffer.hasPendingChanges()) {
                            val (storyState, documentInfo) = documentEditionFlow.first()
                            val workspaceId = workspaceIdFlow.first()
                            performSync(documentInfo.id, workspaceId)
                        }
                    }
            }
        }
    }

    private fun processLastEdit(lastEdit: LastEdit, documentId: String) {
        when (lastEdit) {
            is LastEdit.LineEdition -> {
                if (!lastEdit.storyStep.ephemeral) {
                    syncBuffer.addChange(
                        storyStep = lastEdit.storyStep.copy(
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        ),
                        position = lastEdit.position,
                        documentId = documentId
                    )
                }
            }

            is LastEdit.BulkEdition -> {
                lastEdit.steps
                    .filter { (_, step) -> !step.ephemeral }
                    .forEach { (position, step) ->
                        syncBuffer.addChange(
                            storyStep = step.copy(
                                lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                            ),
                            position = position,
                            documentId = documentId
                        )
                    }
            }

            is LastEdit.LineBreakEdition -> {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                if (!lastEdit.originalStep.second.ephemeral) {
                    syncBuffer.addChange(
                        storyStep = lastEdit.originalStep.second.copy(lastUpdatedAt = timestamp),
                        position = lastEdit.originalStep.first,
                        documentId = documentId
                    )
                }
                if (!lastEdit.newStep.second.ephemeral) {
                    syncBuffer.addChange(
                        storyStep = lastEdit.newStep.second.copy(lastUpdatedAt = timestamp),
                        position = lastEdit.newStep.first,
                        documentId = documentId
                    )
                }
            }

            is LastEdit.InfoEdition -> {
                if (!lastEdit.storyStep.ephemeral) {
                    syncBuffer.addChange(
                        storyStep = lastEdit.storyStep.copy(
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        ),
                        position = lastEdit.position,
                        documentId = documentId
                    )
                }
            }

            is LastEdit.DeleteEdition -> {
                syncBuffer.addDeletion(lastEdit.deletedId)
            }

            is LastEdit.EraseEdition -> {
                syncBuffer.addDeletion(lastEdit.deletedId)
                val (position, step) = lastEdit.updatedStep
                if (!step.ephemeral) {
                    syncBuffer.addChange(
                        storyStep = step.copy(
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        ),
                        position = position,
                        documentId = documentId
                    )
                }
            }

            is LastEdit.BulkDeleteEdition -> {
                lastEdit.deletedIds.forEach { id ->
                    syncBuffer.addDeletion(id)
                }
                // Also sync any updated steps (e.g., position references)
                lastEdit.updatedSteps.forEach { (position, step) ->
                    if (!step.ephemeral) {
                        syncBuffer.addChange(
                            storyStep = step.copy(
                                lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                            ),
                            position = position,
                            documentId = documentId
                        )
                    }
                }
            }

            LastEdit.Whole, LastEdit.Nothing, LastEdit.Metadata -> {
                // These don't trigger step-level syncing
            }
        }
    }

    private suspend fun performSync(documentId: String, workspaceId: String) {
        val batch = syncBuffer.consumeChanges()
        if (batch.isEmpty) return

        val requestTimestamp = Clock.System.now().toEpochMilliseconds()

        val request = StoryStepSyncRequest(
            documentId = documentId,
            workspaceId = workspaceId,
            lastSyncTimestamp = lastSyncTimestamp,
            requestTimestamp = requestTimestamp,
            changes = batch.changes.map { change ->
                StoryStepChangeApi(
                    storyStep = change.storyStep.toApi(change.position),
                    position = change.position
                )
            },
            deletions = batch.deletions.toList()
        )

        try {
            val response = syncApi(request)

            // Update last sync timestamp
            lastSyncTimestamp = response.serverTimestamp

            // Apply server updates only if they are newer than local
            val serverSteps = response.updatedSteps.map { stepApi ->
                stepApi.position to stepApi.toModel()
            }

            if (serverSteps.isNotEmpty() || response.deletedIds.isNotEmpty()) {
                onServerUpdate(serverSteps, response.deletedIds)
            }
        } catch (e: Exception) {
            // On failure, re-add changes to buffer for retry
            batch.changes.forEach { change ->
                syncBuffer.addChange(change.storyStep, change.position, change.documentId)
            }
            batch.deletions.forEach { id ->
                syncBuffer.addDeletion(id)
            }
        }
    }
}
