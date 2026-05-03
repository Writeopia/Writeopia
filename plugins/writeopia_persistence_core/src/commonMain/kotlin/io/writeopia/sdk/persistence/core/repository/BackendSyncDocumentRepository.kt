@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.repository

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.sync.SyncBuffer
import io.writeopia.sdk.persistence.core.sync.SyncState
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A document repository that stores documents in memory and syncs them to a backend server.
 * Uses [SyncBuffer] to batch and debounce sync operations.
 *
 * @param inMemoryRepository The underlying in-memory repository for local storage
 * @param sendDocuments Function to send documents to the backend
 * @param deleteDocuments Function to delete documents from the backend
 * @param getAuthToken Function to get the current auth token
 * @param getWorkspaceId Function to get the current workspace ID
 * @param scope CoroutineScope for async operations
 * @param debounceMs Debounce duration for sync operations (default 2000ms)
 */
class BackendSyncDocumentRepository(
    private val inMemoryRepository: InMemoryDocumentRepository,
    private val sendDocuments: suspend (List<Document>, String, String) -> ResultData<Unit>,
    private val deleteDocuments: suspend (List<String>, String, String) -> ResultData<Unit>,
    private val getAuthToken: suspend () -> String?,
    private val getWorkspaceId: suspend () -> String,
    private val scope: CoroutineScope,
    debounceMs: Long = 2000L,
    maxBatchSize: Int = 50
) : DocumentRepository {

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val documentSyncBuffer = SyncBuffer<Document>(
        scope = scope,
        debounceMs = debounceMs,
        maxBatchSize = maxBatchSize,
        onFlush = ::flushDocuments
    )

    private val deleteSyncBuffer = SyncBuffer<String>(
        scope = scope,
        debounceMs = debounceMs,
        maxBatchSize = maxBatchSize,
        onFlush = ::flushDeletes
    )

    private suspend fun flushDocuments(documents: List<Document>) {
        if (documents.isEmpty()) return

        _syncState.value = SyncState.SYNCING

        val token = getAuthToken()
        if (token == null) {
            _syncState.value = SyncState.ERROR
            return
        }

        val workspaceId = getWorkspaceId()

        // Deduplicate by document ID, keeping the latest version
        val deduplicatedDocuments = documents
            .groupBy { it.id }
            .map { (_, docs) -> docs.last() }

        val result = sendDocuments(deduplicatedDocuments, workspaceId, token)

        _syncState.value = when (result) {
            is ResultData.Complete -> SyncState.SYNCED
            else -> SyncState.ERROR
        }
    }

    private suspend fun flushDeletes(documentIds: List<String>) {
        if (documentIds.isEmpty()) return

        _syncState.value = SyncState.SYNCING

        val token = getAuthToken()
        if (token == null) {
            _syncState.value = SyncState.ERROR
            return
        }

        val workspaceId = getWorkspaceId()

        val result = deleteDocuments(documentIds.distinct(), workspaceId, token)

        _syncState.value = when (result) {
            is ResultData.Complete -> SyncState.SYNCED
            else -> SyncState.ERROR
        }
    }

    /**
     * Immediately flush all pending sync operations.
     * Call this when leaving the editor or when the app goes to background.
     */
    suspend fun flushSync() {
        documentSyncBuffer.flushNow()
        deleteSyncBuffer.flushNow()
    }

    /**
     * Load all documents from the in-memory store.
     */
    fun getAllDocuments(): List<Document> = inMemoryRepository.getAllDocuments()

    /**
     * Clear all documents and load new ones from backend.
     */
    suspend fun loadFromBackend(documents: List<Document>) {
        inMemoryRepository.loadDocuments(documents)
    }

    // Delegate most methods to inMemoryRepository

    override suspend fun loadDocumentsWorkspace(workspaceId: String): List<Document> =
        inMemoryRepository.loadDocumentsWorkspace(workspaceId)

    override suspend fun loadDocumentsForFolder(folderId: String, workspaceId: String): List<Document> =
        inMemoryRepository.loadDocumentsForFolder(folderId, workspaceId)

    override suspend fun loadFavDocumentsForWorkspace(orderBy: String, userId: String): List<Document> =
        inMemoryRepository.loadFavDocumentsForWorkspace(orderBy, userId)

    override suspend fun loadDocumentsForWorkspace(
        orderBy: String,
        userId: String,
        instant: Instant
    ): List<Document> =
        inMemoryRepository.loadDocumentsForWorkspace(orderBy, userId, instant)

    override suspend fun loadDocumentById(id: String, workspaceId: String): Document? =
        inMemoryRepository.loadDocumentById(id, workspaceId)

    override suspend fun loadDocumentByIds(ids: List<String>, workspaceId: String): List<Document> =
        inMemoryRepository.loadDocumentByIds(ids, workspaceId)

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> =
        inMemoryRepository.listenForDocumentsByParentId(parentId, workspaceId)

    override suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo> =
        inMemoryRepository.listenForDocumentInfoById(id)

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        inMemoryRepository.loadDocumentsWithContentByIds(ids, orderBy, workspaceId)

    override suspend fun saveDocument(document: Document) {
        inMemoryRepository.saveDocument(document)
        _syncState.value = SyncState.PENDING
        documentSyncBuffer.add(document)
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        inMemoryRepository.saveDocumentMetadata(document)
        // Load the full document to sync
        val fullDocument = inMemoryRepository.loadDocumentById(document.id, "")
        if (fullDocument != null) {
            _syncState.value = SyncState.PENDING
            documentSyncBuffer.add(fullDocument)
        }
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        inMemoryRepository.saveStoryStep(storyStep, position, documentId)
        // Load the full document to sync
        val document = inMemoryRepository.loadDocumentById(documentId, "")
        if (document != null) {
            _syncState.value = SyncState.PENDING
            documentSyncBuffer.add(document)
        }
    }

    override suspend fun updateStoryStepUrl(url: String, id: String) {
        inMemoryRepository.updateStoryStepUrl(url, id)
    }

    override suspend fun deleteDocument(document: Document) {
        inMemoryRepository.deleteDocument(document)
        _syncState.value = SyncState.PENDING
        deleteSyncBuffer.add(document.id)
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        inMemoryRepository.deleteDocumentByIds(ids)
        _syncState.value = SyncState.PENDING
        deleteSyncBuffer.addAll(ids.toList())
    }

    override suspend fun deleteByWorkspace(userId: String) {
        inMemoryRepository.deleteByWorkspace(userId)
    }

    override suspend fun moveDocumentsToWorkspace(oldUserId: String, newUserId: String) {
        inMemoryRepository.moveDocumentsToWorkspace(oldUserId, newUserId)
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        inMemoryRepository.updateStoryStep(storyStep, position, documentId)
        // Load the full document to sync
        val document = inMemoryRepository.loadDocumentById(documentId, "")
        if (document != null) {
            _syncState.value = SyncState.PENDING
            documentSyncBuffer.add(document)
        }
    }

    override suspend fun search(query: String, workspaceId: String): List<Document> =
        inMemoryRepository.search(query, workspaceId)

    override suspend fun getLastUpdatedAt(userId: String): List<Document> =
        inMemoryRepository.getLastUpdatedAt(userId)

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        inMemoryRepository.favoriteDocumentByIds(ids)
        // Load the full documents to sync
        ids.forEach { id ->
            val document = inMemoryRepository.loadDocumentById(id, "")
            if (document != null) {
                _syncState.value = SyncState.PENDING
                documentSyncBuffer.add(document)
            }
        }
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        inMemoryRepository.unFavoriteDocumentByIds(ids)
        // Load the full documents to sync
        ids.forEach { id ->
            val document = inMemoryRepository.loadDocumentById(id, "")
            if (document != null) {
                _syncState.value = SyncState.PENDING
                documentSyncBuffer.add(document)
            }
        }
    }

    override suspend fun deleteDocumentByFolder(folderId: String) {
        inMemoryRepository.deleteDocumentByFolder(folderId)
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        inMemoryRepository.moveToFolder(documentId, parentId)
        // Load the full document to sync
        val document = inMemoryRepository.loadDocumentById(documentId, "")
        if (document != null) {
            _syncState.value = SyncState.PENDING
            documentSyncBuffer.add(document)
        }
    }

    override suspend fun refreshDocuments() {
        inMemoryRepository.refreshDocuments()
    }

    override suspend fun queryUnsyncedImagesSteps(): List<StoryStep> =
        inMemoryRepository.queryUnsyncedImagesSteps()

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
        inMemoryRepository.stopListeningForFoldersByParentId(parentId, workspaceId)
    }

    override suspend fun loadOutdatedDocumentsByFolder(folderId: String, workspaceId: String): List<Document> =
        inMemoryRepository.loadOutdatedDocumentsByFolder(folderId, workspaceId)

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        inMemoryRepository.loadOutdatedDocumentsForWorkspace(workspaceId)

    override suspend fun loadDocumentsByParentId(parentId: String, workspaceId: String): List<Document> =
        inMemoryRepository.loadDocumentsByParentId(parentId, workspaceId)
}
