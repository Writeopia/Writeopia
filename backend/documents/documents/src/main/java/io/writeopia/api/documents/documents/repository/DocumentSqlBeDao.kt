@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sql.DocumentEntityQueries
import io.writeopia.sql.FolderEntityQueries
import io.writeopia.sql.Folder_entity
import io.writeopia.sql.StoryStepEntityQueries
import io.writeopia.sql.UserFavoriteEntityQueries
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentSqlBeDao(
    private val documentQueries: DocumentEntityQueries?,
    private val storyStepQueries: StoryStepEntityQueries?,
    private val foldersQueries: FolderEntityQueries?,
    private val userFavoriteQueries: UserFavoriteEntityQueries? = null,
) : DocumentSearch {

    override suspend fun search(query: String, workspaceId: String): List<Document> =
        documentQueries?.query(query, workspaceId)
            ?.executeAsList()
            ?.map { entity ->
                Document(
                    id = entity.id,
                    title = entity.title,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = Instant.fromEpochMilliseconds(entity.last_synced),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, entity.icon_tint) },
                    isLocked = entity.is_locked,
                )
            } ?: emptyList()

    override suspend fun getLastUpdatedAt(userId: String): List<Document> =
        documentQueries?.selectLastUpdatedAt()
            ?.executeAsList()
            ?.map { entity ->
                Document(
                    id = entity.id,
                    title = entity.title,
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = Instant.fromEpochMilliseconds(entity.last_synced),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let { MenuItem.Icon(it, entity.icon_tint) },
                    isLocked = entity.is_locked
                )
            } ?: emptyList()

    fun insertDocumentWithContent(document: Document) {
        val result =
            documentQueries?.selectById(document.id, document.workspaceId)?.executeAsOneOrNull()

        if (result != null) {
            storyStepQueries?.deleteByDocumentId(document.id)
        }

        document.content.values.forEachIndexed { i, storyStep ->
            insertStoryStep(storyStep, i.toDouble(), document.id)
        }

        insertDocument(document)
    }

    private fun insertDocument(document: Document) {
        documentQueries?.insert(
            id = document.id,
            title = document.title,
            created_at = document.createdAt.toEpochMilliseconds(),
            last_updated_at = document.lastUpdatedAt.toEpochMilliseconds(),
            last_synced = document.lastUpdatedAt.toEpochMilliseconds(),
            workspace_id = document.workspaceId,
            favorite = document.favorite,
            parent_document_id = document.parentId,
            icon = document.icon?.label,
            icon_tint = document.icon?.tint,
            is_locked = document.isLocked,
            company_id = "",
            deleted = document.deleted
        )
    }

    private fun insertStoryStep(storyStep: io.writeopia.sdk.models.story.StoryStep, position: Double, documentId: String) {
        storyStep.run {
            storyStepQueries?.insert(
                id = id,
                local_id = localId,
                type = type.number,
                parent_id = parentId,
                url = url,
                path = path,
                text = text,
                checked = checked ?: false,
                position = BigDecimal.valueOf(position),
                document_id = documentId,
                is_group = isGroup,
                has_inner_steps = steps.isNotEmpty(),
                background_color = decoration.backgroundColor,
                tags = tags.joinToString(separator = ",") { it.tag.label },
                spans = spans.joinToString(separator = ",") { it.toText() },
                link_to_document = documentLink?.id
            )
        }
    }

    fun insertFolder(folder: Folder) {
        foldersQueries?.insert(
            id = folder.id,
            parent_id = folder.parentId,
            workspace_id = folder.workspaceId,
            title = folder.title,
            created_at = folder.lastUpdatedAt.toEpochMilliseconds().toInt(),
            last_updated_at = folder.lastUpdatedAt.toEpochMilliseconds(),
            last_synced_at = folder.lastSyncedAt?.toEpochMilliseconds(),
            favorite = folder.favorite,
            icon = folder.icon?.label,
            icon_tint = folder.icon?.tint
        )
    }

    fun loadDocumentById(id: String, workspaceId: String): Document? =
        documentQueries?.selectById(id, workspaceId)
            ?.executeAsOneOrNull()
            ?.let { entity ->
                Document(
                    id = entity.id,
                    title = entity.title,
                    content = emptyMap(),
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    lastSyncedAt = Instant.fromEpochMilliseconds(entity.last_synced),
                    workspaceId = entity.workspace_id,
                    favorite = entity.favorite,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let {
                        MenuItem.Icon(
                            it,
                            entity.icon_tint
                        )
                    },
                    isLocked = entity.is_locked
                )
            }

    fun loadFolderById(id: String, workspaceId: String): Folder? =
        foldersQueries?.selectFolderById(id, workspaceId)
            ?.executeAsOneOrNull()
            ?.toModel(0)

    // Content loading methods - kept as no-op for debugging
    fun loadDocumentWithContentByIds(id: List<String>): List<Document> = emptyList()

    fun loadDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> = emptyList()

    fun loadFavDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> = emptyList()

    fun loadDocumentsWithContentByUserIdAfterTime(userId: String, time: Long): List<Document> = emptyList()

    fun loadDocumentsWithContentFolderIdAfterTime(folderId: String, workspaceId: String, time: Long): List<Document> = emptyList()

    fun loadDocumentsWithContentByWorkspaceIdAfterTime(workspaceId: String, time: Long): List<Document> = emptyList()

    fun loadDocumentWithContentById(documentId: String, workspaceId: String): Document? = null

    fun loadDocumentByParentId(parentId: String): List<Document> = emptyList()

    fun loadDocumentWithContentByTitle(): Document? = null

    // Delete and other operations - with real implementation
    fun deleteDocumentById(documentId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        documentQueries?.delete(now, documentId)
        storyStepQueries?.deleteByDocumentId(documentId)
    }

    fun deleteDocumentByIds(ids: Set<String>) {
        documentQueries?.deleteByIds(Clock.System.now().toEpochMilliseconds(), ids)
        storyStepQueries?.deleteByDocumentIds(ids)
    }

    fun loadDocumentIdsByParentId(parentId: String): List<String> =
        documentQueries?.selectIdsByParentId(parentId)
            ?.executeAsList()
            ?: emptyList()

    fun loadAllFoldersByWorkspaceId(workspaceId: String): List<Folder> {
        return foldersQueries?.selectByWorkspace(workspaceId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()
    }

    fun loadFoldersByParentId(parentId: String): List<Folder> {
        return foldersQueries?.selectChildrenFolder(parentId)
            ?.executeAsList()
            ?.map { it.toModel(0) }
            ?: emptyList()
    }

    fun deleteDocumentsByUserId(userId: String) {
        documentQueries?.deleteByUserId(Clock.System.now().toEpochMilliseconds(), userId)
    }

    fun deleteDocumentsByFolderId(folderId: String) {
        documentQueries?.deleteByFolderId(Clock.System.now().toEpochMilliseconds(), folderId)
    }

    fun addUserFavorite(userId: String, documentId: String, workspaceId: String) {
        userFavoriteQueries?.insert(
            userId,
            documentId,
            workspaceId,
            Clock.System.now().toEpochMilliseconds().toInt()
        )
    }

    fun removeUserFavorite(userId: String, documentId: String) {
        userFavoriteQueries?.delete(userId, documentId)
    }

    fun isUserFavorite(userId: String, documentId: String): Boolean {
        return userFavoriteQueries?.isFavorite(userId, documentId)
            ?.executeAsOneOrNull() ?: false
    }

    fun getUserFavoriteDocumentIds(userId: String, workspaceId: String): List<String> {
        return userFavoriteQueries?.selectByUserAndWorkspace(userId, workspaceId)
            ?.executeAsList() ?: emptyList()
    }

    fun moveToFolder(documentId: String, parentId: String) {
        documentQueries?.moveToFolder(
            parentId,
            Clock.System.now().toEpochMilliseconds(),
            documentId
        )
    }

    fun deleteFolder(folderId: String) {
        foldersQueries?.deleteFolder(folderId)
    }

    fun moveFolderToFolder(folderId: String, parentId: String) {
        foldersQueries?.moveToFolder(
            parentId,
            Clock.System.now().toEpochMilliseconds(),
            folderId
        )
    }
}

fun Folder_entity.toModel(count: Long) =
    Folder(
        id = this.id,
        parentId = this.parent_id,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(created_at.toLong()),
        lastUpdatedAt = Instant.fromEpochMilliseconds(last_updated_at ?: 0),
        workspaceId = workspace_id,
        itemCount = count,
        favorite = favorite,
        icon = if (this.icon != null && this.icon_tint != null) MenuItem.Icon(
            this.icon!!,
            this.icon_tint!!
        ) else null,
        lastSyncedAt = if (last_synced_at != null) {
            Instant.fromEpochMilliseconds(last_synced_at!!)
        } else {
            null
        }
    )
