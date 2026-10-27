@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.sdk.utils.iterables.Rank
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.api.documents.documents.repository.SyncEventType
import io.writeopia.api.documents.documents.repository.addUserFavorite
import io.writeopia.api.documents.documents.repository.createSyncEvent
import io.writeopia.api.documents.documents.repository.deleteDocumentsByFolderId
import io.writeopia.api.documents.documents.repository.deleteDocumentsByIds
import io.writeopia.api.documents.documents.repository.deleteFolder
import io.writeopia.api.documents.documents.repository.deleteStoryStepsByIds
import io.writeopia.api.documents.documents.repository.storyStepTombstones
import io.writeopia.api.documents.documents.repository.storyStepDocumentIds
import io.writeopia.api.documents.documents.repository.deleteStoryStepsByIdsForWorkspace
import io.writeopia.databse.tombstone.StoryStepTombstoneStore
import io.writeopia.sdk.serialization.data.StoryStepTombstoneApi
import io.writeopia.api.documents.documents.repository.documentsDiffByFolder
import io.writeopia.api.documents.documents.repository.documentsDiffByWorkspace
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.getDocumentByTitle
import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.documents.documents.repository.getFolderById
import io.writeopia.api.documents.documents.repository.getFoldersByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.api.documents.documents.repository.getPublishedDocumentById
import io.writeopia.api.documents.documents.repository.getStoryStepById
import io.writeopia.api.documents.documents.repository.getStoryStepDocumentId
import io.writeopia.api.documents.documents.repository.getStoryStepsAfterTime
import io.writeopia.api.documents.documents.repository.getUserFavoriteDocumentIds
import io.writeopia.api.documents.documents.repository.isDocumentPublished
import io.writeopia.api.documents.documents.repository.isUserFavorite
import io.writeopia.api.documents.documents.repository.moveDocumentToFolder
import io.writeopia.api.documents.documents.repository.moveFolderToFolder
import io.writeopia.api.documents.documents.repository.removeUserFavorite
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.documents.repository.saveDocumentTag
import io.writeopia.api.documents.documents.repository.saveFolder
import io.writeopia.api.documents.documents.repository.setDocumentPublished
import io.writeopia.api.documents.documents.repository.updateDocumentTitle
import io.writeopia.api.documents.documents.repository.touchDocument
import io.writeopia.api.documents.documents.repository.upsertStoryStep
import io.writeopia.api.documents.embeddings.DocumentEmbeddingService
import io.writeopia.api.embeddings.DocumentPlainText
import io.writeopia.api.documents.search.SearchDocument
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.ResultData
import io.writeopia.connection.logger
import io.writeopia.connection.Urls
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.markdown.InlineMarkdownParser
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.request.DocumentSyncInfo
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import io.writeopia.sdk.serialization.response.UnsyncedDocumentInfo
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object DocumentsService {

    suspend fun receiveDocuments(
        documents: List<Document>,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean,
        embeddingService: DocumentEmbeddingService? = null
    ): Boolean {
        documents.forEach { document ->
            // Where the document was before, so a save that also moves it refreshes both folders.
            val previousParentId = embeddingService?.let {
                writeopiaDb.getDocumentById(document.id, workspaceId)?.parentId
            }
            writeopiaDb.saveDocument(document)
            // The steps travel through the step sync, so what was sent may carry none: the
            // document is embedded as stored.
            val stored = embeddingService?.let { writeopiaDb.getDocumentWithContentById(document.id, workspaceId) }
            embeddingService?.onDocumentSaved(stored ?: document, previousParentId, writeopiaDb)
        }

        return if (useAi) sendToAiHub(documents, workspaceId) else true
    }

    /**
     * Deletes the steps a device removed and keeps their tombstones, so the other devices learn
     * of the deletion and no save brings the steps back. Only the steps of the workspace's
     * documents are touched; the documents are stamped as changed so the next folder diffs of
     * the other devices list them.
     */
    suspend fun deleteStorySteps(
        tombstones: List<StoryStepTombstoneApi>,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null,
    ) {
        if (tombstones.isEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()
        val stepIds = tombstones.map { tombstone -> tombstone.stepId }.distinct()

        // The document a step is in, as stored; the client's word for the ones already gone.
        val storedDocumentIds = writeopiaDb.storyStepDocumentIds(stepIds)
        val documentIds = tombstones.associate { tombstone ->
            tombstone.stepId to (storedDocumentIds[tombstone.stepId] ?: tombstone.documentId)
        }
        // Only the documents of this workspace: a tombstone for another workspace's step is noise.
        val ownedDocumentIds = documentIds.values.distinct()
            .filter { documentId -> writeopiaDb.getDocumentById(documentId, workspaceId) != null }
            .toSet()
        val owned = stepIds.filter { stepId -> documentIds.getValue(stepId) in ownedDocumentIds }
        if (owned.isEmpty()) return

        val tombstones = writeopiaDb.storyStepTombstones()
        val alreadyTombstoned = tombstones.tombstonedAmong(owned, workspaceId)

        writeopiaDb.deleteStoryStepsByIdsForWorkspace(owned, workspaceId)
        tombstones.insertAll(
            owned.filter { stepId -> stepId !in alreadyTombstoned }.map { stepId ->
                StoryStepTombstoneStore.Tombstone(
                    stepId = stepId,
                    documentId = documentIds.getValue(stepId),
                    workspaceId = workspaceId,
                    deletedAt = now
                )
            }
        )

        // Only the documents that lost a step now: a deletion told twice changes nothing.
        owned.filter { stepId -> stepId in storedDocumentIds }
            .map { stepId -> documentIds.getValue(stepId) }
            .distinct()
            .forEach { documentId ->
                val currentUpdatedAt = writeopiaDb.getDocumentById(documentId, workspaceId)
                    ?.lastUpdatedAt
                    ?.toEpochMilliseconds()
                    ?: 0L
                writeopiaDb.touchDocument(
                    documentId = documentId,
                    lastUpdatedAt = maxOf(currentUpdatedAt, now),
                    lastSynced = now
                )
                embeddingService?.embedDocumentIfNeeded(documentId, workspaceId, writeopiaDb)
            }
    }

    /** The ids of the steps deleted in [workspaceId] after [since] (epoch milliseconds). */
    fun deletedStoryStepIds(workspaceId: String, since: Long, writeopiaDb: WriteopiaDbBackend): List<String> =
        writeopiaDb.storyStepTombstones().deletedSince(workspaceId, since)

    /**
     * Stores folders sent by a client. Folders deleted earlier (tombstoned) are dropped
     * rather than recreated: a device that still holds a stale copy must not bring a
     * folder back for everyone else.
     */
    suspend fun receiveFolders(
        folders: List<Folder>,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): Boolean {
        val tombstoned = writeopiaDb.saveFolder(*folders.toTypedArray())

        if (tombstoned.isNotEmpty()) {
            logger.info("Ignored ${tombstoned.size} tombstoned folder(s) sent by client: $tombstoned")
        }

        if (embeddingService != null) {
            folders.filter { folder -> folder.id !in tombstoned && !folder.deleted }
                .forEach { folder -> embeddingService.onFolderSaved(folder, writeopiaDb) }
        }

        return true
    }

    /** Upserts the tags a client sends; clients decide conflicts, the backend keeps the last write. */
    fun receiveDocumentTags(tags: List<DocumentTag>, writeopiaDb: WriteopiaDbBackend) {
        tags.forEach { tag -> writeopiaDb.saveDocumentTag(tag) }
    }

    suspend fun search(
        query: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> =
        SearchDocument.search(query, workspaceId, writeopiaDb)

    suspend fun getDocumentById(
        id: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Document? {
        val document = writeopiaDb.getDocumentWithContentById(id, workspaceId) ?: return null
        return ensureTitleInSync(document)
    }

    suspend fun getDocumentByTitle(
        title: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Document? = writeopiaDb.getDocumentByTitle(title, workspaceId)

    suspend fun getFolderById(
        id: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Folder? = writeopiaDb.getFolderById(id, workspaceId)

    /**
     * The folders from the root down to [id] (included), for the breadcrumb of a folder. Empty
     * when the folder doesn't exist. A broken parent link ends the path where it stops resolving.
     */
    suspend fun getFolderPath(
        id: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Folder> {
        val path = ArrayDeque<Folder>()
        val visited = mutableSetOf<String>()
        var current = writeopiaDb.getFolderById(id, workspaceId)

        // The visited set guards against a cycle in corrupted parent links.
        while (current != null && visited.add(current.id)) {
            path.addFirst(current)
            current = current.parentId
                .takeIf { parentId -> parentId != Folder.ROOT_PATH }
                ?.let { parentId -> writeopiaDb.getFolderById(parentId, workspaceId) }
        }

        return path
    }

    suspend fun createFolder(
        parentFolderId: String,
        title: String,
        workspaceId: String,
        icon: MenuItem.Icon? = null,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): Folder {
        val now = Clock.System.now()
        val folder = Folder(
            id = GenerateId.generate(),
            parentId = parentFolderId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = workspaceId,
            itemCount = 0,
            icon = icon
        )

        writeopiaDb.saveFolder(folder)
        embeddingService?.onFolderSaved(folder, writeopiaDb)
        return folder
    }

    suspend fun updateFolder(
        folderId: String,
        workspaceId: String,
        title: String?,
        icon: MenuItem.Icon?,
        favorite: Boolean?,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): Folder? {
        val existingFolder = writeopiaDb.getFolderById(folderId, workspaceId) ?: return null

        val updatedFolder = existingFolder.copy(
            title = title ?: existingFolder.title,
            icon = icon ?: existingFolder.icon,
            favorite = favorite ?: existingFolder.favorite,
            lastUpdatedAt = Clock.System.now()
        )

        writeopiaDb.saveFolder(updatedFolder)
        if (updatedFolder.title != existingFolder.title) embeddingService?.onFolderSaved(updatedFolder, writeopiaDb)
        return updatedFolder
    }

    fun upsertDocument(
        document: Document,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
    ): Document {
        val documentWithWorkspace = document.copy(
            workspaceId = workspaceId,
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = Clock.System.now()
        )

        writeopiaDb.saveDocument(documentWithWorkspace)

        return documentWithWorkspace
    }

    /** [upsertDocument], then the document's embedding and the folders it is in (or left). */
    suspend fun upsertDocument(
        document: Document,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService?
    ): Document {
        val previousParentId = embeddingService?.let {
            writeopiaDb.getDocumentById(document.id, workspaceId)?.parentId
        }
        val saved = upsertDocument(document, workspaceId, writeopiaDb)
        embeddingService?.onDocumentSaved(saved, previousParentId, writeopiaDb)

        return saved
    }

    suspend fun cloneDocuments(
        documentIds: List<String>,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean,
        embeddingService: DocumentEmbeddingService? = null
    ): List<Document> {
        val now = Clock.System.now()
        val clonedDocuments = mutableListOf<Document>()

        for (documentId in documentIds) {
            val originalDocument = writeopiaDb.getDocumentWithContentById(documentId, workspaceId)
                ?: continue

            // Skip if document doesn't belong to the workspace
            if (originalDocument.workspaceId != workspaceId) continue

            // Clone the content with new IDs for each StoryStep
            val clonedContent = originalDocument.content.mapValues { (_, storyStep) ->
                cloneStoryStep(storyStep)
            }

            val clonedDocument = originalDocument.copy(
                id = GenerateId.generate(),
                title = "${originalDocument.title} (Copy)",
                content = clonedContent,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                favorite = false
            )

            writeopiaDb.saveDocument(clonedDocument)
            embeddingService?.onDocumentSaved(clonedDocument, null, writeopiaDb)
            clonedDocuments.add(clonedDocument)
        }

        if (useAi && clonedDocuments.isNotEmpty()) {
            sendToAiHub(clonedDocuments, workspaceId)
        }

        return clonedDocuments
    }

    private fun cloneStoryStep(storyStep: StoryStep): StoryStep {
        return storyStep.copy(
            id = GenerateId.generate(),
            localId = GenerateId.generate(),
            steps = storyStep.steps.map { cloneStoryStep(it) }
        )
    }

    /**
     * Recursively deletes a folder and all its contents (child folders and documents).
     * This follows the same pattern as NotesUseCase.deleteFolderById.
     * Creates sync events for all deleted folders and documents.
     */
    suspend fun deleteFolder(
        folderId: String,
        workspaceId: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ) {
        val parentId = embeddingService?.let { writeopiaDb.getFolderById(folderId, workspaceId)?.parentId }
        val deletedFolderIds = mutableListOf<String>()
        val deletedDocumentIds = mutableListOf<String>()

        deleteFolderTree(folderId, workspaceId, userId, writeopiaDb, deletedFolderIds, deletedDocumentIds)

        embeddingService?.onFolderDeleted(deletedFolderIds, deletedDocumentIds, parentId, workspaceId, writeopiaDb)
    }

    private suspend fun deleteFolderTree(
        folderId: String,
        workspaceId: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        deletedFolderIds: MutableList<String>,
        deletedDocumentIds: MutableList<String>
    ) {
        val childFolders = writeopiaDb.getFoldersByParentId(folderId)

        // Recursively delete all child folders
        childFolders.forEach { childFolder ->
            deleteFolderTree(childFolder.id, workspaceId, userId, writeopiaDb, deletedFolderIds, deletedDocumentIds)
        }

        // Get document IDs in this folder before deleting them
        val documentIds = writeopiaDb.getIdsByParentId(folderId)
        deletedDocumentIds.addAll(documentIds)
        deletedFolderIds.add(folderId)

        // Create DELETE_DOCUMENT events for all documents in this folder
        documentIds.forEach { documentId ->
            writeopiaDb.createSyncEvent(
                workspaceId = workspaceId,
                eventType = SyncEventType.DELETE_DOCUMENT,
                entityId = documentId,
                userId = userId
            )
        }

        // Delete all documents in this folder
        writeopiaDb.deleteDocumentsByFolderId(folderId)

        // Create DELETE_FOLDER event
        writeopiaDb.createSyncEvent(
            workspaceId = workspaceId,
            eventType = SyncEventType.DELETE_FOLDER,
            entityId = folderId,
            userId = userId
        )

        // Finally delete the folder itself
        writeopiaDb.deleteFolder(folderId, workspaceId)
    }

    suspend fun deleteDocuments(
        documentIds: List<String>,
        workspaceId: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ) {
        // The folders the documents leave, read before they are gone.
        val parentIds = embeddingService?.let {
            documentIds.mapNotNull { documentId -> writeopiaDb.getDocumentById(documentId, workspaceId)?.parentId }
        } ?: emptyList()

        // Create DELETE_DOCUMENT events for all documents
        documentIds.forEach { documentId ->
            writeopiaDb.createSyncEvent(
                workspaceId = workspaceId,
                eventType = SyncEventType.DELETE_DOCUMENT,
                entityId = documentId,
                userId = userId
            )
        }

        writeopiaDb.deleteDocumentsByIds(documentIds)
        embeddingService?.onDocumentsDeleted(documentIds, parentIds, workspaceId, writeopiaDb)
    }

    suspend fun favoriteDocument(
        userId: String,
        documentId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.addUserFavorite(userId, documentId, workspaceId)
    }

    suspend fun unFavoriteDocument(
        userId: String,
        documentId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.removeUserFavorite(userId, documentId)
    }

    suspend fun isDocumentFavorited(
        userId: String,
        documentId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        return writeopiaDb.isUserFavorite(userId, documentId)
    }

    suspend fun getUserFavoriteDocumentIds(
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<String> {
        return writeopiaDb.getUserFavoriteDocumentIds(userId, workspaceId)
    }

    suspend fun moveFolder(
        folderId: String,
        targetParentId: String,
        workspaceId: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): Boolean {
        // Prevent moving a folder into itself
        if (folderId == targetParentId) {
            return false
        }

        // Prevent creating a cycle by checking if targetParentId is a descendant of folderId
        // Traverse up from targetParentId to root, if we encounter folderId, it would create a cycle
        if (wouldCreateCycle(folderId, targetParentId, workspaceId = workspaceId, writeopiaDb)) {
            return false
        }

        // Get current parent for the sync event
        val currentFolder = writeopiaDb.getFolderById(folderId, workspaceId)
        val oldParentId = currentFolder?.parentId

        writeopiaDb.moveFolderToFolder(folderId, targetParentId)

        // Create MOVE_FOLDER sync event
        writeopiaDb.createSyncEvent(
            workspaceId = workspaceId,
            eventType = SyncEventType.MOVE_FOLDER,
            entityId = folderId,
            userId = userId,
            oldParentId = oldParentId,
            newParentId = targetParentId
        )

        embeddingService?.onFolderMoved(oldParentId, targetParentId, workspaceId, writeopiaDb)

        return true
    }

    /**
     * Moves a document to a different folder.
     * Creates a MOVE_DOCUMENT sync event.
     */
    suspend fun moveDocument(
        documentId: String,
        targetParentId: String,
        workspaceId: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): Boolean {
        // Get current document to find old parent
        val currentDocument = writeopiaDb.getDocumentById(documentId, workspaceId)
            ?: return false

        val oldParentId = currentDocument.parentId

        // Move the document
        writeopiaDb.moveDocumentToFolder(documentId, targetParentId)

        // Create MOVE_DOCUMENT sync event
        writeopiaDb.createSyncEvent(
            workspaceId = workspaceId,
            eventType = SyncEventType.MOVE_DOCUMENT,
            entityId = documentId,
            userId = userId,
            oldParentId = oldParentId,
            newParentId = targetParentId
        )

        embeddingService?.onDocumentMoved(oldParentId, targetParentId, workspaceId, writeopiaDb)

        return true
    }

    /**
     * Checks if moving folderId to targetParentId would create a cycle.
     * This happens when targetParentId is a descendant of folderId.
     * We traverse up from targetParentId to root, and if we encounter folderId, it would create a cycle.
     */
    private suspend fun wouldCreateCycle(
        folderId: String,
        targetParentId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        var currentId = targetParentId

        while (currentId != Folder.ROOT_PATH && currentId.isNotEmpty()) {
            if (currentId == folderId) {
                // Found the folder we're trying to move in the ancestor chain
                // Moving it here would create a cycle
                return true
            }

            val folder = writeopiaDb.getFolderById(currentId, workspaceId)
                ?: break // Folder not found, assume no cycle (or it's at root level)

            currentId = folder.parentId
        }

        return false
    }

    /**
     * Ensures document title metadata is in sync with the title from content.
     * Returns the document with corrected title if needed.
     */
    private fun ensureTitleInSync(document: Document): Document {
        val titleFromContent = document.content.values.find { storyStep ->
            storyStep.type.name == "title"
        }?.text

        return if (titleFromContent != null && titleFromContent != document.title) {
            document.copy(title = titleFromContent)
        } else {
            document
        }
    }

    /**
     * Gets documents diff by folder with titles synchronized from content.
     */
    suspend fun getDocumentsDiffByFolder(
        folderId: String,
        workspaceId: String,
        lastSync: Long,
        orderBy: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Document> {
        return writeopiaDb.documentsDiffByFolder(folderId, workspaceId, lastSync, orderBy)
            .map { ensureTitleInSync(it) }
    }

    /**
     * Gets documents diff by workspace with titles synchronized from content.
     */
    suspend fun getDocumentsDiffByWorkspace(
        workspaceId: String,
        lastSync: Long,
        orderBy: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<Document> {
        return writeopiaDb.documentsDiffByWorkspace(workspaceId, lastSync, orderBy)
            .map { ensureTitleInSync(it) }
    }

    private suspend fun sendToAiHub(documents: List<Document>, workspaceId: String): Boolean {
        val aiHubUrl = Urls.AI_HUB ?: return true

        return wrWebClient.post("$aiHubUrl/documents/") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }, workspaceId))
        }.status.isSuccess()
    }

    fun countDocumentsByWorkspaceId(workspaceId: String, writeopiaDb: WriteopiaDbBackend): Long {
        return writeopiaDb.documentEntityQueries.countByWorkspaceId(workspaceId).executeAsOne()
    }

    /**
     * Gets a published document by ID.
     * Returns null if the document doesn't exist or is not published.
     */
    suspend fun getPublishedDocument(documentId: String, writeopiaDb: WriteopiaDbBackend): Document? {
        return writeopiaDb.getPublishedDocumentById(documentId)
    }

    /**
     * Sets the published status of a document.
     */
    fun setPublished(documentId: String, published: Boolean, writeopiaDb: WriteopiaDbBackend) {
        writeopiaDb.setDocumentPublished(documentId, published)
    }

    /**
     * Checks if a document is published.
     */
    fun isPublished(documentId: String, writeopiaDb: WriteopiaDbBackend): Boolean {
        return writeopiaDb.isDocumentPublished(documentId)
    }

    /**
     * Syncs StorySteps between client and server.
     *
     * Logic:
     * 1. Create serverTimestamp at start of request
     * 2. Get server steps updated after client's lastSyncTimestamp
     * 3. For each client change:
     *    - If server step is newer (serverStep.lastUpdatedAt > clientStep.lastUpdatedAt), skip save
     *    - Otherwise, save client's version with request.requestTimestamp
     * 4. Process deletions
     * 5. Return steps newer than client's lastSync (excluding client's changes)
     */
    suspend fun syncStorySteps(
        documentId: String,
        workspaceId: String,
        request: StoryStepSyncRequest,
        writeopiaDb: WriteopiaDbBackend,
        embeddingService: DocumentEmbeddingService? = null
    ): StoryStepSyncResponse {
        val serverTimestamp = Clock.System.now().toEpochMilliseconds()

        // Check if document exists, create it if not
        val existingDocument = writeopiaDb.getDocumentById(documentId, workspaceId)
        if (existingDocument == null) {
            // Extract title from the first title-type story step, or use a default
            val titleStep = request.changes.firstOrNull { it.storyStep.type.name == "title" }
            val title = titleStep?.storyStep?.text ?: "Untitled"

            val now = Clock.System.now()
            val newDocument = Document(
                id = documentId,
                title = title,
                content = emptyMap(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                parentId = "root",
                workspaceId = workspaceId
            )
            writeopiaDb.saveDocument(newDocument)
        }

        // Get server steps updated after client's last sync
        val serverUpdatedSteps = writeopiaDb.getStoryStepsAfterTime(
            documentId = documentId,
            afterTime = request.lastSyncTimestamp
        )

        // Create a map of server step IDs to their lastUpdatedAt for conflict resolution
        val serverStepTimestamps = serverUpdatedSteps.associate { (_, step) ->
            step.id to (step.lastUpdatedAt ?: 0L)
        }

        // Track which step IDs the client is updating (to exclude from response)
        val clientUpdatedStepIds = mutableSetOf<String>()
        var updatedTitle: String? = null

        // A step deleted at some point never comes back, whatever a device still holds.
        val tombstones = writeopiaDb.storyStepTombstones()
        val tombstoned = tombstones.tombstonedAmong(request.changes.map { change -> change.storyStep.id }, workspaceId)

        // Process client changes
        for (change in request.changes) {
            if (change.storyStep.id in tombstoned) continue

            val clientStep = change.storyStep.toModel()
            val clientTimestamp = change.storyStep.lastUpdatedAt ?: 0L
            val serverStepTimestamp = serverStepTimestamps[clientStep.id]

            // Only save if server step doesn't exist or client is newer
            if (serverStepTimestamp == null || clientTimestamp >= serverStepTimestamp) {
                writeopiaDb.upsertStoryStep(
                    storyStep = clientStep,
                    position = change.position,
                    documentId = documentId,
                    lastUpdatedAt = request.requestTimestamp
                )
                clientUpdatedStepIds.add(clientStep.id)

                // Track title updates
                if (change.storyStep.type.name == "title") {
                    updatedTitle = change.storyStep.text
                }
            }
        }

        // Update document title if title step was changed
        if (updatedTitle != null) {
            val currentDoc = writeopiaDb.getDocumentById(documentId, workspaceId)
            if (currentDoc != null && currentDoc.title != updatedTitle) {
                writeopiaDb.updateDocumentTitle(documentId, updatedTitle)
            }
        }

        // Process deletions: only steps of this document (a client may send any id), and only
        // if the step wasn't updated on the server after the client's last sync.
        val stepsOfThisDocument = writeopiaDb.storyStepDocumentIds(request.deletions)
            .filterValues { owner -> owner == documentId }
            .keys
        val deletionsToApply = request.deletions.filter { deletionId ->
            val serverStepTimestamp = serverStepTimestamps[deletionId]
            deletionId in stepsOfThisDocument &&
                (serverStepTimestamp == null || serverStepTimestamp <= request.lastSyncTimestamp)
        }

        if (deletionsToApply.isNotEmpty()) {
            writeopiaDb.deleteStoryStepsByIdsForWorkspace(deletionsToApply, workspaceId)
            // Kept so the other devices delete them too, and nothing brings them back.
            tombstones.insert(deletionsToApply, documentId, workspaceId, serverTimestamp)
        }

        // Steps alone don't change the document row: without this, the folder diffs of the
        // other devices (filtered by last_synced) never listed a document edited this way.
        if (clientUpdatedStepIds.isNotEmpty() || deletionsToApply.isNotEmpty()) {
            val currentUpdatedAt = writeopiaDb.getDocumentById(documentId, workspaceId)
                ?.lastUpdatedAt
                ?.toEpochMilliseconds()
                ?: 0L
            writeopiaDb.touchDocument(
                documentId = documentId,
                lastUpdatedAt = maxOf(currentUpdatedAt, request.requestTimestamp),
                lastSynced = serverTimestamp
            )
        }

        // The steps of the document deleted elsewhere since the client's last sync.
        val serverDeletedIds = tombstones.deletedSinceForDocument(documentId, request.lastSyncTimestamp)
            .filter { stepId -> stepId !in deletionsToApply }

        // Return server steps that were updated after client's lastSync, excluding client's changes
        val stepsToReturn = serverUpdatedSteps
            .filter { (_, step) -> step.id !in clientUpdatedStepIds }
            .map { (position, step) -> step.toApi(position) }

        // The document is embedded again only after a big change; the service decides.
        embeddingService?.embedDocumentIfNeeded(documentId, workspaceId, writeopiaDb)

        return StoryStepSyncResponse(
            serverTimestamp = serverTimestamp,
            updatedSteps = stepsToReturn,
            deletedIds = serverDeletedIds
        )
    }

    /**
     * What came of describing a link card.
     */
    sealed class LinkCardDescriptionResult {
        /** The card as saved, with the description as its text. */
        data class Success(val position: String, val storyStep: StoryStep) : LinkCardDescriptionResult()

        /**
         * The request does not carry a link card with a web address, or its card belongs to
         * another document (maps to HTTP 400).
         */
        data class InvalidRequest(val message: String) : LinkCardDescriptionResult()

        /** The AI answered nothing usable (maps to HTTP 502). */
        data class Error(val message: String) : LinkCardDescriptionResult()
    }

    /**
     * Describes the page of a link card with the AI and saves the description as the card's
     * text, before answering, so the client gets it from its next sync.
     *
     * The card is the one in the database with the request's id, when the sync already
     * delivered it; otherwise the request's card is saved at the request's position. The saved
     * card is stamped with the request's timestamp, like a sync would, so the client's later
     * edits of the card win over it and a sync from that time on returns it.
     *
     * @param describe Answers with the description of the page at the given address, or null.
     */
    suspend fun describeLinkCard(
        documentId: String,
        workspaceId: String,
        request: LinkCardDescriptionRequest,
        describe: suspend (url: String) -> String?,
        writeopiaDb: WriteopiaDbBackend
    ): LinkCardDescriptionResult {
        val requestedStep = request.storyStep.toModel()
        val url = requestedStep.url?.trim().orEmpty()

        if (requestedStep.type.number != StoryTypes.LINK_CARD.type.number) {
            return LinkCardDescriptionResult.InvalidRequest("The story step is not a link card")
        }

        if (url.isBlank()) {
            return LinkCardDescriptionResult.InvalidRequest("The link card has no address")
        }

        // The address goes into an AI prompt: only a web address of a sane length is accepted.
        if (!LinkCardDescription.isWebAddress(url)) {
            return LinkCardDescriptionResult.InvalidRequest("The link card address is not a web address")
        }

        // The card's id may only be reused inside this document; another document's step is
        // not overwritten through it.
        val stepDocumentId = writeopiaDb.getStoryStepDocumentId(requestedStep.id)
        if (stepDocumentId != null && stepDocumentId != documentId) {
            return LinkCardDescriptionResult.InvalidRequest("The story step belongs to another document")
        }

        val description = LinkCardDescription.tidy(describe(url))
            ?: return LinkCardDescriptionResult.Error("The AI answered no description for $url")

        if (writeopiaDb.getDocumentById(documentId, workspaceId) == null) {
            val now = Clock.System.now()
            writeopiaDb.saveDocument(
                Document(
                    id = documentId,
                    title = "Untitled",
                    content = emptyMap(),
                    createdAt = now,
                    lastUpdatedAt = now,
                    lastSyncedAt = now,
                    parentId = "root",
                    workspaceId = workspaceId
                )
            )
        }

        // The synced card, when there is one, keeps its place: the client may have moved it.
        val (position, storedStep) = writeopiaDb.getStoryStepById(requestedStep.id)
            ?.takeIf { writeopiaDb.getStoryStepDocumentId(requestedStep.id) == documentId }
            ?: (request.position to requestedStep)

        val describedStep = storedStep.copy(
            type = StoryTypes.LINK_CARD.type,
            url = storedStep.url?.takeIf { it.isNotBlank() } ?: url,
            text = description,
            lastUpdatedAt = request.requestTimestamp
        )

        writeopiaDb.upsertStoryStep(
            storyStep = describedStep,
            position = position,
            documentId = documentId,
            lastUpdatedAt = request.requestTimestamp
        )

        return LinkCardDescriptionResult.Success(position, describedStep)
    }

    /**
     * Result type for generateSummaryDocument operation.
     */
    sealed class GenerateSummaryResult {
        data class Success(val document: Document) : GenerateSummaryResult()
        data class NeedsSync(val unsyncedDocuments: List<UnsyncedDocumentInfo>) : GenerateSummaryResult()

        /** Client input validation error (maps to HTTP 400) */
        data class InvalidRequest(val message: String) : GenerateSummaryResult()

        /** Resource not found error (maps to HTTP 404) */
        data class NotFound(val message: String) : GenerateSummaryResult()

        /** Server/AI processing error (maps to HTTP 500) */
        data class Error(val message: String) : GenerateSummaryResult()

        data object GenAiUnavailable : GenerateSummaryResult()
    }

    /**
     * Generates a summary document from multiple documents.
     *
     * Flow:
     * 1. Validate documents list not empty
     * 2. Check sync status for each document (compare client's lastSyncedAt with server's)
     * 3. If unsynced documents exist, return NeedsSync
     * 4. Extract text content from all documents
     * 5. Generate summary using GenAI
     * 6. Create and save summary document
     */
    suspend fun generateSummaryDocument(
        documents: List<DocumentSyncInfo>,
        targetFolderId: String,
        workspaceId: String,
        summaryTitle: String?,
        model: String?,
        ignoreSyncCheck: Boolean,
        genAiService: GenAiService,
        writeopiaDb: WriteopiaDbBackend
    ): GenerateSummaryResult {
        // Check if GenAI is available
        if (!genAiService.isAvailable()) {
            return GenerateSummaryResult.GenAiUnavailable
        }

        // Validate documents
        if (documents.isEmpty()) {
            return GenerateSummaryResult.InvalidRequest("Documents list cannot be empty")
        }

        if (documents.size > MAX_DOCUMENTS_FOR_SUMMARY) {
            return GenerateSummaryResult.InvalidRequest(
                "Too many documents: ${documents.size}. Maximum allowed is $MAX_DOCUMENTS_FOR_SUMMARY"
            )
        }

        // Validate target folder exists (unless it's the root folder)
        if (targetFolderId != Folder.ROOT_PATH) {
            val targetFolder = writeopiaDb.getFolderById(targetFolderId, workspaceId)
            if (targetFolder == null) {
                return GenerateSummaryResult.NotFound("Target folder not found: $targetFolderId")
            }
        }

        // Fetch all documents and check sync status (unless ignoreSyncCheck is true)
        val unsyncedDocuments = mutableListOf<UnsyncedDocumentInfo>()
        val documentsToProcess = mutableListOf<Document>()

        for (docSyncInfo in documents) {
            val document = writeopiaDb.getDocumentWithContentById(docSyncInfo.documentId, workspaceId)
            if (document == null) {
                return GenerateSummaryResult.NotFound("Document not found: ${docSyncInfo.documentId}")
            }

            if (ignoreSyncCheck) {
                // Web clients skip sync check - they always use server version
                documentsToProcess.add(document)
            } else {
                // Check sync status: compare client's lastSyncedAt with server's lastSyncedAt
                // They must match for the document to be considered synced
                val serverLastSyncedAt = document.lastSyncedAt?.toEpochMilliseconds()
                val clientLastSyncedAt = docSyncInfo.lastSyncedAt

                // Document is synced if both lastSyncedAt values match
                val isSynced = serverLastSyncedAt != null &&
                               clientLastSyncedAt != null &&
                               serverLastSyncedAt == clientLastSyncedAt

                if (!isSynced) {
                    unsyncedDocuments.add(
                        UnsyncedDocumentInfo(
                            documentId = document.id,
                            documentTitle = document.title,
                            lastUpdatedAt = document.lastUpdatedAt.toEpochMilliseconds(),
                            lastSyncedAt = serverLastSyncedAt
                        )
                    )
                } else {
                    documentsToProcess.add(document)
                }
            }
        }

        // If there are unsynced documents, return NeedsSync
        if (unsyncedDocuments.isNotEmpty()) {
            return GenerateSummaryResult.NeedsSync(unsyncedDocuments)
        }

        // Extract text content from all documents
        val combinedText = DocumentPlainText.promptText(documentsToProcess)

        if (combinedText.isBlank()) {
            return GenerateSummaryResult.InvalidRequest("No text content found in the provided documents")
        }

        if (combinedText.length > MAX_COMBINED_TEXT_LENGTH) {
            return GenerateSummaryResult.InvalidRequest(
                "Combined document text exceeds maximum length: ${combinedText.length} characters. " +
                    "Maximum allowed is $MAX_COMBINED_TEXT_LENGTH characters"
            )
        }

        // Generate summary using GenAI
        val aiResponse = genAiService.generateSummary(combinedText, model)

        val errorMessage = aiResponse.error
        if (errorMessage != null) {
            return GenerateSummaryResult.Error(errorMessage)
        }

        val summaryText = aiResponse.response
        if (summaryText.isNullOrBlank()) {
            return GenerateSummaryResult.Error("AI generated an empty summary")
        }

        // Create summary document
        val now = Clock.System.now()
        val title = summaryTitle ?: "Summary of ${documentsToProcess.size} documents"
        val documentId = GenerateId.generate()

        // Build content map
        val content = buildSummaryContent(title, summaryText)

        val summaryDocument = parseDocumentMarkdown(
            Document(
                id = documentId,
                title = title,
                content = content,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                parentId = targetFolderId,
                workspaceId = workspaceId
            )
        )

        // Save the document
        writeopiaDb.saveDocument(summaryDocument)

        return GenerateSummaryResult.Success(summaryDocument)
    }

    /**
     * Builds the content map for a summary document.
     * Creates TITLE, TEXT paragraphs, and LAST_SPACE StorySteps.
     */
    private fun buildSummaryContent(title: String, summaryText: String): Map<String, StoryStep> {
        val content = mutableMapOf<String, StoryStep>()
        var position = Rank.at(0)

        // Add TITLE StoryStep
        content[position] = StoryStep(
            id = GenerateId.generate(),
            localId = GenerateId.generate(),
            type = StoryTypes.TITLE.type,
            text = title
        )
        position = Rank.after(position)

        // Split summary into paragraphs and create TEXT StorySteps
        val paragraphs = summaryText.split("\n\n").filter { it.isNotBlank() }
        for (paragraph in paragraphs) {
            content[position] = StoryStep(
                id = GenerateId.generate(),
                localId = GenerateId.generate(),
                type = StoryTypes.TEXT.type,
                text = paragraph.trim()
            )
            position = Rank.after(position)
        }

        // Add LAST_SPACE StoryStep
        content[position] = StoryStep(
            id = GenerateId.generate(),
            localId = GenerateId.generate(),
            type = StoryTypes.LAST_SPACE.type,
            text = ""
        )

        return content
    }

    /**
     * Parses markdown syntax in all StorySteps of a document.
     * Converts markdown markers (bold, italic, URLs) to SpanInfo objects.
     */
    private fun parseDocumentMarkdown(document: Document): Document {
        val parsedContent = document.content.mapValues { (_, storyStep) ->
            InlineMarkdownParser.parseMarkdown(storyStep)
        }
        return document.copy(content = parsedContent)
    }

    /**
     * Maximum number of documents that can be summarized in a single request.
     * Prevents excessive processing time and memory usage.
     */
    private const val MAX_DOCUMENTS_FOR_SUMMARY = 20

    /**
     * Maximum total character count for combined document text before sending to GenAI.
     * Prevents exceeding model context limits and controls costs.
     */
    private const val MAX_COMBINED_TEXT_LENGTH = 100_000
}
