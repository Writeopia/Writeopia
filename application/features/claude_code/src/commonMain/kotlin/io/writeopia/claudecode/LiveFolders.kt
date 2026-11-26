package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder

/** The folders Claude Code can put documents in, and make. */
interface LiveFolders {
    /** The folder the person has open: new folders and documents go there unless told otherwise. */
    fun currentFolderId(): String

    suspend fun exists(folderId: String): Boolean

    /** Makes a folder named [name] inside [parentId] and returns its id. */
    suspend fun create(name: String, parentId: String): String

    /** Every folder of the workspace, deleted ones left out. */
    suspend fun list(): List<LiveFolder>
}

/**
 * The folders of the app's database, like the folders made from the side menu: the workspace
 * sync sends them to the backend.
 */
class RepositoryLiveFolders(
    private val folderRepository: FolderRepository,
    private val workspaceId: suspend () -> String,
    private val currentFolder: () -> String,
) : LiveFolders {

    override fun currentFolderId(): String = currentFolder()

    override suspend fun exists(folderId: String): Boolean =
        folderId == Folder.ROOT_PATH || folderRepository.getFolderById(folderId)?.deleted == false

    override suspend fun create(name: String, parentId: String): String {
        val folder = Folder.fromName(name, workspaceId()).copy(parentId = parentId)
        folderRepository.createFolder(folder)
        folderRepository.refreshFolders()
        return folder.id
    }

    override suspend fun list(): List<LiveFolder> =
        folderRepository.getFoldersForWorkspace(workspaceId())
            .filter { folder -> !folder.deleted }
            .map { folder -> LiveFolder(folder.id, folder.title, folder.parentId) }
}
