package io.writeopia.sdk.repository

import io.writeopia.sdk.manager.DocumentUpdate
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.search.DocumentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * DocumentRepository is the repository for using simple CRUD operations in [Document].
 * The implementations of this interface shouldn't control order (sorting) or oder configurations,
 * those need to be passed as parameters.
 */
interface DocumentRepository : DocumentUpdate, DocumentSearch {
    // Change workspaceId for workspace ID in this class.

    suspend fun loadDocumentsForFolder(folderId: String, workspaceId: String): List<Document>

    suspend fun loadDocumentsWorkspace(workspaceId: String): List<Document>

    suspend fun loadFavDocumentsForWorkspace(orderBy: String, workspaceId: String): List<Document>

    suspend fun loadDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String,
        instant: Instant
    ): List<Document>

    suspend fun loadOutdatedDocumentsByFolder(folderId: String, workspaceId: String): List<Document>

    suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document>

    suspend fun loadDocumentById(id: String, workspaceId: String): Document?

    suspend fun loadDocumentByIds(ids: List<String>, workspaceId: String): List<Document>

    suspend fun loadDocumentsWithContentByIds(ids: List<String>, orderBy: String, workspaceId: String): List<Document>

    suspend fun loadDocumentsByParentId(parentId: String, workspaceId: String): List<Document>

    suspend fun listenForDocumentsByParentId(parentId: String, workspaceId: String): Flow<Map<String, List<Document>>>

    suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo?>

    suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String)

    /**
     * Saves document. Both with content and meta data.
     */
    override suspend fun saveDocument(document: Document)

    /**
     * Saves the document meta data. Use this was updating the content of the document is not
     * necessary. This is a much lighter operation than [saveDocument], because it is not
     * necessary to save/update all lines of content.
     */
    override suspend fun saveDocumentMetadata(document: Document)

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String)

    suspend fun deleteDocument(document: Document)

    suspend fun deleteDocumentByIds(ids: Set<String>)

    suspend fun deleteDocumentByFolder(folderId: String)

    suspend fun favoriteDocumentByIds(ids: Set<String>)

    suspend fun unFavoriteDocumentByIds(ids: Set<String>)

    /**
     * Deleted all the documents of a Workspace
     */
    suspend fun deleteByWorkspace(workspaceId: String)

    /**
     * Moves all tickets from one user to another. Use this we would like to pass all the data of
     * documents to another user. When the offline user becomes a new online user, all documents
     * should be moved to the new online user.
     */
    suspend fun moveDocumentsToWorkspace(oldWorkspaceId: String, newWorkspaceId: String)

    suspend fun moveToFolder(documentId: String, parentId: String)

    suspend fun refreshDocuments()
}
