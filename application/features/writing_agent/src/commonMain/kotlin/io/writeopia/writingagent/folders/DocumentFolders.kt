package io.writeopia.writingagent.folders

import io.writeopia.app.dto.writingagent.FolderCandidate

/**
 * What the agent needs to file the open document: where it is, where it could go, and how to move
 * it. The screen's ViewModel provides this; the agent decides when to offer it.
 */
interface DocumentFolders {
    /** The id of the folder the document is in. [io.writeopia.sdk.models.document.Folder.ROOT_PATH] at the root. */
    suspend fun currentFolderId(): String

    /** The workspace the document is in, so the backend can rank its folders. Blank when unknown. */
    suspend fun workspaceId(): String

    /** The folders the document could move to, at most [limit], in the order the menu shows them. */
    suspend fun candidates(limit: Int): List<FolderCandidate>

    suspend fun moveTo(folderId: String)

    /**
     * Makes a folder for the document, named by the AI from its title and text, and moves the
     * document there. The result is the name of the new folder.
     */
    suspend fun createFolderAndMove(documentTitle: String, documentText: String): Result<String>
}
