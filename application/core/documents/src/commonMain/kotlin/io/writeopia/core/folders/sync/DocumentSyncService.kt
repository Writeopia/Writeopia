@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Syncs documents with the backend one [StoryStep] at a time. The folder and workspace syncs
 * and the summary generation all go through here, so a document is never replaced whole on
 * either side.
 *
 * Sending, in this order:
 * 1. [sendDeletedSteps]: the steps deleted here go first, as ids only, for the whole workspace.
 *    The backend deletes them and keeps the tombstones for the other devices; once it has
 *    them, they are dropped here.
 * 2. [syncDocument]: the steps edited since the document was last sent. The backend keeps
 *    the newer copy of each step, so an older edit never erases a newer one. Once sent, the
 *    document is stamped as synced at the time taken before the request: whatever was edited
 *    meanwhile is still newer than that and goes with the next sync.
 *
 * Receiving:
 * 3. [receiveDeletedSteps]: the steps deleted on other devices since the last sync, as ids.
 * 4. [mergeReceivedDocument]: the steps changed on other devices, merged step by step; a step
 *    deleted here is never written back.
 */
class DocumentSyncService(
    private val documentRepository: DocumentRepository,
    private val syncApi: suspend (StoryStepSyncRequest) -> StoryStepSyncResponse,
    private val documentsApi: DocumentsApi? = null,
    private val documentMerger: DocumentMerger = DocumentMerger(),
) {

    /**
     * Sends the deletions pending in the workspace, in batches, and forgets the ones the
     * backend accepted.
     *
     * @return the ids sent, so a listing fetched before they went doesn't bring the steps back;
     * null when some could not be sent (they stay pending).
     */
    suspend fun sendDeletedSteps(workspaceId: String): Set<String>? {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return null
        val api = documentsApi ?: return emptySet()

        val tombstones = documentRepository.loadStoryStepTombstones(workspaceId)
        if (tombstones.isEmpty()) return emptySet()

        for (batch in tombstones.chunked(MAX_TOMBSTONES_PER_BATCH)) {
            val result = try {
                api.deleteStorySteps(batch, workspaceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error sending deleted steps: ${e.message}")
                return null
            }
            if (result !is ResultData.Complete) return null

            documentRepository.deleteStoryStepTombstones(batch.map { tombstone -> tombstone.stepId })
        }

        return tombstones.map { tombstone -> tombstone.stepId }.toSet()
    }

    /**
     * Sends the steps of a document that were edited since it was last sent, then stamps it
     * as synced. Nothing is sent when it is already in sync.
     *
     * @return false when the document is unknown or the backend could not be reached.
     */
    suspend fun syncDocument(documentId: String, workspaceId: String): Boolean {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return false

        val document = documentRepository.loadDocumentById(documentId, workspaceId) ?: return false
        return syncDocumentSteps(document)
    }

    /** [syncDocument] for a document already loaded with its content. */
    suspend fun syncDocumentSteps(document: Document): Boolean {
        val workspaceId = document.workspaceId
        if (workspaceId == Workspace.disconnectedWorkspace().id) return false

        val lastSyncTimestamp = document.lastSyncedAt?.toEpochMilliseconds() ?: 0L
        val outdatedSteps = getOutdatedStorySteps(document, lastSyncTimestamp)

        // In sync already. lastSyncedAt stays: there is no newer server time to move it to.
        if (outdatedSteps.isEmpty()) return true

        // The time of the send. Every step edited after it is newer than the stamp below.
        val requestTimestamp = Clock.System.now().toEpochMilliseconds()
        var currentSyncTimestamp = lastSyncTimestamp

        // Large documents (a first sync, say) go in batches: smaller requests, more reliable.
        for ((batchIndex, batch) in outdatedSteps.chunked(MAX_STEPS_PER_BATCH).withIndex()) {
            val request = StoryStepSyncRequest(
                documentId = document.id,
                workspaceId = workspaceId,
                lastSyncTimestamp = currentSyncTimestamp,
                requestTimestamp = requestTimestamp,
                changes = batch.map { (position, storyStep) ->
                    StoryStepChangeApi(storyStep = storyStep.toApi(position), position = position)
                },
                deletions = emptyList()
            )

            val response = try {
                syncApi(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error syncing document ${document.id} (batch $batchIndex): ${e.message}")
                return false
            }

            // What the backend had that this device didn't; once, the first answer has it all.
            if (batchIndex == 0) applyServerUpdates(document, response)
            currentSyncTimestamp = response.serverTimestamp
        }

        stampSent(document, outdatedSteps, Instant.fromEpochMilliseconds(requestTimestamp))
        return true
    }

    /**
     * Removes the steps deleted on other devices since [since]. No tombstone is kept for them:
     * the backend has one.
     *
     * @return their ids, so a listing fetched before doesn't bring them back; null when the
     * backend could not be reached.
     */
    suspend fun receiveDeletedSteps(workspaceId: String, since: Instant): Set<String>? {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return null
        val api = documentsApi ?: return emptySet()

        val result = try {
            api.getDeletedStoryStepIds(workspaceId, since)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Error receiving deleted steps: ${e.message}")
            return null
        }
        if (result !is ResultData.Complete) return null

        documentRepository.deleteStoryStepsByIds(result.data, workspaceId)
        return result.data.toSet()
    }

    /**
     * Merges a document received from the backend into the local copy, step by step: a step
     * only there is added, a step newer there replaces the local one, the rest stays. The steps
     * deleted here and not yet sent are left out, like the ones in [excluding] (deleted just
     * now, here or elsewhere, after the listing was fetched). A document deleted here is not
     * brought back.
     *
     * @return true when something was written.
     */
    suspend fun mergeReceivedDocument(received: Document, excluding: Set<String> = emptySet()): Boolean {
        val workspaceId = received.workspaceId
        val local = documentRepository.loadDocumentById(received.id, workspaceId)
        if (local?.deleted == true) return false

        val tombstoned = documentRepository.loadStoryStepTombstones(workspaceId)
            .map { tombstone -> tombstone.stepId }
            .toSet()
        val incoming = received.copy(content = received.content.withoutSteps(tombstoned + excluding))

        if (local == null) {
            documentRepository.saveDocument(incoming)
            return true
        }

        val merged = documentMerger.merge(local, incoming) ?: return false
        val localById = local.content.values.associateBy { step -> step.id }
        // The merger hands back the local instance when it keeps it, so the rest came from the backend.
        val stepsToWrite = merged.content.entries
            .filter { (_, step) -> step != localById[step.id] }
            .map { (position, step) -> position to step }

        val annotationsChanged = merged.commentConversations != local.commentConversations ||
            merged.tagIds != local.tagIds
        val backendIsNewer = incoming.lastUpdatedAt > local.lastUpdatedAt

        if (stepsToWrite.isEmpty() && !annotationsChanged && !backendIsNewer) return false

        val localSyncedAt = local.lastSyncedAt
        val localOutdated = localSyncedAt == null || local.lastUpdatedAt > localSyncedAt
        val metadata = if (localOutdated) {
            // Still to be sent: it keeps looking outdated, with its own timestamps.
            merged.copy(lastUpdatedAt = local.lastUpdatedAt, lastSyncedAt = local.lastSyncedAt)
        } else {
            // Nothing pending here: in sync with what was just received.
            merged.copy(lastSyncedAt = Clock.System.now())
        }

        if (annotationsChanged) {
            // Comments and tags are saved with the document; the content is the merged one.
            documentRepository.saveDocument(metadata)
        } else {
            documentRepository.saveDocumentMetadata(metadata)
            if (stepsToWrite.isNotEmpty()) documentRepository.saveStorySteps(stepsToWrite, local.id)
        }

        return true
    }

    /** [mergeReceivedDocument] for each document. */
    suspend fun mergeReceivedDocuments(received: List<Document>, excluding: Set<String> = emptySet()) {
        received.forEach { document -> mergeReceivedDocument(document, excluding) }
    }

    /**
     * The steps edited since the document was last sent, by position. Everything when it was
     * never sent (the caller batches them).
     */
    private fun getOutdatedStorySteps(
        document: Document,
        lastSyncTimestamp: Long
    ): List<Pair<Double, StoryStep>> =
        document.content.entries
            .filter { (_, storyStep) ->
                val stepLastUpdated = storyStep.lastUpdatedAt ?: 0L
                stepLastUpdated > lastSyncTimestamp || lastSyncTimestamp == 0L
            }
            .map { (position, storyStep) -> position to storyStep }
            .sortedBy { it.first }

    /**
     * What the backend answered with: its deletions, then its steps newer than the local ones.
     */
    private suspend fun applyServerUpdates(document: Document, response: StoryStepSyncResponse) {
        if (response.deletedIds.isNotEmpty()) {
            documentRepository.deleteStoryStepsByIds(response.deletedIds, document.workspaceId)
        }

        val serverSteps = response.updatedSteps.map { stepApi -> stepApi.position to stepApi.toModel() }
        if (serverSteps.isEmpty()) return

        val tombstoned = documentRepository.loadStoryStepTombstones(document.workspaceId)
            .map { tombstone -> tombstone.stepId }
            .toSet()
        val localById = document.content.values.associateBy { step -> step.id }
        val newerSteps = serverSteps.filter { (_, serverStep) ->
            if (serverStep.id in tombstoned) return@filter false
            val localStep = localById[serverStep.id] ?: return@filter true
            val localTimestamp = localStep.lastUpdatedAt ?: Long.MAX_VALUE
            (serverStep.lastUpdatedAt ?: 0L) > localTimestamp
        }

        if (newerSteps.isNotEmpty()) documentRepository.saveStorySteps(newerSteps, document.id)
    }

    /**
     * The document is in sync as of [sentAt]: that goes on the document, and on the steps sent,
     * so they are no longer newer than it. A step edited since keeps its newer time.
     */
    private suspend fun stampSent(document: Document, sent: List<Pair<Double, StoryStep>>, sentAt: Instant) {
        val current = documentRepository.loadDocumentById(document.id, document.workspaceId) ?: return
        documentRepository.saveDocumentMetadata(current.copy(lastSyncedAt = sentAt))

        val sentById = sent.associate { (_, step) -> step.id to step.lastUpdatedAt }
        val stamped = current.content.entries
            .filter { (_, step) -> step.id in sentById && step.lastUpdatedAt == sentById[step.id] }
            .map { (position, step) -> position to step.copy(lastUpdatedAt = sentAt.toEpochMilliseconds()) }
        if (stamped.isNotEmpty()) documentRepository.saveStorySteps(stamped, document.id)
    }

    private fun Map<Double, StoryStep>.withoutSteps(ids: Set<String>): Map<Double, StoryStep> =
        if (ids.isEmpty()) this else filterNot { (_, step) -> step.id in ids }

    companion object {
        /** Steps per request: keeps the payload small and the request reliable. */
        private const val MAX_STEPS_PER_BATCH = 50

        /** Ids per deletion request. */
        private const val MAX_TOMBSTONES_PER_BATCH = 500

        fun create(
            documentRepository: DocumentRepository,
            syncApi: suspend (StoryStepSyncRequest) -> StoryStepSyncResponse,
            documentsApi: DocumentsApi? = null,
        ): DocumentSyncService = DocumentSyncService(
            documentRepository = documentRepository,
            syncApi = syncApi,
            documentsApi = documentsApi,
        )
    }
}
