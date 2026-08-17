@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A no-op folder repository for the webapp. The webapp should only use backend data,
 * so this repository returns empty states and does not persist any data locally.
 */
class InMemoryFolderRepository : FolderRepository {

    private val emptyStateFlow = MutableStateFlow<Map<String, List<Folder>>>(emptyMap())

    override suspend fun createFolder(folder: Folder) {
        // No-op: webapp uses backend data only
    }

    override suspend fun updateFolder(folder: Folder) {
        // No-op: webapp uses backend data only
    }

    override suspend fun getFoldersForWorkspaceAfterTime(
        userId: String,
        instant: Instant
    ): List<Folder> = emptyList()

    override suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder> = emptyList()

    override suspend fun listenForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Folder>>> = emptyStateFlow.asStateFlow()

    override suspend fun deleteFolderById(folderId: String) {
        // No-op: webapp uses backend data only
    }

    override suspend fun refreshFolders() {
        // No-op: webapp uses backend data only
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        // No-op: webapp uses backend data only
    }

    override suspend fun deleteFolderByParent(folderId: String) {
        // No-op: webapp uses backend data only
    }

    override suspend fun setLastUpdated(folderId: String, long: Long) {
        // No-op: webapp uses backend data only
    }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        // No-op: webapp uses backend data only
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        // No-op: webapp uses backend data only
    }

    override suspend fun getFolderById(id: String): Folder? = null

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder> =
        emptyList()

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
        // No-op: webapp uses backend data only
    }

    override suspend fun localOutDatedFolders(workspaceId: String): List<Folder> = emptyList()

    companion object {
        private var instance: InMemoryFolderRepository? = null

        fun singleton(): InMemoryFolderRepository = instance ?: run {
            instance = InMemoryFolderRepository()
            instance!!
        }
    }
}
