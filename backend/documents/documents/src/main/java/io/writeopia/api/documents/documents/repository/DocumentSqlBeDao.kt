@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sql.DocumentEntityQueries
import io.writeopia.sql.FolderEntityQueries
import io.writeopia.sql.StoryStepEntityQueries
import io.writeopia.sql.UserFavoriteEntityQueries
import kotlin.time.ExperimentalTime

class DocumentSqlBeDao(
    private val documentQueries: DocumentEntityQueries?,
    private val storyStepQueries: StoryStepEntityQueries?,
    private val foldersQueries: FolderEntityQueries?,
    private val userFavoriteQueries: UserFavoriteEntityQueries? = null,
) : DocumentSearch {

    override suspend fun search(query: String, workspaceId: String): List<Document> = emptyList()

    override suspend fun getLastUpdatedAt(userId: String): List<Document> = emptyList()

    fun insertDocumentWithContent(document: Document) {}

    fun insertFolder(folder: Folder) {}

    fun loadDocumentById(id: String, workspaceId: String): Document? = null

    fun loadFolderById(id: String, workspaceId: String): Folder? = null

    fun loadDocumentWithContentByIds(id: List<String>): List<Document> = emptyList()

    fun loadDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> = emptyList()

    fun loadFavDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> = emptyList()

    fun loadDocumentsWithContentByUserIdAfterTime(userId: String, time: Long): List<Document> = emptyList()

    fun loadDocumentsWithContentFolderIdAfterTime(folderId: String, workspaceId: String, time: Long): List<Document> = emptyList()

    fun loadDocumentsWithContentByWorkspaceIdAfterTime(workspaceId: String, time: Long): List<Document> = emptyList()

    fun deleteDocumentById(documentId: String) {}

    fun deleteDocumentByIds(ids: Set<String>) {}

    fun loadDocumentWithContentById(documentId: String, workspaceId: String): Document? = null

    fun loadDocumentByParentId(parentId: String): List<Document> = emptyList()

    fun loadDocumentIdsByParentId(parentId: String): List<String> = emptyList()

    fun loadAllFoldersByWorkspaceId(workspaceId: String): List<Folder> = emptyList()

    fun loadFoldersByParentId(parentId: String): List<Folder> = emptyList()

    fun deleteDocumentsByUserId(userId: String) {}

    fun deleteDocumentsByFolderId(folderId: String) {}

    fun addUserFavorite(userId: String, documentId: String, workspaceId: String) {}

    fun removeUserFavorite(userId: String, documentId: String) {}

    fun isUserFavorite(userId: String, documentId: String): Boolean = false

    fun getUserFavoriteDocumentIds(userId: String, workspaceId: String): List<String> = emptyList()

    fun moveToFolder(documentId: String, parentId: String) {}

    fun deleteFolder(folderId: String) {}

    fun moveFolderToFolder(folderId: String, parentId: String) {}

    fun loadDocumentWithContentByTitle(): Document? = null
}
