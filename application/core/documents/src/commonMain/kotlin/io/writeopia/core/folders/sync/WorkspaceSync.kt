package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository

//Todo: Implement sync for workspace
class WorkspaceSync(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
) {

    suspend fun syncWorkspace(workspaceId: String) {
        val workspace = authRepository.getWorkspace()

        val folders = folderRepository.getFoldersForWorkspace(workspaceId)
        val documents = documentRepository.loadDocumentsForWorkspace(
            orderBy = OrderBy.NAME.type,
            workspaceId = workspaceId,
            instant = workspace.lastSync
        )

        val response = documentsApi.getWorkspaceNewData(workspaceId, workspace.lastSync)
        val (newDocuments, newFolders) = if (response is ResultData.Complete) response.data else return

        val localOutdatedDocs = documentRepository.loadOutdatedDocuments(workspaceId)
        val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)

        val documentsNotSent = documentConflictHandler.handleConflict(
            localOutdatedDocs,
            newDocuments,
        )
        
        val documentsNotSent = documentConflictHandler.handleConflict(
            localOutdatedDocs,
            newDocuments,
        )

    }
}
