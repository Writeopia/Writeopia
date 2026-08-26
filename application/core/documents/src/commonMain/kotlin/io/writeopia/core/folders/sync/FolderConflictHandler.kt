@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder
import kotlin.time.ExperimentalTime

class FolderConflictHandler(
    private val folderRepository: FolderRepository
) {

    /**
     * Handle conflicts with folders that were updated both locally and in the backend.
     *
     * @param localFolders The folders that exist locally
     * @param externalFolders The folders from the backend
     *
     * @return The folders that need to be sent to the backend (local winners + local-only folders)
     */
    suspend fun handleConflict(
        localFolders: List<Folder>,
        externalFolders: List<Folder>,
    ): List<Folder> {
        val localFoldersById = localFolders.associateBy { it.id }
        val foldersToSend = mutableListOf<Folder>()

        // Process external folders - create or update locally
        externalFolders.forEach { externalFolder ->
            val localFolder = localFoldersById[externalFolder.id]

            if (localFolder == null) {
                // Folder doesn't exist locally - create it with server's lastSyncedAt
                folderRepository.createFolder(externalFolder)
            } else {
                // Folder exists both locally and externally - resolve conflict (most recent wins)
                val winner = if (localFolder.lastUpdatedAt >= externalFolder.lastUpdatedAt) {
                    foldersToSend.add(localFolder) // Local is newer, send to backend
                    // Keep local folder as-is, don't update lastSyncedAt with client time
                    localFolder
                } else {
                    // External folder wins - keep its lastSyncedAt from server
                    externalFolder
                }
                folderRepository.updateFolder(winner)
            }
        }

        // Add local-only folders (not in external) to send list
        val externalFolderIds = externalFolders.map { it.id }.toSet()
        localFolders.filter { it.id !in externalFolderIds }.forEach { localOnlyFolder ->
            foldersToSend.add(localOnlyFolder)
        }

        return foldersToSend
    }
}
