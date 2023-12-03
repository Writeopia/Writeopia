package io.writeopia.sdk.persistence.sqldelight.dao

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.sql.DocumentEntityQueries
import io.writeopia.sdk.sql.StoryStepEntityQueries
import kotlinx.datetime.Instant

class DocumentSqlDao(
    private val documentQueries: DocumentEntityQueries,
    private val storyStepQueries: StoryStepEntityQueries,
) {

    fun insertDocumentWithContent(document: Document) {
        document.content.values.forEachIndexed { i, storyStep ->
            insertStoryStep(storyStep, i.toLong(), document.id)
        }

        insertDocument(document)
    }

    fun insertDocument(document: Document) {
        documentQueries.insert(
            id = document.id,
            title = document.title,
            created_at = document.createdAt.toEpochMilliseconds(),
            last_updated_at = document.lastUpdatedAt.toEpochMilliseconds(),
            user_id = document.userId
        )
    }

    fun insertStoryStep(storyStep: StoryStep, position: Long, documentId: String) {
        storyStep.run {
            storyStepQueries.insert(
                id = id,
                local_id = localId,
                type = type.number.toLong(),
                parent_id = parentId,
                url = url,
                path = path,
                text = text,
                checked = checked.let { if (it == true) 1 else 0 },
                position = position,
                document_id = documentId,
                is_group = isGroup.let { if (it) 1 else 0 },
                has_inner_steps = steps.isNotEmpty().let { if (it) 1 else 0 },
                background_color = decoration.backgroundColor?.toLong(),
            )
        }
    }

    fun insertDocuments(vararg documents: Document) {
        documents.forEach { document ->
            insertDocumentWithContent(document)
        }
    }

    fun loadDocumentWithContentByIds(id: List<String>): List<Document> =
        documentQueries.selectWithContentByIds(id).executeAsList()
            .groupBy { it.id }
            .mapNotNull { (documentId, content) ->
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
//                                decoration = decoration, // Todo: Fix!
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
                    )
                }
            }

    fun loadDocumentsWithContentByIds(ids: Set<String>): List<Document> {
        val result = documentQueries.selectWithContentByIds(ids).executeAsList()
            .groupBy { it.id }
            .mapNotNull { (documentId, content) ->
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
//                                decoration = decoration, // Todo: Fix!
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
                    )
                }
            }

        return result
    }

    fun loadDocumentsWithContentByUserId(userId: String): List<Document> {
        return documentQueries.selectWithContentByUserId(userId).executeAsList()
            .groupBy { it.id }
            .mapNotNull { (documentId, content) ->
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
//                                decoration = decoration, // Todo: Fix!
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
                    )
                }
            }
    }

    fun deleteDocumentById(document: String) {
        documentQueries.delete(document)
    }

    fun deleteDocumentByIds(ids: Set<String>) {
        documentQueries.deleteByIds(ids)
    }

    fun loadDocumentWithContentById(documentId: String): Document? =
        documentQueries.selectWithContentById(documentId).executeAsList()
            .groupBy { it.id }
            .mapNotNull { (documentId, content) ->
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
//                                decoration = decoration, // Todo: Fix!
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
                    )
                }
            }
            .firstOrNull()

    fun deleteDocumentsByUserId(userId: String) {
        documentQueries.deleteByUserId(userId)
    }
}
