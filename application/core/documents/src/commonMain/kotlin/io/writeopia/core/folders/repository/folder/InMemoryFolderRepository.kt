package io.writeopia.core.folders.repository.folder

import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

class InMemoryFolderRepository : FolderRepository {

    private val mutableMap = mutableMapOf<String, List<Folder>>()
    private val foldersStateFlow = MutableStateFlow<Map<String, List<Folder>>>(mutableMap)

    override suspend fun createFolder(folder: Folder) {
        println("createFolder")
        mutableMap[folder.id] = listOf(folder)
        refreshState()
    }

    override suspend fun updateFolder(folder: Folder) {
        mutableMap[folder.id] = listOf(folder)
        refreshState()
    }

    override suspend fun getFoldersForWorkspaceAfterTime(
        userId: String,
        instant: Instant
    ): List<Folder> = emptyList()

    override suspend fun getFoldersForWorkspace(workspaceId: String): List<Folder> = emptyList()

    override suspend fun listenForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Folder>>> {
        return foldersStateFlow.asStateFlow()
    }

    override suspend fun deleteFolderById(folderId: String) {
        mutableMap.remove(folderId)
        refreshState()
    }

    override suspend fun refreshFolders() {
        foldersStateFlow.value = mutableMap
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {}

    override suspend fun deleteFolderByParent(folderId: String) {}

    override suspend fun setLastUpdated(folderId: String, long: Long) {}

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {}

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {}

    override suspend fun getFolderById(id: String): Folder =
        Folder.fromName("folder", "disconnecter_user").copy(id = id)

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder> =
        emptyList()

    private fun refreshState() {
        foldersStateFlow.value = mutableMap
    }

    companion object {
        private var instance: InMemoryFolderRepository? = null

        fun singleton(): InMemoryFolderRepository {
            return instance ?: run {
                instance = InMemoryFolderRepository()
                instance!!
            }
        }
    }

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
    }

    override suspend fun localOutDatedFolders(workspaceId: String): List<Folder> = emptyList()
}
