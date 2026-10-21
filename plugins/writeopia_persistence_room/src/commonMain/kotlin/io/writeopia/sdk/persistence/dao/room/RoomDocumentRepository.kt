@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.dao.room

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.CommentEntityDao
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.DocumentTagLinkDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.models.story.StoryStepTombstone
import io.writeopia.sdk.persistence.entity.story.StoryStepTombstoneEntity
import io.writeopia.sdk.persistence.dao.StoryStepTombstoneDao
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity
import io.writeopia.sdk.persistence.entity.tag.DocumentTagLinkEntity
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

class RoomDocumentRepository(
    private val documentEntityDao: DocumentEntityDao,
    private val storyUnitEntityDao: StoryUnitEntityDao? = null,
    private val commentEntityDao: CommentEntityDao,
    private val database: RoomDatabase,
    private val documentTagLinkDao: DocumentTagLinkDao? = null,
    private val storyStepTombstoneDao: StoryStepTombstoneDao? = null,
) : DocumentRepository, DocumentSearch {

    private val documentsState: MutableStateFlow<Map<String, List<Document>>> =
        MutableStateFlow(emptyMap())

    override suspend fun loadDocumentsForFolder(
        folderId: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentsByParentIdForWorkspace(folderId, workspaceId)
            .map { documentEntity ->
                documentEntity.toModel(
                    commentConversations = loadCommentConversations(documentEntity.id),
                    tagIds = loadTagIds(documentEntity.id),
                )
            }

    override suspend fun loadFavDocumentsForWorkspace(
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadFavoriteDocumentsWithContentForWorkspace(workspaceId)
            .map { (documentEntity, storyEntity) -> documentEntity.toListedModel(storyEntity) }

    override suspend fun listenForFavoriteDocuments(workspaceId: String): Flow<List<Document>> =
        documentEntityDao.listenForFavoriteDocumentsWithContentForWorkspace(workspaceId)
            .map { resultsMap ->
                resultsMap.map { (documentEntity, storyEntity) ->
                    documentEntity.toListedModel(storyEntity)
                }
            }

    private suspend fun DocumentEntity.toListedModel(storyEntity: List<StoryStepEntity>): Document =
        toModel(loadInnerSteps(storyEntity), loadCommentConversations(id), loadTagIds(id))

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
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
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
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
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
            documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
        }

    override suspend fun loadDocumentByIds(
        ids: List<String>,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentByIdsForWorkspace(ids, workspaceId).map { documentEntity ->
            val content = loadInnerSteps(
                storyUnitEntityDao?.loadDocumentContent(documentEntity.id) ?: emptyList()
            )
            documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
        }

    override suspend fun loadDocumentsWithContentByIds(
        ids: List<String>,
        orderBy: String,
        workspaceId: String
    ): List<Document> =
        documentEntityDao.loadDocumentWithContentByIdsForWorkspace(ids, orderBy, workspaceId)
            .entries
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
            }

    override suspend fun saveDocument(document: Document) {
        writeTransaction {
            val existing = documentEntityDao.loadDocumentById(document.id)
            require(existing == null || existing.workspaceId == document.workspaceId) {
                "Document does not belong to the requested workspace"
            }

            saveDocumentMetadata(document)

            document.content.toEntity(document.id).let { data ->
                storyUnitEntityDao?.deleteDocumentContent(documentId = document.id)
                insertStepsKeepingTombstones(data)
            }

            val comments = document.commentConversations.toCommentEntities(document.id)
            commentEntityDao.deleteByDocumentId(document.id)
            commentEntityDao.insertComments(*comments.toTypedArray())

            documentTagLinkDao?.deleteByDocumentId(document.id)
            val links = document.tagIds.map { tagId -> DocumentTagLinkEntity(document.id, tagId) }
            if (links.isNotEmpty()) documentTagLinkDao?.insertLinks(*links.toTypedArray())
        }
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
        writeTransaction {
            val ownedIds = documentEntityDao.loadDocumentByIdsForWorkspace(
                ids.toList(),
                workspaceId,
            ).map { document -> document.id }

            commentEntityDao.deleteByDocumentIds(ownedIds)
            documentTagLinkDao?.deleteByDocumentIds(ownedIds)
            storyUnitEntityDao?.deleteByDocumentIds(ownedIds)
            documentEntityDao.hardDeleteDocumentByIds(ownedIds, workspaceId)
        }
    }

    override suspend fun getSoftDeletedDocuments(workspaceId: String): List<Document> =
        documentEntityDao.getSoftDeletedByWorkspace(workspaceId).map { it.toModel() }

    override suspend fun saveStoryStep(storyStep: StoryStep, position: Long, documentId: String) {
        insertStepsKeepingTombstones(listOf(storyStep.toEntity(position, documentId)))
    }

    override suspend fun saveStorySteps(steps: List<Pair<Long, StoryStep>>, documentId: String) {
        insertStepsKeepingTombstones(
            steps.map { (position, storyStep) -> storyStep.toEntity(position, documentId) }
        )
    }

    /**
     * Writes the steps, except those deleted here (they have a tombstone the backend doesn't
     * know of yet): a deleted step is never written back, whatever is saving it.
     */
    private suspend fun insertStepsKeepingTombstones(steps: List<StoryStepEntity>) {
        if (steps.isEmpty()) return
        val tombstoneDao = storyStepTombstoneDao
        val tombstoned = if (tombstoneDao == null) {
            emptySet()
        } else {
            // In batches: SQLite binds one variable per id, and older builds allow 999.
            steps.map { step -> step.id }.distinct().chunked(SQLITE_IDS_PER_QUERY)
                .flatMap { batch -> tombstoneDao.stepIdsIn(batch) }
                .toSet()
        }
        val toWrite = steps.filter { step -> step.id !in tombstoned }
        if (toWrite.isNotEmpty()) storyUnitEntityDao?.insertStoryUnits(*toWrite.toTypedArray())
    }

    /**
     * Deletes a step the user removed. A tombstone is kept until the backend is told, so the
     * step is never recreated by a sync that still carries it.
     */
    override suspend fun deleteStoryStep(storyStepId: String, documentId: String) {
        writeTransaction {
            documentEntityDao.loadDocumentById(documentId)?.let { document ->
                storyStepTombstoneDao?.insert(
                    StoryStepTombstoneEntity(
                        stepId = storyStepId,
                        documentId = documentId,
                        workspaceId = document.workspaceId,
                        deletedAt = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
            storyUnitEntityDao?.deleteById(storyStepId)
        }
    }

    override suspend fun loadStoryStepTombstones(workspaceId: String): List<StoryStepTombstone> =
        storyStepTombstoneDao?.loadByWorkspaceId(workspaceId)
            ?.map { entity ->
                StoryStepTombstone(
                    stepId = entity.stepId,
                    documentId = entity.documentId,
                    workspaceId = entity.workspaceId,
                    deletedAt = entity.deletedAt
                )
            }
            ?: emptyList()

    override suspend fun deleteStoryStepTombstones(stepIds: Collection<String>) {
        stepIds.distinct().chunked(SQLITE_IDS_PER_QUERY).forEach { batch ->
            storyStepTombstoneDao?.deleteByStepIds(batch)
        }
    }

    override suspend fun deleteStoryStepsByIds(stepIds: Collection<String>, workspaceId: String) {
        stepIds.distinct().chunked(SQLITE_IDS_PER_QUERY).forEach { batch ->
            storyUnitEntityDao?.deleteByIdsForWorkspace(batch, workspaceId)
        }
    }

    override suspend fun updateStoryStepUrl(url: String, id: String) {
        storyUnitEntityDao?.updateUrl(url, id)
    }

    override suspend fun updateStoryStep(storyStep: StoryStep, position: Long, documentId: String) {
        storyUnitEntityDao?.updateStoryStep(storyStep.toEntity(position, documentId))
    }

    override suspend fun deleteByWorkspace(userId: String) {
        writeTransaction {
            val documentIds = documentEntityDao.loadDocumentIdsForWorkspace(userId)

            commentEntityDao.deleteByDocumentIds(documentIds)
            documentTagLinkDao?.deleteByDocumentIds(documentIds)
            storyUnitEntityDao?.deleteByDocumentIds(documentIds)
            documentEntityDao.purgeDocumentsByUserId(userId)
        }
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
        documentEntityDao.loadDocumentsByParentIdForWorkspace(parentId, workspaceId)
            .map { documentEntity ->
                documentEntity.toModel(
                    commentConversations = loadCommentConversations(documentEntity.id),
                    tagIds = loadTagIds(documentEntity.id),
                )
            }

    /**
     * This method removes the story units that are not in the root level (they don't have parents)
     * and loads the inner steps of the steps that have children.
     */
    private suspend fun loadInnerSteps(storyEntities: List<StoryStepEntity>): Map<Long, StoryStep> =
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

    private companion object {
        /** Ids per `IN (...)` query: SQLite binds one variable per id, and builds before 3.32 allow 999. */
        const val SQLITE_IDS_PER_QUERY = 500
    }

    private suspend fun <T> writeTransaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }

    private suspend fun loadCommentConversations(
        documentId: String,
    ): Map<String, List<Comment>> =
        commentEntityDao
            .loadByDocumentId(documentId)
            .toCommentConversations()

    private suspend fun loadTagIds(documentId: String): Set<String> =
        documentTagLinkDao?.loadTagIdsByDocumentId(documentId)?.toSet() ?: emptySet()

    private suspend fun setFavorite(ids: Set<String>, workspaceId: String, isFavorite: Boolean) {
        ids.mapNotNull { id ->
            if (workspaceId.isEmpty()) {
                documentEntityDao.loadDocumentById(id)
            } else {
                documentEntityDao.loadDocumentByIdForWorkspace(id, workspaceId)
            }
        }.forEach { documentEntity ->
            documentEntityDao.updateDocument(documentEntity.copy(favorite = isFavorite))
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
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
            }

    override suspend fun loadOutdatedDocumentsForWorkspace(workspaceId: String): List<Document> =
        documentEntityDao.loadOutdatedDocumentsWithContentForWorkspace(workspaceId)
            .map { (documentEntity, storyEntity) ->
                val content = loadInnerSteps(storyEntity)
                documentEntity.toModel(content, loadCommentConversations(documentEntity.id), loadTagIds(documentEntity.id))
            }
}
