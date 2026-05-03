@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.sync.SyncBuffer
import io.writeopia.sdk.persistence.core.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A folder repository that stores folders in memory and syncs them to a backend server.
 * Uses [SyncBuffer] to batch and debounce sync operations.
 *
 * @param inMemoryRepository The underlying in-memory repository for local storage
 * @param sendFolders Function to send folders to the backend
 * @param deleteFolder Function to delete a folder from the backend
 * @param getAuthToken Function to get the current auth token
 * @param getWorkspaceId Function to get the current workspace ID
 * @param scope CoroutineScope for async operations
 * @param debounceMs Debounce duration for sync operations (default 2000ms)
 */
class BackendSyncFolderRepository(
    private val inMemoryRepository: InMemoryFolderRepository,
    private val sendFolders: suspend (List<Folder>, String, String) -> ResultData<Unit>,
    private val deleteFolder: suspend (String, String, String) -> ResultData<Unit>,
    private val getAuthToken: suspend () -> String?,
    private val getWorkspaceId: suspend () -> String,
    private val scope: CoroutineScope,
    debounceMs: Long = 2000L,
    maxBatchSize: Int = 50
) : FolderRepository {

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val folderSyncBuffer = SyncBuffer<Folder>(
        scope = scope,
        debounceMs = debounceMs,
        maxBatchSize = maxBatchSize,
        onFlush = ::flushFolders
    )

    private val deleteSyncBuffer = SyncBuffer<String>(
        scope = scope,
        debounceMs = debounceMs,
        maxBatchSize = maxBatchSize,
        onFlush = ::flushDeletes
    )

    private suspend fun flushFolders(folders: List<Folder>) {
        if (folders.isEmpty()) return

        _syncState.value = SyncState.SYNCING

        val token = getAuthToken()
        if (token == null) {
            _syncState.value = SyncState.ERROR
            return
        }

        val workspaceId = getWorkspaceId()

        // Deduplicate by folder ID, keeping the latest version
        val deduplicatedFolders = folders
            .groupBy { it.id }
            .map { (_, docs) -> docs.last() }

        val result = sendFolders(deduplicatedFolders, workspaceId, token)

        _syncState.value = when (result) {
            is ResultData.Complete -> SyncState.SYNCED
            else -> SyncState.ERROR
        }
    }

    private suspend fun flushDeletes(folderIds: List<String>) {
        if (folderIds.isEmpty()) return

        _syncState.value = SyncState.SYNCING

        val token = getAuthToken()
        if (token == null) {
            _syncState.value = SyncState.ERROR
            return
        }

        val workspaceId = getWorkspaceId()

        // Delete each folder individually
        var hasError = false
        folderIds.distinct().forEach { folderId ->
            val result = deleteFolder(folderId, workspaceId, token)
            if (result !is ResultData.Complete) {
                hasError = true
            }
        }

        _syncState.value = if (hasError) SyncState.ERROR else SyncState.SYNCED
    }

    /**
     * Immediately flush all pending sync operations.
     * Call this when leaving the editor or when the app goes to background.
     */
    suspend fun flushSync() {
        folderSyncBuffer.flushNow()
        deleteSyncBuffer.flushNow()
    }

    /**
     * Load folders from backend into memory.
     */
    suspend fun loadFromBackend(folders: List<Folder>) {
        folders.forEach { folder ->
            inMemoryRepository.createFolder(folder)
        }
    }

    // Delegate methods to inMemoryRepository

    override suspend fun getFolderById(id: String): Folder? =
        inMemoryRepository.getFolderById(id)

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder> =
        inMemoryRepository.getFolderByParentId(parentId, workspaceId)

    override suspend fun getFoldersForWorkspaceAfterTime(workspaceId: String, instant: Instant): List<Folder> =
        inMemoryRepository.getFoldersForWorkspaceAfterTime(workspaceId, instant)

    override suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder> =
        inMemoryRepository.getFoldersForWorkspace(workspaceId)

    override suspend fun createFolder(folder: Folder) {
        inMemoryRepository.createFolder(folder)
        _syncState.value = SyncState.PENDING
        folderSyncBuffer.add(folder)
    }

    override suspend fun updateFolder(folder: Folder) {
        inMemoryRepository.updateFolder(folder)
        _syncState.value = SyncState.PENDING
        folderSyncBuffer.add(folder)
    }

    override suspend fun setLastUpdated(folderId: String, long: Long) {
        inMemoryRepository.setLastUpdated(folderId, long)
        val folder = inMemoryRepository.getFolderById(folderId)
        if (folder != null) {
            _syncState.value = SyncState.PENDING
            folderSyncBuffer.add(folder)
        }
    }

    override suspend fun deleteFolderById(folderId: String) {
        inMemoryRepository.deleteFolderById(folderId)
        _syncState.value = SyncState.PENDING
        deleteSyncBuffer.add(folderId)
    }

    override suspend fun deleteFolderByParent(folderId: String) {
        inMemoryRepository.deleteFolderByParent(folderId)
    }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        inMemoryRepository.favoriteDocumentByIds(ids)
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        inMemoryRepository.unFavoriteDocumentByIds(ids)
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        inMemoryRepository.moveToFolder(documentId, parentId)
    }

    override suspend fun refreshFolders() {
        inMemoryRepository.refreshFolders()
    }

    override suspend fun listenForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Folder>>> =
        inMemoryRepository.listenForFoldersByParentId(parentId, workspaceId)

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
        inMemoryRepository.stopListeningForFoldersByParentId(parentId, workspaceId)
    }

    override suspend fun localOutDatedFolders(workspaceId: String): List<Folder> =
        inMemoryRepository.localOutDatedFolders(workspaceId)
}
