@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.repository

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class InMemoryDocumentRepository : DocumentRepository {

    private val documentsMap: MutableMap<String, Document> = mutableMapOf()
    private val _documentsMapState = MutableStateFlow(documentsMap)
    private val documentsMapState = _documentsMapState.map { map ->
        map.mapValues { (_, document) ->
            listOf(document)
        }
    }

    override suspend fun loadDocumentsWorkspace(
        workspaceId: String
    ): List<Document> =
        documentsMap.values.toList()

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentsMap.values.filter { it.parentId == folderId }

    override suspend fun loadFavDocumentsForWorkspace(
        orderBy: String,
        userId: String
    ): List<Document> =
        documentsMap.values.filter { document -> document.favorite }

    override suspend fun loadDocumentsForWorkspace(
        orderBy: String,
        userId: String,
        instant: Instant
    ): List<Document> = documentsMap.values.toList()

    override suspend fun loadDocumentById(id: String, workspaceId: String): Document? =
        documentsMap[id]

    override suspend fun loadDocumentByIds(ids: List<String>, workspaceId: String): List<Document> =
        ids.mapNotNull { id ->
            documentsMap[id]
        }

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> = documentsMapState

    override suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo> =
        documentsMapState.map {  documentsMap ->
            documentsMap.values.flatten().find { document ->
                document.id == id
            }?.info() ?: DocumentInfo.empty()
        }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> {
        val idSet = ids.toSet()

        return documentsMap.filter { (key, _) -> idSet.contains(key) }.values.toList()
    }

    override suspend fun saveDocument(document: Document) {
        documentsMap[document.id] = document
        refreshState()
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentsMap[document.id]?.let { currentDocument ->
            documentsMap[document.id] = currentDocument.copy(title = document.title)
            refreshState()
        }
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        documentsMap[documentId]?.let { document ->
            val newContent = document.content + (position to storyStep)
            documentsMap[documentId] = document.copy(content = newContent)
            refreshState()
        }
    }

    override suspend fun updateStoryStepUrl(url: String, id: String) {
    }

    override suspend fun deleteDocument(document: Document) {
        documentsMap.remove(document.id)
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        ids.forEach(documentsMap::remove)
    }

    override suspend fun deleteByWorkspace(userId: String) {
        documentsMap.clear()
    }

    override suspend fun moveDocumentsToWorkspace(oldUserId: String, newUserId: String) {
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
    }

    override suspend fun search(query: String, workspaceId: String): List<Document> =
        documentsMap.values.filter { it.title.contains(query) }

    override suspend fun getLastUpdatedAt(userId: String): List<Document> =
        documentsMap.values.sortedByDescending { it.lastUpdatedAt }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        setFavorite(ids, true)
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        setFavorite(ids, false)
    }

    private fun setFavorite(ids: Set<String>, isFavorite: Boolean) {
        ids.forEach { id ->
            documentsMap[id]?.copy(favorite = isFavorite)?.let { document ->
                documentsMap[id] = document
            }
        }
        refreshState()
    }

    override suspend fun deleteDocumentByFolder(folderId: String) {
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
    }

    override suspend fun refreshDocuments() {
        refreshState()
    }

    private fun refreshState() {
        _documentsMapState.value = documentsMap.toMutableMap()
    }

    /**
     * Load all documents from the in-memory store.
     * Used by BackendSyncDocumentRepository for syncing.
     */
    fun getAllDocuments(): List<Document> = documentsMap.values.toList()

    /**
     * Clear all documents from the in-memory store.
     */
    fun clearAll() {
        documentsMap.clear()
        refreshState()
    }

    /**
     * Load multiple documents into memory (e.g., from backend).
     */
    suspend fun loadDocuments(documents: List<Document>) {
        documents.forEach { document ->
            documentsMap[document.id] = document
        }
        refreshState()
    }

    override suspend fun queryUnsyncedImagesSteps(): List<StoryStep> = emptyList()

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
    }

    override suspend fun loadOutdatedDocumentsByFolder(folderId: String, workspaceId: String): List<Document> = emptyList()

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        emptyList()

    override suspend fun loadDocumentsByParentId(parentId: String, workspaceId: String): List<Document> =
        documentsMap.values.filter { it.parentId == parentId }
}
