package io.writeopia.api.documents.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.persistence.core.extensions.sortWithOrderBy
import io.writeopia.sdk.persistence.sqldelight.toLong
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sql.DocumentEntityQueries
import io.writeopia.sql.StoryStepEntityQueries
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class DocumentSqlBeDao(
    private val documentQueries: DocumentEntityQueries?,
    private val storyStepQueries: StoryStepEntityQueries?,
) : DocumentSearch {

    override suspend fun search(query: String): List<Document> = documentQueries?.query(query)
        ?.executeAsList()
        ?.map { entity ->
            Document(
                id = entity.id,
                title = entity.title,
                createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                userId = entity.user_id,
                favorite = entity.favorite == 1L,
                parentId = entity.parent_document_id,
                icon = entity.icon?.let { MenuItem.Icon(it, entity.icon_tint?.toInt()) },
                isLocked = entity.is_locked == 1L
            )
        }
        ?: emptyList()

    override suspend fun getLastUpdatedAt(): List<Document> = documentQueries?.selectLastUpdatedAt()
        ?.executeAsList()
        ?.map { entity ->
            Document(
                id = entity.id,
                title = entity.title,
                createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                userId = entity.user_id,
                favorite = entity.favorite == 1L,
                parentId = entity.parent_document_id,
                icon = entity.icon?.let { MenuItem.Icon(it, entity.icon_tint?.toInt()) },
                isLocked = entity.is_locked == 1L
            )
        }
        ?: emptyList()

    suspend fun insertDocumentWithContent(document: Document) {
        storyStepQueries?.deleteByDocumentId(document.id)
        document.content.values.forEachIndexed { i, storyStep ->
            insertStoryStep(storyStep, i.toLong(), document.id)
        }

        insertDocument(document)
    }

    suspend fun insertDocument(document: Document) {
        documentQueries?.insert(
            id = document.id,
            title = document.title,
            created_at = document.createdAt.toEpochMilliseconds(),
            last_updated_at = document.lastUpdatedAt.toEpochMilliseconds(),
            user_id = document.userId,
            favorite = document.favorite.toLong(),
            parent_document_id = document.parentId,
            icon = document.icon?.label,
            icon_tint = document.icon?.tint?.toLong(),
            is_locked = document.isLocked.toLong()
        )
    }

    suspend fun insertStoryStep(storyStep: StoryStep, position: Long, documentId: String) {
        storyStep.run {
            storyStepQueries?.insert(
                id = id,
                local_id = localId,
                type = type.number.toLong(),
                parent_id = parentId,
                url = url,
                path = path,
                text = text,
                checked = checked.toLong(),
                position = position,
                document_id = documentId,
                is_group = isGroup.toLong(),
                has_inner_steps = steps.isNotEmpty().toLong(),
                background_color = decoration.backgroundColor?.toLong(),
                tags = tags.joinToString(separator = ",") { it.tag.label },
                spans = spans.joinToString(separator = ",") { it.toText() },
                link_to_document = documentLink?.id
            )
        }
    }

    suspend fun insertDocuments(vararg documents: Document) {
        documents.forEach { document ->
            insertDocumentWithContent(document)
        }
    }

    suspend fun loadDocumentById(id: String): Document? =
        documentQueries?.selectById(id)
            ?.executeAsOneOrNull()
            ?.let { entity ->
                Document(
                    id = entity.id,
                    title = entity.title,
                    content = emptyMap(),
                    createdAt = Instant.fromEpochMilliseconds(entity.created_at),
                    lastUpdatedAt = Instant.fromEpochMilliseconds(entity.last_updated_at),
                    userId = entity.user_id,
                    favorite = entity.favorite == 1L,
                    parentId = entity.parent_document_id,
                    icon = entity.icon?.let {
                        MenuItem.Icon(
                            it,
                            entity.icon_tint?.toInt()
                        )
                    },
                    isLocked = entity.is_locked == 1L
                )
            }

    suspend fun loadDocumentWithContentByIds(id: List<String>): List<Document> =
        documentQueries?.selectWithContentByIds(id)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            } ?: emptyList()

    suspend fun loadDocumentsWithContentByUserId(orderBy: String, userId: String): List<Document> {
        return documentQueries?.selectWithContentByUserId(userId)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            }
            ?.sortWithOrderBy(OrderBy.fromString(orderBy))
            ?: emptyList()
    }

    suspend fun loadFavDocumentsWithContentByUserId(
        orderBy: String,
        userId: String
    ): List<Document> {
        return documentQueries?.selectFavoritesWithContentByUserId(userId)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            }
            ?.sortWithOrderBy(OrderBy.fromString(orderBy))
            ?: emptyList()
    }

    suspend fun loadDocumentsWithContentByUserIdAfterTime(
        userId: String,
        time: Long
    ): List<Document> {
        return documentQueries?.selectWithContentByUserIdAfterTime(userId, time)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            } ?: emptyList()
    }

    suspend fun deleteDocumentById(documentId: String) {
        documentQueries?.delete(documentId)
        storyStepQueries?.deleteByDocumentId(documentId)
    }

    suspend fun deleteDocumentByIds(ids: Set<String>) {
        documentQueries?.deleteByIds(ids)
        storyStepQueries?.deleteByDocumentIds(ids)
    }

    suspend fun loadDocumentWithContentById(documentId: String): Document? =
        documentQueries?.selectWithContentById(documentId)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        !innerContent.id_.isNullOrEmpty()
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            }
            ?.firstOrNull()

    suspend fun loadDocumentByParentId(parentId: String): List<Document> {
        return documentQueries?.selectWithContentByParentId(parentId)
            ?.awaitAsList()
            ?.groupBy { it.id }
            ?.mapNotNull { (documentId, content) ->
                content.firstOrNull()?.let { document ->
                    val innerContent = content.filter { innerContent ->
                        innerContent.id_?.isNotEmpty() == true
                    }.associate { innerContent ->
                        val storyStep = StoryStep(
                            id = innerContent.id_!!,
                            localId = innerContent.local_id!!,
                            type = StoryTypes.fromNumber(innerContent.type!!.toInt()).type,
                            parentId = innerContent.parent_id,
                            url = innerContent.url,
                            path = innerContent.path,
                            text = innerContent.text,
                            checked = innerContent.checked == 1L,
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
                        userId = document.user_id,
                        favorite = document.favorite == 1L,
                        parentId = document.parent_document_id,
                        icon = document.icon?.let {
                            MenuItem.Icon(
                                it,
                                document.icon_tint?.toInt()
                            )
                        },
                        isLocked = document.is_locked == 1L
                    )
                }
            } ?: emptyList()
    }

    suspend fun loadDocumentIdsByParentId(parentId: String): List<String> =
        documentQueries?.selectIdsByParentId(parentId)
            ?.awaitAsList()
            ?: emptyList()

    suspend fun deleteDocumentsByUserId(userId: String) {
        documentQueries?.deleteByUserId(userId)
    }

    suspend fun deleteDocumentsByFolderId(folderId: String) {
        documentQueries?.deleteByFolderId(folderId)
    }

    suspend fun favoriteById(documentId: String) {
        documentQueries?.favoriteById(documentId)
    }

    suspend fun unFavoriteById(documentId: String) {
        documentQueries?.unFavoriteById(documentId)
    }

    suspend fun moveToFolder(documentId: String, parentId: String) {
        documentQueries?.moveToFolder(
            parentId,
            Clock.System.now().toEpochMilliseconds(),
            documentId
        )
    }
}
