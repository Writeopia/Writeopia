@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.dao.room

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.CommentEntityDao
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.entity.comment.CommentEntity
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity
import io.writeopia.sdk.persistence.parse.toCommentConversations
import io.writeopia.sdk.persistence.parse.toCommentEntities
import io.writeopia.sdk.persistence.parse.toEntity
import io.writeopia.sdk.persistence.parse.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.map
import kotlin.time.ExperimentalTime

private object LegacyCommentEntityDao : CommentEntityDao {
    override suspend fun insertComments(vararg comments: CommentEntity) = Unit

    override suspend fun loadByDocumentId(documentId: String): List<CommentEntity> = emptyList()

    override suspend fun loadByDocumentIds(documentIds: List<String>): List<CommentEntity> = emptyList()

    override suspend fun deleteByDocumentId(documentId: String) = Unit

    override suspend fun deleteByDocumentIds(documentIds: List<String>) = Unit
}

class RoomDocumentRepository(
    private val documentEntityDao: DocumentEntityDao,
    private val storyUnitEntityDao: StoryUnitEntityDao? = null,
    private val commentEntityDao: CommentEntityDao,
) : DocumentRepository, DocumentSearch {

    @Deprecated("Pass CommentEntityDao to preserve comment loading.")
    constructor(
        documentEntityDao: DocumentEntityDao,
        storyUnitEntityDao: StoryUnitEntityDao? = null,
    ) : this(documentEntityDao, storyUnitEntityDao, LegacyCommentEntityDao)

    private val documentsState: MutableStateFlow<Map<String, List<Document>>> =
        MutableStateFlow(emptyMap())

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> {
        val documents = documentEntityDao.loadDocumentsByParentIdForWorkspace(folderId, workspaceId)
        val commentsByDocumentId = loadCommentConversationsByDocumentIds(documents.map { it.id })

        return documents.map { document ->
            document.toModel(
                commentConversations = commentsByDocumentId[document.id].orEmpty(),
            )
        }
    }

    override suspend fun loadFavDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        emptyList()

    override suspend fun deleteDocumentByFolder(folderId: String, workspaceId: String) {
        val documentsToDelete = documentEntityDao.loadDocumentsByParentId(folderId)
            .filter { it.workspaceId == workspaceId }
            .map { doc ->
                doc.copy(
                    lastUpdatedAt = Clock.System.now().toEpochMilliseconds(),
                    isDeleted = true
                )
            }
        if (documentsToDelete.isNotEmpty()) {
            documentEntityDao.updateDocument(*documentsToDelete.toTypedArray())
        }
    }

    override suspend fun search(query: String, workspaceId: String): List<Document> =
        documentEntityDao.search(query).map { it.toModel() }

    override suspend fun getLastUpdatedAt(workspaceId: String): List<Document> =
        documentEntityDao.selectByLastUpdated().map { it.toModel() }

    override suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> =
        documentEntityDao.listenForDocumentsWithContentByParentIdForWorkspace(
            parentId,
            workspaceId,
        ).map { resultsMap ->
            resultsMap.map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id))
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
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id))
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
        documentEntityDao.loadDocumentByIdForWorkspace(id, workspaceId)?.let { documentEntity ->
            val content = loadInnerSteps(
                storyUnitEntityDao?.loadDocumentContent(documentEntity.id) ?: emptyList()
            )
            documentEntity.toModel(content, loadCommentConversations(documentEntity.id))
        }

    override suspend fun loadDocumentByIds(
        ids: List<String>,
        workspaceId: String
    ): List<Document> {
        val documents = documentEntityDao.loadDocumentByIdsForWorkspace(ids, workspaceId)
        val commentsByDocumentId =
            loadCommentConversationsByDocumentIds(documents.map { it.id })

        return documents.map { documentEntity ->
            val content = loadInnerSteps(
                storyUnitEntityDao?.loadDocumentContent(documentEntity.id) ?: emptyList()
            )
            documentEntity.toModel(
                content,
                commentsByDocumentId[documentEntity.id].orEmpty(),
            )
        }
    }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> {
        val documents =
            documentEntityDao.loadDocumentWithContentByIdsForWorkspace(ids, orderBy, workspaceId)
                .entries
        val commentsByDocumentId =
            loadCommentConversationsByDocumentIds(documents.map { it.key.id })

        return documents.map { (documentEntity, storyEntity) ->
            val content = loadInnerSteps(storyEntity)
            documentEntity.toModel(
                content,
                commentsByDocumentId[documentEntity.id].orEmpty(),
            )
        }
    }

    override suspend fun saveDocument(document: Document) {
        val storySteps = document.content.toEntity(document.id)
        val comments = document.commentConversations.toCommentEntities(document.id)

        documentEntityDao.saveDocumentWithContent(
            document = document.toEntity(),
            storySteps = storySteps,
            comments = comments,
        )
    }

    override suspend fun saveDocumentMetadata(document: Document) {
        documentEntityDao.insertDocuments(document.toEntity())
    }

    override suspend fun deleteDocument(document: Document, workspaceId: String) {
        documentEntityDao.updateDocument(
            document.toEntity()
                .copy(
                    lastUpdatedAt = Clock.System.now().toEpochMilliseconds(),
                    isDeleted = true
                )
        )
    }

    override suspend fun deleteDocumentByIds(ids: Set<String>, workspaceId: String) {
        documentEntityDao.updateDocument(
            *ids.mapNotNull {
                documentEntityDao.loadDocumentById(it)
                    ?.takeIf { doc -> doc.workspaceId == workspaceId }
                    ?.copy(
                        lastUpdatedAt = Clock.System.now().toEpochMilliseconds(),
                        isDeleted = true
                    )
            }.toTypedArray()
        )
    }

    override suspend fun hardDeleteDocumentByIds(ids: Set<String>, workspaceId: String) {
        documentEntityDao.hardDeleteDocumentsWithContentByIds(ids.toList(), workspaceId)
    }

    override suspend fun getSoftDeletedDocuments(workspaceId: String): List<Document> =
        documentEntityDao.getSoftDeletedByWorkspace(workspaceId).map { it.toModel() }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Double, documentId: String) {
        val dbPos = storyStep.dbPosition ?: position
        storyUnitEntityDao?.insertStoryUnits(storyStep.toEntity(dbPos, documentId))
    }

    override suspend fun saveStorySteps(steps: List<Pair<Double, StoryStep>>, documentId: String) {
        steps.forEach { (position, storyStep) ->
            storyUnitEntityDao?.insertStoryUnits(storyStep.toEntity(position, documentId))
        }
    }

    override suspend fun deleteStoryStep(storyStepId: String, documentId: String) {
        storyUnitEntityDao?.deleteById(storyStepId)
    }

    override suspend fun updateStoryStepUrl(url: String, id: String) {
        storyUnitEntityDao?.updateUrl(url, id)
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Double, documentId: String) {
        val dbPos = storyStep.dbPosition ?: position
        storyUnitEntityDao?.updateStoryStep(storyStep.toEntity(dbPos, documentId))
    }

    override suspend fun deleteByWorkspace(userId: String) {
        documentEntityDao.purgeDocumentsWithContentByWorkspace(userId)
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
    ): List<Document> {
        val documents = documentEntityDao.loadDocumentsByParentIdForWorkspace(parentId, workspaceId)
        val commentsByDocumentId = loadCommentConversationsByDocumentIds(documents.map { it.id })

        return documents.map { document ->
            document.toModel(
                commentConversations = commentsByDocumentId[document.id].orEmpty(),
            )
        }
    }

    /**
     * This method removes the story units that are not in the root level (they don't have parents)
     * and loads the inner steps of the steps that have children.
     */
    private suspend fun loadInnerSteps(storyEntities: List<StoryStepEntity>): Map<Double, StoryStep> =
        storyEntities.filter { entity -> entity.parentId == null }
            .sortedBy { it.position }
            .associate { entity -> entity.position to entity }
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

    private suspend fun loadCommentConversations(documentId: String) =
        commentEntityDao.loadByDocumentId(documentId).toCommentConversations()

    private suspend fun loadCommentConversationsByDocumentIds(documentIds: List<String>) =
        if (documentIds.isEmpty()) {
            emptyMap()
        } else {
            commentEntityDao.loadByDocumentIds(documentIds)
                .groupBy { entity -> entity.documentId }
                .mapValues { (_, entities) -> entities.toCommentConversations() }
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

    override suspend fun queryUnsyncedImagesSteps(): List<StoryStep> =
        storyUnitEntityDao?.getUnsyncedSteps()
            ?.map { step -> step.toModel() }
            ?: emptyList()

    override suspend fun stopListeningForFoldersByParentId(
        parentId: String,
        workspaceId: String
    ) {
    }

    override suspend fun loadOutdatedDocumentsByFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadOutdatedDocumentsByFolderId(folderId, workspaceId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id))
            }

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        documentEntityDao.loadOutdatedDocumentsWithContentForWorkspace(workspaceId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id))
            }
}
