@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder
import kotlin.time.Clock
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
        val now = Clock.System.now()

        // Process external folders - create or update locally
        externalFolders.forEach { externalFolder ->
            val localFolder = localFoldersById[externalFolder.id]

            // IMPORTANT: Check if folder is soft-deleted locally before creating/updating
            // A soft-deleted folder should never be recreated from backend data
            val existingFolder = folderRepository.getFolderById(externalFolder.id)
            if (existingFolder?.deleted == true) {
                // Folder is soft-deleted locally, skip it to prevent resurrection
                return@forEach
            }

            // A folder taken from the backend is in sync by definition, so stamp it as synced.
            // The API payload carries no lastSyncedAt; leaving it null would make the folder
            // count as outdated and be re-sent on every sync.
            val syncedExternal = externalFolder.markSynced(now)

            if (localFolder == null) {
                // Folder doesn't exist locally - create it
                folderRepository.createFolder(syncedExternal)
            } else {
                // Folder exists both locally and externally - resolve conflict (most recent wins)
                val winner = if (localFolder.lastUpdatedAt >= externalFolder.lastUpdatedAt) {
                    foldersToSend.add(localFolder) // Local is newer, send to backend
                    // Keep local folder as-is, don't update lastSyncedAt with client time
                    localFolder
                } else {
                    // External folder wins
                    syncedExternal
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

    /**
     * lastSyncedAt is never earlier than lastUpdatedAt, so a server clock ahead of this
     * device's cannot make the folder look outdated.
     */
    private fun Folder.markSynced(now: kotlin.time.Instant): Folder =
        copy(lastSyncedAt = maxOf(now, lastUpdatedAt))
}
