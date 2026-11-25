@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync.cloud

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.sync.DocumentSyncService
import io.writeopia.core.folders.sync.FolderConflictHandler
import io.writeopia.core.folders.sync.SyncActivity
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Syncs the offline workspace with a folder in the user's own cloud storage ([CloudFileStore]),
 * with no Writeopia backend in between. The layout is in [CloudSyncFile]: each device writes only
 * its own files and reads everyone else's, so the store needs no locking and a write is never
 * lost to another.
 *
 * The rules are the ones of the backend sync ([DocumentSyncService]): documents merge step by
 * step, the newer step wins; a deleted step, document or folder never comes back.
 *
 * In this order:
 * 1. Deletions: what was deleted here goes into this device's deletions file, then is forgotten
 *    here; what other devices deleted is removed here.
 * 2. Folders: the other devices' changed folders are merged in (the newer one wins), then this
 *    device's folders file is written when a folder changed here.
 * 3. Documents: the other devices' changed documents are merged in, step by step; then each
 *    document changed here is written to its file and stamped as synced, at the time taken before
 *    it was written, so an edit made meanwhile goes with the next sync.
 */
class CloudWorkspaceSync(
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val store: CloudFileStore,
    private val state: CloudSyncStateStore,
    private val documentSyncService: DocumentSyncService = DocumentSyncService(
        documentRepository = documentRepository,
        syncApi = { error("The cloud sync never calls the backend") },
    ),
    private val folderConflictHandler: FolderConflictHandler = FolderConflictHandler(folderRepository),
    private val json: Json = writeopiaJson,
) {
    private val running = Mutex()

    suspend fun sync(workspaceId: String): ResultData<Unit> =
        SyncActivity.track {
            running.withLock {
                try {
                    syncInternal(workspaceId)
                    ResultData.Complete(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Cloud sync failed: ${e.message}")
                    ResultData.Error(e)
                }
            }
        }

    private suspend fun syncInternal(workspaceId: String) {
        val deviceId = state.deviceId()
        val files = store.list()
        val seen = state.seenVersions()

        val parsed = files.mapNotNull { file -> CloudSyncFile.parse(file.name)?.let { kind -> file to kind } }
        val own = parsed.filter { (_, kind) -> kind.deviceId == deviceId }
        val others = parsed.filter { (_, kind) -> kind.deviceId != deviceId }
        val changedOthers = others.filter { (file, _) -> seen[file.id] != file.version }
        val ownDocumentFiles = own
            .mapNotNull { (file, kind) -> (kind as? CloudSyncFile.DocumentFile)?.let { it.documentId to file } }
            .toMap()

        // 1. Deletions
        val deleted = syncDeletions(workspaceId, deviceId, own, others, changedOthers, ownDocumentFiles)

        // 2. Folders
        val receivedFolders = changedOthers
            .filter { (_, kind) -> kind is CloudSyncFile.FoldersFile }
            .flatMap { (file, _) -> json.decodeFromString<CloudFolders>(store.download(file.id)).folders }
            .map { folderApi -> folderApi.toModel().copy(workspaceId = workspaceId) }
            .filter { folder -> folder.id !in deleted.folders }
            // The same folder from two devices: the newer copy.
            .groupBy { folder -> folder.id }
            .map { (_, copies) -> copies.maxBy { folder -> folder.lastUpdatedAt } }
        folderConflictHandler.handleConflict(
            localFolders = folderRepository.localOutDatedFolders(workspaceId),
            externalFolders = receivedFolders,
        )
        sendFolders(workspaceId, deviceId, own)

        // 3. Documents
        changedOthers
            .mapNotNull { (file, kind) -> (kind as? CloudSyncFile.DocumentFile)?.let { file to it } }
            .filter { (_, kind) -> kind.documentId !in deleted.documents }
            .forEach { (file, _) ->
                val received = json.decodeFromString<DocumentApi>(store.download(file.id))
                    .toModel()
                    .receivedInto(workspaceId)
                documentSyncService.mergeReceivedDocuments(listOf(received), excluding = deleted.steps.keys)
            }
        sendDocuments(workspaceId, deviceId, ownDocumentFiles)

        // Every file of the others is taken in now; the ones gone from the store are forgotten.
        state.saveSeenVersions(others.associate { (file, _) -> file.id to file.version })

        documentRepository.refreshDocuments()
        folderRepository.refreshFolders()
    }

    /**
     * Writes what was deleted here to this device's deletions file and forgets it here (the
     * tombstones, the soft deleted documents and folders), then removes what the other devices
     * deleted.
     *
     * @return everything deleted on any device.
     */
    private suspend fun syncDeletions(
        workspaceId: String,
        deviceId: String,
        own: List<Pair<CloudFile, CloudSyncFile>>,
        others: List<Pair<CloudFile, CloudSyncFile>>,
        changedOthers: List<Pair<CloudFile, CloudSyncFile>>,
        ownDocumentFiles: Map<String, CloudFile>,
    ): CloudDeletions {
        val ownFile = own.firstOrNull { (_, kind) -> kind is CloudSyncFile.DeletionsFile }?.first
        val ownDeletions = ownFile?.let { file -> decodeDeletions(store.download(file.id)) } ?: CloudDeletions()

        val tombstones = documentRepository.loadStoryStepTombstones(workspaceId)
        val softDeletedDocuments = documentRepository.getSoftDeletedDocuments(workspaceId)
        val softDeletedFolders = folderRepository.getSoftDeletedFolders(workspaceId)
        val now = Clock.System.now().toEpochMilliseconds()

        val deletedHere = CloudDeletions(
            steps = tombstones.associate { tombstone -> tombstone.stepId to tombstone.deletedAt },
            documents = softDeletedDocuments.associate { document -> document.id to now },
            folders = softDeletedFolders.associate { folder -> folder.id to now },
        )

        val ownNow = if (deletedHere.isEmpty()) {
            ownDeletions
        } else {
            // The earlier time stays for what was already there.
            val merged = deletedHere + ownDeletions
            val content = json.encodeToString(CloudDeletions.serializer(), merged)
            if (ownFile == null) {
                store.create(CloudSyncFile.deletionsName(deviceId), content)
            } else {
                store.update(ownFile.id, content)
            }

            // Only now that the store has them.
            if (tombstones.isNotEmpty()) {
                documentRepository.deleteStoryStepTombstones(tombstones.map { tombstone -> tombstone.stepId })
            }
            if (softDeletedDocuments.isNotEmpty()) {
                documentRepository.hardDeleteDocumentByIds(softDeletedDocuments.map { it.id }.toSet(), workspaceId)
            }
            softDeletedFolders.forEach { folder -> folderRepository.hardDeleteFolderById(folder.id, workspaceId) }
            merged
        }

        // What the other devices deleted. An unchanged file was applied before; every file is
        // still read, since the whole set keeps old copies in their files from coming back.
        val changedIds = changedOthers.map { (file, _) -> file.id }.toSet()
        var all = ownNow
        others
            .filter { (_, kind) -> kind is CloudSyncFile.DeletionsFile }
            .forEach { (file, _) ->
                val theirs = decodeDeletions(store.download(file.id))
                all += theirs
                if (file.id in changedIds) applyDeletions(theirs, workspaceId)
            }

        // This device's copy of a document deleted anywhere goes from the store too.
        all.documents.keys
            .mapNotNull(ownDocumentFiles::get)
            .forEach { file -> store.delete(file.id) }

        return all
    }

    private suspend fun applyDeletions(deletions: CloudDeletions, workspaceId: String) {
        if (deletions.steps.isNotEmpty()) {
            documentRepository.deleteStoryStepsByIds(deletions.steps.keys, workspaceId)
        }
        if (deletions.documents.isNotEmpty()) {
            documentRepository.hardDeleteDocumentByIds(deletions.documents.keys, workspaceId)
        }
        deletions.folders.keys.forEach { folderId -> folderRepository.hardDeleteFolderById(folderId, workspaceId) }
    }

    /** Writes every folder here to this device's folders file, when one changed here. */
    private suspend fun sendFolders(
        workspaceId: String,
        deviceId: String,
        own: List<Pair<CloudFile, CloudSyncFile>>
    ) {
        val outdated = folderRepository.localOutDatedFolders(workspaceId)
        val ownFile = own.firstOrNull { (_, kind) -> kind is CloudSyncFile.FoldersFile }?.first
        if (outdated.isEmpty() && ownFile != null) return

        val sentAt = Clock.System.now()
        val folders = folderRepository.getFoldersForWorkspace(workspaceId).filterNot(Folder::deleted)
        val content = json.encodeToString(CloudFolders.serializer(), CloudFolders(folders.map(Folder::toApi)))

        if (ownFile == null) {
            store.create(CloudSyncFile.foldersName(deviceId), content)
        } else {
            store.update(ownFile.id, content)
        }

        outdated.forEach { folder ->
            folderRepository.updateFolder(folder.copy(lastSyncedAt = folder.lastSyncedAt?.let { maxOf(it, sentAt) } ?: sentAt))
        }
    }

    /** Writes each document changed here to its file, then stamps it as synced. */
    private suspend fun sendDocuments(
        workspaceId: String,
        deviceId: String,
        ownDocumentFiles: Map<String, CloudFile>
    ) {
        documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId).forEach { outdated ->
            val sentAt = Clock.System.now()
            // With its content as stored now.
            val document = documentRepository.loadDocumentById(outdated.id, workspaceId) ?: return@forEach
            val content = json.encodeToString(DocumentApi.serializer(), document.toApi())

            val file = ownDocumentFiles[document.id]
            if (file == null) {
                store.create(CloudSyncFile.documentName(deviceId, document.id), content)
            } else {
                store.update(file.id, content)
            }

            val current = documentRepository.loadDocumentById(document.id, workspaceId) ?: return@forEach
            val syncedAt = current.lastSyncedAt?.let { maxOf(it, sentAt) } ?: sentAt
            documentRepository.saveDocumentMetadata(current.copy(lastSyncedAt = syncedAt))
        }
    }

    private fun decodeDeletions(content: String): CloudDeletions =
        json.decodeFromString(CloudDeletions.serializer(), content)

    /**
     * A document from another device, as this device keeps it: in its workspace, and in sync
     * as received, so it isn't written back as this device's own copy unless it changes here.
     */
    private fun Document.receivedInto(workspaceId: String): Document =
        copy(workspaceId = workspaceId, lastSyncedAt = maxOf(lastUpdatedAt, lastSyncedAt ?: Instant.DISTANT_PAST))
}
