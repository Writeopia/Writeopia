@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.repository

import io.writeopia.sdk.manager.DocumentUpdate
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryStepTombstone
import io.writeopia.sdk.search.DocumentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

    /**
     * The favorite documents of the whole workspace, wherever they are, updated as favorites
     * change. Implementations that can't observe the storage read them once.
     */
    suspend fun listenForFavoriteDocuments(workspaceId: String): Flow<List<Document>> =
        flow { emit(loadFavDocumentsForWorkspace(OrderBy.UPDATE.type, workspaceId)) }

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

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Double, documentId: String)

    suspend fun updateStoryStepUrl(url: String, id: String)

    /**
     * The steps deleted on this device that the backend has not been told about yet.
     * Written by [deleteStoryStep]; a step with a tombstone is never saved again.
     */
    suspend fun loadStoryStepTombstones(workspaceId: String): List<StoryStepTombstone> = emptyList()

    /** Forgets the tombstones of [stepIds], once the backend has them. */
    suspend fun deleteStoryStepTombstones(stepIds: Collection<String>) {}

    /**
     * Removes steps deleted elsewhere. No tombstone is kept: the deletion came from the backend,
     * which has one already.
     */
    suspend fun deleteStoryStepsByIds(stepIds: Collection<String>, workspaceId: String) {}

    suspend fun deleteDocument(document: Document, workspaceId: String)

    /**
     * Soft delete: marks documents as deleted but keeps in database.
     * The documents will be synced to backend and then hard deleted once confirmed.
     */
    suspend fun deleteDocumentByIds(ids: Set<String>, workspaceId: String)

    suspend fun deleteDocumentByFolder(folderId: String, workspaceId: String)

    /**
     * Hard delete: permanently removes documents from database.
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteDocumentByIds(ids: Set<String>, workspaceId: String)

    /**
     * Get all soft-deleted documents for a workspace.
     * Use this to find documents that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedDocuments(workspaceId: String): List<Document>

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

    suspend fun queryUnsyncedImagesSteps(): List<StoryStep>

    /**
     * Sets the published status of a document.
     * This is an optional server-side operation.
     */
    suspend fun setPublished(documentId: String, published: Boolean) {
        // Default implementation does nothing - requires server connectivity
    }

    /**
     * Checks if a document is published.
     * This is an optional server-side operation.
     */
    suspend fun isPublished(documentId: String): Boolean {
        // Default implementation returns false - requires server connectivity
        return false
    }
}
