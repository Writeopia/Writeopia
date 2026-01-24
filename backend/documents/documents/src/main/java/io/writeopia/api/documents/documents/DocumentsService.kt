@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.deleteDocumentsByFolderId
import io.writeopia.api.documents.documents.repository.deleteDocumentsByIds
import io.writeopia.api.documents.documents.repository.deleteFolder
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.getFolderById
import io.writeopia.api.documents.documents.repository.getFoldersByParentId
import io.writeopia.api.documents.documents.repository.moveFolderToFolder
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.documents.repository.saveFolder
import io.writeopia.api.documents.search.SearchDocument
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object DocumentsService {

    suspend fun receiveDocuments(
        documents: List<Document>,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean
    ): Boolean {
        documents.forEach { document ->
            writeopiaDb.saveDocument(document)
        }

        return if (useAi) sendToAiHub(documents, workspaceId) else true
    }

    suspend fun receiveFolders(
        folders: List<Folder>,
        writeopiaDb: WriteopiaDbBackend,
    ): Boolean {
        folders.forEach { folder -> writeopiaDb.saveFolder(folder) }

        return true
    }

    suspend fun search(
        query: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ): ResultData<List<Document>> =
        SearchDocument.search(query, userId, writeopiaDb)

    suspend fun getDocumentById(
        id: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Document? = writeopiaDb.getDocumentById(id, workspaceId)

    suspend fun getFolderById(
        id: String,
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Folder? = writeopiaDb.getFolderById(id, userId)

    suspend fun createFolder(
        parentFolderId: String,
        title: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Folder {
        val now = Clock.System.now()
        val folder = Folder(
            id = GenerateId.generate(),
            parentId = parentFolderId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = workspaceId,
            itemCount = 0
        )
        
        writeopiaDb.saveFolder(folder)
        return folder
    }

    suspend fun upsertDocument(
        document: Document,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean
    ): Document {
        val documentWithWorkspace = document.copy(
            workspaceId = workspaceId,
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = Clock.System.now()
        )
        
        writeopiaDb.saveDocument(documentWithWorkspace)
        
        if (useAi) {
            sendToAiHub(listOf(documentWithWorkspace), workspaceId)
        }
        
        return documentWithWorkspace
    }

    /**
     * Recursively deletes a folder and all its contents (child folders and documents).
     * This follows the same pattern as NotesUseCase.deleteFolderById.
     */
    suspend fun deleteFolder(
        folderId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        val childFolders = writeopiaDb.getFoldersByParentId(folderId)

        // Recursively delete all child folders
        childFolders.forEach { childFolder ->
            deleteFolder(childFolder.id, writeopiaDb)
        }

        // Delete all documents in this folder
        writeopiaDb.deleteDocumentsByFolderId(folderId)

        // Finally delete the folder itself
        writeopiaDb.deleteFolder(folderId)
    }

    suspend fun deleteDocuments(
        documentIds: List<String>,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.deleteDocumentsByIds(documentIds)
    }

    suspend fun moveFolder(
        folderId: String,
        targetParentId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        // Prevent moving a folder into itself
        if (folderId == targetParentId) {
            return false
        }

        writeopiaDb.moveFolderToFolder(folderId, targetParentId)
        return true
    }

    private suspend fun sendToAiHub(documents: List<Document>, workspaceId: String,) =
        wrWebClient.post("${Urls.AI_HUB}/documents/") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }, workspaceId))
        }.status
            .isSuccess()
}
