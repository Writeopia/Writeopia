@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.BackendSyncFolderRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.repository.BackendSyncDocumentRepository
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Loads workspace data from the backend into memory-based repositories.
 * Used when the application is in MEMORY_WITH_SYNC persistence mode.
 *
 * @param documentRepository The backend sync document repository to populate
 * @param folderRepository The backend sync folder repository to populate
 * @param authRepository Repository for authentication and workspace information
 * @param documentsApi API client for fetching workspace data
 */
class WorkspaceLoader(
    private val documentRepository: BackendSyncDocumentRepository,
    private val folderRepository: BackendSyncFolderRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi
) {

    /**
     * Loads all workspace data from the backend into memory.
     * Should be called on app startup when using MEMORY_WITH_SYNC mode.
     *
     * @return [ResultData.Complete] if successful, [ResultData.Error] otherwise
     */
    suspend fun loadWorkspaceFromBackend(): ResultData<Unit> {
        return try {
            val authToken = authRepository.getAuthToken()
            if (authToken == null) {
                return ResultData.Error(Exception("Not authenticated"))
            }

            val workspace = authRepository.getWorkspace()
            if (workspace == null) {
                return ResultData.Error(Exception("No workspace selected"))
            }

            // Load all data from the beginning of time
            val response = documentsApi.getWorkspaceNewData(
                workspaceId = workspace.id,
                lastSync = Instant.DISTANT_PAST,
                token = authToken
            )

            when (response) {
                is ResultData.Complete -> {
                    val (documents, folders) = response.data

                    // Load folders first (they may be parents of documents)
                    folderRepository.loadFromBackend(folders)

                    // Then load documents
                    documentRepository.loadFromBackend(documents)

                    ResultData.Complete(Unit)
                }
                is ResultData.Error -> {
                    ResultData.Error(Exception("Failed to load workspace data"))
                }
                else -> {
                    ResultData.Idle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ResultData.Error(e)
        }
    }

    /**
     * Refreshes workspace data from the backend.
     * Merges new data with existing in-memory data.
     *
     * @param since Load only data updated since this time
     * @return [ResultData.Complete] if successful, [ResultData.Error] otherwise
     */
    suspend fun refreshFromBackend(since: Instant = Instant.DISTANT_PAST): ResultData<Unit> {
        return try {
            val authToken = authRepository.getAuthToken()
            if (authToken == null) {
                return ResultData.Error(Exception("Not authenticated"))
            }

            val workspace = authRepository.getWorkspace()
            if (workspace == null) {
                return ResultData.Error(Exception("No workspace selected"))
            }

            val response = documentsApi.getWorkspaceNewData(
                workspaceId = workspace.id,
                lastSync = since,
                token = authToken
            )

            when (response) {
                is ResultData.Complete -> {
                    val (documents, folders) = response.data

                    // Load folders first
                    folderRepository.loadFromBackend(folders)

                    // Then load documents
                    documentRepository.loadFromBackend(documents)

                    ResultData.Complete(Unit)
                }
                is ResultData.Error -> {
                    ResultData.Error(Exception("Failed to refresh workspace data"))
                }
                else -> {
                    ResultData.Idle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ResultData.Error(e)
        }
    }
}
