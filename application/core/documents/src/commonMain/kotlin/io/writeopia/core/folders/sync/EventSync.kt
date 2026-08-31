@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.response.SyncEventApi
import kotlin.time.ExperimentalTime

/**
 * Handles syncing of delete and move events from the server.
 * This ensures that when a document/folder is deleted or moved on one device,
 * other devices receive and process these events on app startup.
 *
 * Also handles pushing local pending deletions (soft-deleted items) to the backend.
 */
class EventSync(
    private val documentRepository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi
) {
    /**
     * Syncs events from the server since the last event sync.
     * Should be called on app startup before loading documents.
     *
     * First pushes any local pending deletions to the backend, then pulls events from server.
     */
    suspend fun syncEvents(workspaceId: String): ResultData<Unit> {
        try {
            if (workspaceId == Workspace.disconnectedWorkspace().id) {
                return ResultData.Idle()
            }

            val workspace = authRepository.getWorkspace()
                ?: return ResultData.Idle()

            // First, push any local pending deletions to the backend
            syncLocalDeletionsToBackend(workspaceId)

            val response = documentsApi.getEventsDiff(
                workspaceId = workspaceId,
                lastEventSync = workspace.lastEventSync
            )

            return when (response) {
                is ResultData.Complete -> {
                    val eventDiff = response.data

                    // Process events in order (they're sorted by createdAt ASC)
                    processEvents(eventDiff.events)

                    // Update lastEventSync in workspace
                    authRepository.updateLastEventSync(workspaceId, eventDiff.serverTimestamp)

                    // Refresh local data to reflect the changes
                    documentRepository.refreshDocuments()
                    folderRepository.refreshFolders()

                    ResultData.Complete(Unit)
                }
                is ResultData.Error -> ResultData.Error(null)
                is ResultData.Loading -> ResultData.Loading()
                is ResultData.Idle -> ResultData.Idle()
                is ResultData.InProgress -> ResultData.Loading()
            }
        } catch (e: Exception) {
            println("Error syncing events: ${e.message}")
            return ResultData.Error(e)
        }
    }

    /**
     * Pushes local pending deletions (soft-deleted items) to the backend.
     * On success, hard deletes the items locally.
     * On failure, items remain soft-deleted for retry on next sync.
     */
    private suspend fun syncLocalDeletionsToBackend(workspaceId: String) {
        try {
            // Sync soft-deleted documents
            val softDeletedDocuments = documentRepository.getSoftDeletedDocuments(workspaceId)
            for (document in softDeletedDocuments) {
                val result = documentsApi.deleteDocuments(
                    documentIds = listOf(document.id),
                    workspaceId = workspaceId
                )
                if (result is ResultData.Complete) {
                    // Backend confirmed deletion, hard delete locally
                    documentRepository.hardDeleteDocumentByIds(setOf(document.id))
                }
                // If failed, document remains soft-deleted for retry on next sync
            }

            // Sync soft-deleted folders
            val softDeletedFolders = folderRepository.getSoftDeletedFolders(workspaceId)
            for (folder in softDeletedFolders) {
                val result = documentsApi.deleteFolder(
                    folderId = folder.id,
                    workspaceId = workspaceId
                )
                if (result is ResultData.Complete) {
                    // Backend confirmed deletion, hard delete locally
                    folderRepository.hardDeleteFolderById(folder.id)
                }
                // If failed, folder remains soft-deleted for retry on next sync
            }
        } catch (e: Exception) {
            // Log but continue - items will remain soft-deleted for retry
            println("Error syncing local deletions: ${e.message}")
        }
    }

    /**
     * Processes events from the server.
     * Events from the server represent confirmed deletions, so we use hard delete.
     */
    private suspend fun processEvents(events: List<SyncEventApi>) {
        events.forEach { event ->
            try {
                when (event.eventType) {
                    "DELETE_DOCUMENT" -> {
                        // Events from server are confirmed deletions, use hard delete
                        documentRepository.hardDeleteDocumentByIds(setOf(event.entityId))
                    }
                    "DELETE_FOLDER" -> {
                        // Events from server are confirmed deletions, use hard delete
                        folderRepository.hardDeleteFolderById(event.entityId)
                    }
                    "MOVE_DOCUMENT" -> {
                        event.newParentId?.let { newParentId ->
                            documentRepository.moveToFolder(event.entityId, newParentId)
                        }
                    }
                    "MOVE_FOLDER" -> {
                        event.newParentId?.let { newParentId ->
                            folderRepository.moveToFolder(event.entityId, newParentId)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log but continue processing other events
                println("Error processing event ${event.id}: ${e.message}")
            }
        }
    }
}
