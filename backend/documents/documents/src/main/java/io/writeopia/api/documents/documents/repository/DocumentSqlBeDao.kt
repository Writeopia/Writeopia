@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.extensions.sortWithOrderBy
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sql.DocumentEntityQueries
import io.writeopia.sql.FolderEntityQueries
import io.writeopia.sql.Folder_entity
import io.writeopia.sql.StoryStepEntityQueries
import io.writeopia.sql.UserFavoriteEntityQueries
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
            insertStoryStep(storyStep, i.toLong(), document.id)
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

    private fun insertStoryStep(storyStep: StoryStep, position: Long, documentId: String) {
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
                position = position.toInt(),
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

    fun loadDocumentWithContentByIds(id: List<String>): List<Document> =
        documentQueries?.selectWithContentByIds(id)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked ?: false,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            } ?: emptyList()

    fun loadDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> {
        return documentQueries?.selectWithContentByUserId(userId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            }
            ?.sortWithOrderBy(OrderBy.fromString(orderBy))
            ?: emptyList()
    }

    fun loadFavDocumentsWithContentByUserId(
        orderBy: String,
        userId: String
    ): List<Document> {
        return documentQueries?.selectFavoritesWithContentByUserId(userId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            }
            ?.sortWithOrderBy(OrderBy.fromString(orderBy))
            ?: emptyList()
    }

    fun loadDocumentsWithContentByUserIdAfterTime(
        userId: String,
        time: Long
    ): List<Document> {
        return documentQueries?.selectWithContentByUserIdAfterTime(userId, time)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            } ?: emptyList()
    }

    fun loadDocumentsWithContentFolderIdAfterTime(
        folderId: String,
        workspaceId: String,
        time: Long
    ): List<Document> {
        return documentQueries?.selectWithContentByFolderIdAfterTime(folderId, time, workspaceId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            } ?: emptyList()
    }

    fun loadDocumentsWithContentByWorkspaceIdAfterTime(
        workspaceId: String,
        time: Long
    ): List<Document> {
        return documentQueries?.selectWithContentByWorkspaceIdAfterTime(time, workspaceId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            } ?: emptyList()
    }

    fun deleteDocumentById(documentId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        documentQueries?.delete(now, documentId)
        storyStepQueries?.deleteByDocumentId(documentId)
    }

    fun deleteDocumentByIds(ids: Set<String>) {
        documentQueries?.deleteByIds(Clock.System.now().toEpochMilliseconds(), ids)
        storyStepQueries?.deleteByDocumentIds(ids)
    }

    fun loadDocumentWithContentById(documentId: String, workspaceId: String): Document? =
        documentQueries?.selectWithContentById(documentId, workspaceId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color?.toInt(),
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!!.toInt() to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            }
            ?.firstOrNull()

    fun loadDocumentByParentId(parentId: String): List<Document> {
        return documentQueries?.selectWithContentByParentId(parentId)
            ?.executeAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        innerContent.id_?.isNotEmpty() == true
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked,
//                                steps = emptyList(), // Todo: Fix!
                            decoration = Decoration(
                                backgroundColor = innerContent.background_color,
                            ),
                            tags = innerContent.tags
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.mapNotNull(TagInfo.Companion::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            spans = innerContent.spans
                                ?.split(",")
                                ?.filter { it.isNotEmpty() }
                                ?.map(SpanInfo::fromString)
                                ?.toSet()
                                ?: emptySet(),
                            documentLink = innerContent.link_to_document?.let { documentId ->
                                val title = documentQueries.selectTitleByDocumentId(documentId)
                                    .executeAsOneOrNull()

                                DocumentLink(documentId, title)
                            }
                        )

                        innerContent.position!! to storyStep
                    }

                    Document(
                        id = documentId,
                        title = document.title,
                        content = innerContent,
                        createdAt = Instant.fromEpochMilliseconds(document.created_at),
                        lastUpdatedAt = Instant.fromEpochMilliseconds(document.last_updated_at),
                        lastSyncedAt = Instant.fromEpochMilliseconds(document.last_synced),
                        workspaceId = document.workspace_id,
                        favorite = document.favorite,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint
                            )
                        },
                        isLocked = document.is_locked
                    )
                }
            } ?: emptyList()
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
