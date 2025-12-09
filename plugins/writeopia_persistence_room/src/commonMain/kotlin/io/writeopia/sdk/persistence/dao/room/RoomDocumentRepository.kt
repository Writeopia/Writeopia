package io.writeopia.sdk.persistence.dao.room

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity
import io.writeopia.sdk.persistence.parse.toEntity
import io.writeopia.sdk.persistence.parse.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.map

class RoomDocumentRepository(
    private val documentEntityDao: DocumentEntityDao,
    private val storyUnitEntityDao: StoryUnitEntityDao? = null
) : DocumentRepository, DocumentSearch {

    private val documentsState: MutableStateFlow<Map<String, List<Document>>> =
        MutableStateFlow(emptyMap())

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentsByParentId(folderId).map { it.toModel() }

    override suspend fun loadFavDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        emptyList()

    override suspend fun deleteDocumentByFolder(folderId: String) {
    }

    override suspend fun search(query: String, workspaceId: String): List<Document> =
        documentEntityDao.search(query).map { it.toModel() }

    override suspend fun getLastUpdatedAt(workspaceId: String): List<Document> =
        documentEntityDao.selectByLastUpdated().map { it.toModel() }

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> =
        documentEntityDao.listenForDocumentsWithContentByParentId(parentId)
            .map { resultsMap ->
                resultsMap.map { (documentEntity, storyEntity) ->
                    val content = loadInnerSteps(storyEntity)
                    documentEntity.toModel(content)
                }.groupBy { it.parentId }
            }

    override suspend fun listenForDocumentInfoById(id: String): Flow<DocumentInfo?> =
        documentEntityDao.listenForDocumentById(id).map { entity ->
            entity?.toModel()?.info()
        }

    override suspend fun loadDocumentsWorkspace(workspaceId: String): List<Document> =
        documentEntityDao.loadDocumentsWithContentForUser(workspaceId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content)
            }

    override suspend fun loadDocumentsForWorkspace(
        orderBy: String,
        userId: String,
        instant: Instant
    ): List<Document> = throw IllegalStateException("This method is not supported")

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {
        setFavorite(ids, "", true)
    }

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {
        setFavorite(ids, "", false)
    }

    override suspend fun loadDocumentById(
        id: String,
        workspaceId: String
    ): Document? =
        documentEntityDao.loadDocumentById(id)?.let { documentEntity ->
            val content = loadInnerSteps(
                storyUnitEntityDao?.loadDocumentContent(documentEntity.id) ?: emptyList()
            )
            documentEntity.toModel(content)
        }

    override suspend fun loadDocumentByIds(
        ids: List<String>,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentByIds(ids).map { documentEntity ->
            val content = loadInnerSteps(
                storyUnitEntityDao?.loadDocumentContent(documentEntity.id) ?: emptyList()
            )
            documentEntity.toModel(content)
        }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentWithContentByIds(ids, orderBy)
            .entries
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content)
            }

    override suspend fun saveDocument(document: Document) {
        saveDocumentMetadata(document)

        document.content.toEntity(document.id).let { data ->
            storyUnitEntityDao?.deleteDocumentContent(documentId = document.id)
            storyUnitEntityDao?.insertStoryUnits(*data.toTypedArray())
        }
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentEntityDao.insertDocuments(document.toEntity())
    }

    override suspend fun deleteDocument(document: Document) {
        documentEntityDao.deleteDocuments(document.toEntity())
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>) {
        // The user ID is not relevant in the way to delete documents
        documentEntityDao.deleteDocuments(
            *ids.map {
                DocumentEntity.createById(
                    it,
                    "",
                    parentId = ""
                )
            }.toTypedArray()
        )
    }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        println("saving story steps: ${storyStep.spans.joinToString { it.toText() }}")
        storyUnitEntityDao?.insertStoryUnits(storyStep.toEntity(position, documentId))
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Int, documentId: String) {
        storyUnitEntityDao?.updateStoryStep(storyStep.toEntity(position, documentId))
    }

    override suspend fun deleteByWorkspace(userId: String) {
        documentEntityDao.deleteDocumentsByUserId(userId)
    }

    override suspend fun moveDocumentsToWorkspace(oldUserId: String, newUserId: String) {
        documentEntityDao.moveDocumentsToNewUser(oldUserId, newUserId)
    }

    override suspend fun moveToFolder(documentId: String, parentId: String) {
        documentEntityDao.loadDocumentById(id = documentId)?.let { documentEntity ->
            val updated = documentEntity.copy(parentId = parentId)
            documentEntityDao.updateDocument(updated)
        }
    }

    override suspend fun loadDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentsByParentId(parentId).map { it.toModel() }

    /**
     * This method removes the story units that are not in the root level (they don't have parents)
     * and loads the inner steps of the steps that have children.
     */
    private suspend fun loadInnerSteps(storyEntities: List<StoryStepEntity>): Map<Int, StoryStep> =
        storyEntities.filter { entity -> entity.parentId == null }
            .associateBy { entity -> entity.position }
            .mapValues { (_, entity) ->
                if (entity.linkToDocument != null) {
                    val title = documentEntityDao.getDocumentTitleById(entity.linkToDocument)
                    return@mapValues entity.toModel(
                        documentLink = DocumentLink(
                            entity.linkToDocument,
                            title
                        )
                    )
                }

                if (entity.hasInnerSteps) {
                    val innerSteps = storyUnitEntityDao?.queryInnerSteps(entity.id) ?: emptyList()
                    return@mapValues entity.toModel(innerSteps)
                }

                entity.toModel()
            }

    private suspend fun setFavorite(ids: Set<String>, workspaceId: String, isFavorite: Boolean) {
        ids.mapNotNull { id ->
            loadDocumentById(id, workspaceId)
        }.forEach { document ->
            documentEntityDao.updateDocument(document.copy(favorite = isFavorite).toEntity())
        }
    }

    override suspend fun refreshDocuments() {
    }

    override suspend fun stopListeningForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ) {
    }

    override suspend fun loadOutdatedDocumentsByFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadOutdatedDocumentsByFolderId(folderId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content)
            }

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        documentEntityDao.loadOutdatedDocumentsWithContentForWorkspace(workspaceId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content)
            }
}
