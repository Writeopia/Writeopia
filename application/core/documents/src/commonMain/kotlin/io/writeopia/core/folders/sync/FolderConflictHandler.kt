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
     * @param localFolders The folders changed locally since they were last sent (outdated)
     * @param externalFolders The folders from the backend
     *
     * @return The folders that need to be sent to the backend (local winners + local-only folders)
     */
    suspend fun handleConflict(
        localFolders: List<Folder>,
        externalFolders: List<Folder>,
    ): List<Folder> {
        val outdatedLocalById = localFolders.associateBy { it.id }
        val foldersToSend = mutableListOf<Folder>()
        val now = Clock.System.now()

        // Process external folders - create or update locally
        externalFolders.forEach { externalFolder ->
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

            // Only a local copy with unsent changes can win a conflict. An up-to-date local
            // copy has nothing to send, so the backend's version simply replaces it.
            val outdatedLocal = outdatedLocalById[externalFolder.id]

            when {
                // A new folder is in sync, but its contents are not: contentSyncedAt stays null
                // so the first time it is opened, everything inside it is fetched.
                existingFolder == null -> folderRepository.createFolder(syncedExternal)

                outdatedLocal != null && outdatedLocal.lastUpdatedAt >= externalFolder.lastUpdatedAt -> {
                    foldersToSend.add(outdatedLocal) // Local is newer, send to backend
                    // Keep local folder as-is, don't update lastSyncedAt with client time
                    folderRepository.updateFolder(outdatedLocal)
                }

                // Keep this device's contents watermark: the backend knows nothing about it, and
                // receiving the folder itself says nothing about whether its contents were fetched.
                else -> folderRepository.updateFolder(
                    syncedExternal.copy(contentSyncedAt = existingFolder.contentSyncedAt)
                )
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
     * The watermark is this device's clock, never the backend's lastUpdatedAt: a backend
     * timestamp ahead of this device would otherwise hide the next local edit from the
     * outdated check. If the backend clock is ahead, the folder is merely re-sent once.
     */
    private fun Folder.markSynced(now: kotlin.time.Instant): Folder =
        copy(lastSyncedAt = now)
}
