@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.api.documents.documents.repository.addUserFavorite
import io.writeopia.api.documents.documents.repository.deleteDocumentsByFolderId
import io.writeopia.api.documents.documents.repository.deleteDocumentsByIds
import io.writeopia.api.documents.documents.repository.deleteFolder
import io.writeopia.api.documents.documents.repository.getDocumentById
import io.writeopia.api.documents.documents.repository.getFolderById
import io.writeopia.api.documents.documents.repository.getFoldersByParentId
import io.writeopia.api.documents.documents.repository.getUserFavoriteDocumentIds
import io.writeopia.api.documents.documents.repository.isUserFavorite
import io.writeopia.api.documents.documents.repository.moveFolderToFolder
import io.writeopia.api.documents.documents.repository.removeUserFavorite
import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.documents.documents.repository.saveFolder
import io.writeopia.api.documents.search.SearchDocument
import io.writeopia.connection.ResultData
import io.writeopia.connection.Urls
import io.writeopia.connection.wrWebClient
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
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
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Folder? = writeopiaDb.getFolderById(id, workspaceId)

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

    suspend fun cloneDocuments(
        documentIds: List<String>,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend,
        useAi: Boolean
    ): List<Document> {
        val now = Clock.System.now()
        val clonedDocuments = mutableListOf<Document>()

        for (documentId in documentIds) {
            val originalDocument = writeopiaDb.getDocumentWithContentById(documentId, workspaceId)
                ?: continue

            // Skip if document doesn't belong to the workspace
            if (originalDocument.workspaceId != workspaceId) continue

            // Clone the content with new IDs for each StoryStep
            val clonedContent = originalDocument.content.mapValues { (_, storyStep) ->
                cloneStoryStep(storyStep)
            }

            val clonedDocument = originalDocument.copy(
                id = GenerateId.generate(),
                title = "${originalDocument.title} (Copy)",
                content = clonedContent,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                favorite = false
            )

            writeopiaDb.saveDocument(clonedDocument)
            clonedDocuments.add(clonedDocument)
        }

        if (useAi && clonedDocuments.isNotEmpty()) {
            sendToAiHub(clonedDocuments, workspaceId)
        }

        return clonedDocuments
    }

    private fun cloneStoryStep(storyStep: StoryStep): StoryStep {
        return storyStep.copy(
            id = GenerateId.generate(),
            localId = GenerateId.generate(),
            steps = storyStep.steps.map { cloneStoryStep(it) }
        )
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

    suspend fun favoriteDocument(
        userId: String,
        documentId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.addUserFavorite(userId, documentId, workspaceId)
    }

    suspend fun unFavoriteDocument(
        userId: String,
        documentId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.removeUserFavorite(userId, documentId)
    }

    suspend fun isDocumentFavorited(
        userId: String,
        documentId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        return writeopiaDb.isUserFavorite(userId, documentId)
    }

    suspend fun getUserFavoriteDocumentIds(
        userId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): List<String> {
        return writeopiaDb.getUserFavoriteDocumentIds(userId, workspaceId)
    }

    suspend fun moveFolder(
        folderId: String,
        targetParentId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        // Prevent moving a folder into itself
        if (folderId == targetParentId) {
            return false
        }

        // Prevent creating a cycle by checking if targetParentId is a descendant of folderId
        // Traverse up from targetParentId to root, if we encounter folderId, it would create a cycle
        if (wouldCreateCycle(folderId, targetParentId, workspaceId = workspaceId, writeopiaDb)) {
            return false
        }

        writeopiaDb.moveFolderToFolder(folderId, targetParentId)
        return true
    }

    /**
     * Checks if moving folderId to targetParentId would create a cycle.
     * This happens when targetParentId is a descendant of folderId.
     * We traverse up from targetParentId to root, and if we encounter folderId, it would create a cycle.
     */
    private suspend fun wouldCreateCycle(
        folderId: String,
        targetParentId: String,
        workspaceId: String,
        writeopiaDb: WriteopiaDbBackend
    ): Boolean {
        var currentId = targetParentId

        while (currentId != Folder.ROOT_PATH && currentId.isNotEmpty()) {
            if (currentId == folderId) {
                // Found the folder we're trying to move in the ancestor chain
                // Moving it here would create a cycle
                return true
            }

            val folder = writeopiaDb.getFolderById(currentId, workspaceId)
                ?: break // Folder not found, assume no cycle (or it's at root level)

            currentId = folder.parentId
        }

        return false
    }

    private suspend fun sendToAiHub(documents: List<Document>, workspaceId: String) =
        wrWebClient.post("${Urls.AI_HUB}/documents/") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }, workspaceId))
        }.status
            .isSuccess()
}
