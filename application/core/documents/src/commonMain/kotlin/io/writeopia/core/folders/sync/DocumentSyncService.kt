@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.PendingFavorite
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.sorting.OrderBy
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
 * 2. [syncDocuments]: for each document changed here, the steps edited since it was last
 *    sent (the backend keeps the newer copy of each step, so an older edit never erases a
 *    newer one), then the metadata of all of them in one request. Only then are they stamped
 *    as synced, at the time taken before the send: whatever was edited meanwhile is still
 *    newer than that and goes with the next sync.
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
     * Sends a document changed here: its edited steps, then its metadata (title, folder, icon,
     * tags), then stamps it as synced. Nothing is sent when it is already in sync.
     *
     * @return false when the document is unknown or the backend could not be reached.
     */
    suspend fun syncDocument(documentId: String, workspaceId: String): Boolean {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return false

        val document = documentRepository.loadDocumentById(documentId, workspaceId) ?: return false
        return syncDocuments(listOf(document), workspaceId)?.isNotEmpty() == true
    }

    /**
     * Sends the documents changed here, already loaded with their content. For each one its
     * edited steps go first, one request per batch; then the metadata of all of them goes in
     * one request, without content; only then are they stamped as synced, at the time taken
     * before anything was sent, so an edit made meanwhile is still newer and goes next time.
     * Nothing is stamped while the metadata is not on the backend: a rename or a move would be
     * lost otherwise, and the steps sent again are harmless (the backend keeps the newer copy).
     *
     * A document whose steps could not be sent is left out, to be tried again next time.
     *
     * @return the documents now in sync; null when the metadata could not be sent.
     */
    suspend fun syncDocuments(documents: List<Document>, workspaceId: String): List<Document>? {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return null
        if (documents.isEmpty()) return emptyList()

        // The time of the send. Every step edited after it is newer than the stamp below.
        val sentAt = Clock.System.now().toEpochMilliseconds()

        val sentSteps = mutableMapOf<String, List<Pair<String, StoryStep>>>()
        val toSend = documents.filter { document ->
            sendSteps(document, sentAt)?.also { steps -> sentSteps[document.id] = steps } != null
        }
        if (toSend.isEmpty()) return emptyList()

        documentsApi?.let { api ->
            val result = try {
                api.sendDocuments(toSend.map { document -> document.withoutContent() }, workspaceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error sending documents: ${e.message}")
                return null
            }
            if (result !is ResultData.Complete) return null
        }

        val stampedAt = Instant.fromEpochMilliseconds(sentAt)
        toSend.forEach { document -> stampSent(document, sentSteps.getValue(document.id), stampedAt) }
        return toSend
    }

    /**
     * Sends the steps of [document] edited since it was last sent, in batches, with
     * [requestTimestamp] as their edit time on the backend.
     *
     * @return the steps sent (none when the document was already in sync); null when the
     * backend could not be reached.
     */
    private suspend fun sendSteps(document: Document, requestTimestamp: Long): List<Pair<String, StoryStep>>? {
        val lastSyncTimestamp = document.lastSyncedAt?.toEpochMilliseconds() ?: 0L
        val outdatedSteps = getOutdatedStorySteps(document, lastSyncTimestamp)
        if (outdatedSteps.isEmpty()) return outdatedSteps

        var currentSyncTimestamp = lastSyncTimestamp

        // Large documents (a first sync, say) go in batches: smaller requests, more reliable.
        for ((batchIndex, batch) in outdatedSteps.chunked(MAX_STEPS_PER_BATCH).withIndex()) {
            val request = StoryStepSyncRequest(
                documentId = document.id,
                workspaceId = document.workspaceId,
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
                return null
            }

            // What the backend had that this device didn't; once, the first answer has it all.
            if (batchIndex == 0) applyServerUpdates(document, response)
            currentSyncTimestamp = response.serverTimestamp
        }

        return outdatedSteps
    }

    /**
     * Removes the steps deleted on other devices since [since]. No tombstone is kept for them:
     * the backend has one.
     *
     * @return their ids, so a listing fetched before doesn't bring them back, and the backend's
     * time before it looked, to ask from next time (its clock, not this device's: a deletion
     * made while the clocks disagree would be skipped for good otherwise); null when the
     * backend could not be reached.
     */
    suspend fun receiveDeletedSteps(workspaceId: String, since: Instant): ReceivedDeletions? {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return null
        val api = documentsApi ?: return ReceivedDeletions(emptySet(), since)

        val result = try {
            api.getDeletedStoryStepIds(workspaceId, since)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Error receiving deleted steps: ${e.message}")
            return null
        }
        if (result !is ResultData.Complete) return null

        documentRepository.deleteStoryStepsByIds(result.data.stepIds, workspaceId)
        return ReceivedDeletions(
            stepIds = result.data.stepIds.toSet(),
            serverTimestamp = Instant.fromEpochMilliseconds(result.data.serverTimestamp)
        )
    }

    /** What [receiveDeletedSteps] brought: the ids, and where the next request starts from. */
    data class ReceivedDeletions(val stepIds: Set<String>, val serverTimestamp: Instant)

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
        // The favorite is per user and comes apart ([receiveFavorites]): what the listing
        // carries is not this user's, so the local mark stays whichever copy is newer.
        val metadata = if (localOutdated) {
            // Still to be sent: it keeps looking outdated, with its own timestamps.
            merged.copy(lastUpdatedAt = local.lastUpdatedAt, lastSyncedAt = local.lastSyncedAt, favorite = local.favorite)
        } else {
            // Nothing pending here: in sync with what was just received.
            merged.copy(lastSyncedAt = Clock.System.now(), favorite = local.favorite)
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
     * Makes the favorites of the workspace those of the backend. They are per user and kept
     * apart from the documents there, so a document favorited elsewhere doesn't come back
     * changed with the listing: its id is asked for apart and the local copy marked.
     *
     * A favorite changed here is recorded ([PendingFavorite]) and sent right away, but it may
     * not be there yet: the request failed (the document itself wasn't on the backend), was
     * cancelled with the screen, or is still on its way while this runs. The documents with a
     * record keep their favorite instead of losing it to the backend, and the record is sent
     * from here ([sendPendingFavorites]), to stay until the backend has it. The backend wins
     * for every other document, which is how a change made on another device arrives; a
     * document edited here is no exception, since an edit says nothing about the favorite.
     *
     * @return false when the backend could not be asked; the favorites stay as they are.
     */
    suspend fun receiveFavorites(workspaceId: String): Boolean {
        val api = documentsApi ?: return false
        val response = api.getUserFavorites(workspaceId)
        val remote = (response as? ResultData.Complete)?.data?.toSet() ?: return false
        val local = documentRepository.loadFavDocumentsForWorkspace(OrderBy.UPDATE.type, workspaceId)
            .map { document -> document.id }
            .toSet()
        val pending = documentRepository.loadPendingFavorites(workspaceId)
        val changedHere = pending.map { change -> change.documentId }.toSet()

        val toFavorite = remote - local - changedHere
        val toUnfavorite = local - remote - changedHere
        if (toFavorite.isNotEmpty()) documentRepository.favoriteDocumentByIds(toFavorite)
        if (toUnfavorite.isNotEmpty()) documentRepository.unFavoriteDocumentByIds(toUnfavorite)

        sendPendingFavorites(api, pending, backendHas = remote, workspaceId)
        return true
    }

    /**
     * Tells the backend the favorites changed here that it doesn't have yet, and forgets each
     * record it takes. A record that can't be sent stays for the next time, and the local
     * favorite stays with it. Nothing goes for the disconnected workspace.
     */
    suspend fun sendPendingFavorites(workspaceId: String) {
        if (workspaceId == Workspace.disconnectedWorkspace().id) return
        val api = documentsApi ?: return
        sendPendingFavorites(api, documentRepository.loadPendingFavorites(workspaceId), backendHas = null, workspaceId)
    }

    /**
     * @param backendHas the backend's favorites when just asked, so a record it agrees with is
     * only forgotten; null to send every record.
     */
    private suspend fun sendPendingFavorites(
        api: DocumentsApi,
        pending: List<PendingFavorite>,
        backendHas: Set<String>?,
        workspaceId: String
    ) {
        for (change in pending) {
            val backendAgrees = backendHas != null && (change.documentId in backendHas) == change.favorite
            if (backendAgrees || sendFavorite(api, change, workspaceId)) {
                // Unless the favorite changed again meanwhile: that record is newer and stays.
                documentRepository.deletePendingFavorite(change)
            }
        }
    }

    /** @return true when the backend has the favorite now. A failure is only logged. */
    private suspend fun sendFavorite(api: DocumentsApi, change: PendingFavorite, workspaceId: String): Boolean {
        val result = try {
            api.favoriteDocument(documentId = change.documentId, favorite = change.favorite, workspaceId = workspaceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Error sending the favorite of document ${change.documentId}: ${e.message}")
            return false
        }
        if (result !is ResultData.Complete) println("The favorite of document ${change.documentId} could not be sent")
        return result is ResultData.Complete
    }

    /**
     * The steps edited since the document was last sent, by position. Everything when it was
     * never sent (the caller batches them).
     */
    private fun getOutdatedStorySteps(
        document: Document,
        lastSyncTimestamp: Long
    ): List<Pair<String, StoryStep>> =
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
     * so they are no longer newer than it. A step edited since keeps its newer time. Only the
     * metadata of what is stored now is written, never the snapshot sent.
     */
    private suspend fun stampSent(document: Document, sent: List<Pair<String, StoryStep>>, sentAt: Instant) {
        val current = documentRepository.loadDocumentById(document.id, document.workspaceId) ?: return
        // Never earlier than a stamp already there.
        val syncedAt = current.lastSyncedAt?.let { maxOf(it, sentAt) } ?: sentAt
        documentRepository.saveDocumentMetadata(current.copy(lastSyncedAt = syncedAt))

        val sentById = sent.associate { (_, step) -> step.id to step.lastUpdatedAt }
        val stamped = current.content.entries
            .filter { (_, step) -> step.id in sentById && step.lastUpdatedAt == sentById[step.id] }
            .map { (position, step) -> position to step.copy(lastUpdatedAt = sentAt.toEpochMilliseconds()) }
        if (stamped.isNotEmpty()) documentRepository.saveStorySteps(stamped, document.id)
    }

    /** The document's metadata alone: its steps travel through the step sync. */
    private fun Document.withoutContent(): Document = copy(content = emptyMap())

    private fun Map<String, StoryStep>.withoutSteps(ids: Set<String>): Map<String, StoryStep> =
        if (ids.isEmpty()) this else filterNot { (_, step) -> step.id in ids }

    companion object {
        /**
         * Where the deletions watermark of a workspace is kept: in the folder contents
         * watermarks, under this id instead of a folder's. Deletions are workspace-wide, and
         * the value is the backend's time, not this device's.
         */
        const val DELETIONS_WATERMARK_ID = "__step_deletions__"

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
