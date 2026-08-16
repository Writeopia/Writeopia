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

    // Holds documents grouped by parentId for reactive updates
    private val documentsGroupedByParentIdFlow = MutableStateFlow<Map<String, List<Document>>>(emptyMap())

    override suspend fun loadDocumentsWorkspace(
        workspaceId: String
    ): List<Document> =
        documentsMap.values.filter { it.workspaceId == workspaceId }

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentsMap.values.filter { it.parentId == folderId && it.workspaceId == workspaceId }

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
    ): Flow<Map<String, List<Document>>> {
        refreshDocuments()
        return documentsGroupedByParentIdFlow
    }

    override suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo> =
        documentsGroupedByParentIdFlow.map { groupedDocs ->
            groupedDocs.values.flatten().find { document ->
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
        refreshDocuments()
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentsMap[document.id]?.let { currentDocument ->
            documentsMap[document.id] = currentDocument.copy(title = document.title)
            refreshDocuments()
        }
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Double, documentId: String) {
        documentsMap[documentId]?.let { document ->
            val newContent = document.content + (position to storyStep)
            documentsMap[documentId] = document.copy(content = newContent)
            refreshDocuments()
        }
    }

    override suspend fun saveStorySteps(steps: List<Pair<Double, StoryStep>>, documentId: String) {
        documentsMap[documentId]?.let { document ->
            val newContent = document.content.toMutableMap()
            steps.forEach { (position, storyStep) ->
                newContent[position] = storyStep
            }
            documentsMap[documentId] = document.copy(content = newContent)
            refreshDocuments()
        }
    }

    override suspend fun deleteStoryStep(storyStepId: String, documentId: String) {
        documentsMap[documentId]?.let { document ->
            val newContent = document.content.filterNot { (_, step) -> step.id == storyStepId }
                .values.mapIndexed { index, step -> index.toDouble() to step }.toMap()
            documentsMap[documentId] = document.copy(content = newContent)
            refreshDocuments()
        }
    }

    override suspend fun updateStoryStepUrl(url: String, id: String) {
    }

    override suspend fun deleteDocument(document: Document) {
        documentsMap.remove(document.id)
        refreshDocuments()
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        ids.forEach(documentsMap::remove)
        refreshDocuments()
    }

    override suspend fun deleteByWorkspace(userId: String) {
        documentsMap.clear()
        refreshDocuments()
    }

    override suspend fun moveDocumentsToWorkspace(oldUserId: String, newUserId: String) {
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Double, documentId: String) {
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

    private suspend fun setFavorite(ids: Set<String>, isFavorite: Boolean) {
        ids.forEach { id ->
            documentsMap[id]?.copy(favorite = isFavorite)?.let { document ->
                documentsMap[id] = document
            }
        }
        refreshDocuments()
    }

    override suspend fun deleteDocumentByFolder(folderId: String) {
        val keysToRemove = documentsMap.filter { (_, doc) -> doc.parentId == folderId }.keys
        keysToRemove.forEach { documentsMap.remove(it) }
        refreshDocuments()
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        documentsMap[documentId]?.let { document ->
            documentsMap[documentId] = document.copy(parentId = parentId)
            refreshDocuments()
        }
    }

    override suspend fun refreshDocuments() {
        documentsGroupedByParentIdFlow.value = documentsMap.values.groupBy { it.parentId }
    }

    override suspend fun queryUnsyncedImagesSteps(): List<StoryStep> = emptyList()

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {
    }

    override suspend fun loadOutdatedDocumentsByFolder(folderId: String, workspaceId: String): List<Document> = emptyList()

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        emptyList()

    override suspend fun loadDocumentsByParentId(parentId: String, workspaceId: String): List<Document> =
        documentsMap.values.filter { it.parentId == parentId && it.workspaceId == workspaceId }
}
