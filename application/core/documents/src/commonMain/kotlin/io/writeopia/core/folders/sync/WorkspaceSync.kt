package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock

class WorkspaceSync(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
    private val documentConflictHandler: DocumentConflictHandler,
) {

    suspend fun syncWorkspace(workspaceId: String): ResultData<Unit> {
        try {
            val authToken = authRepository.getAuthToken() ?: return ResultData.Error(null)
            val workspace = authRepository.getWorkspace()

            val response = documentsApi.getWorkspaceNewData(
                workspaceId,
                workspace.lastSync,
                authToken
            )
            val (newDocuments, newFolders) = if (response is ResultData.Complete) {
                println("Received response from API")
                response.data
            } else {
                println("Error syncing workspace")
                return ResultData.Error()
            }

            val localOutdatedDocs =
                documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId)
            val localOutdatedFolders = folderRepository.localOutDatedFolders(workspaceId)

            println("local outdated docs: ${localOutdatedFolders.size}")

            val documentsNotSent = documentConflictHandler.handleConflict(
                localOutdatedDocs,
                newDocuments,
            )

            val foldersNotSent = documentConflictHandler.handleConflictForFolders(
                localFolders = localOutdatedFolders,
                externalFolders = newFolders,
            )

            println("sending documents")
            val resultSendDocuments = documentsApi.sendDocuments(documentsNotSent, authToken)
            println("sending folders")
            val resultSendFolders = documentsApi.sendFolders(foldersNotSent, authToken)

            if (resultSendDocuments is ResultData.Complete && resultSendFolders is ResultData.Complete) {
                println("documents sent")
                val now = Clock.System.now()
                // If everything ran accordingly, update the sync time of the folder.
                documentsNotSent.forEach { document ->
                    val newDocument = document.copy(lastSyncedAt = now)
                    documentRepository.saveDocumentMetadata(newDocument)
                }

                documentRepository.refreshDocuments()
                folderRepository.refreshFolders()

                return ResultData.Complete(Unit)
            } else {
                println("documents NOT sent")
                return ResultData.Error()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ResultData.Error(e)
        }
    }
}
