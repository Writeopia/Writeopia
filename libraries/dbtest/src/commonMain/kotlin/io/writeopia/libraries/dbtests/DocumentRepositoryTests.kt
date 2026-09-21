@file:OptIn(ExperimentalTime::class)

package io.writeopia.libraries.dbtests

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class DocumentRepositoryTests(private val documentRepository: DocumentRepository) {

    suspend fun saveAndLoadADocumentWithContent() {
        val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = listOf(
                (
                    0.0 to StoryStep(
                        type = StoryTypes.TEXT.type,
                        text = "text",
                        tags = setOf(TagInfo(Tag.H1)),
                        dbPosition = 0.0
                    )
                )
            ).toMap(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false,
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun saveAndLoadADocumentWithoutContent() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = emptyMap(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentWithOneImageInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = simpleImage(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentWithManyImagesInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageStepsList(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(loadedDocument?.content?.isNotEmpty(), true)
        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentOneImageGroupInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageGroup(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun favoriteAndUnFavoriteDocumentById() {
        val now = now()

        val id = GenerateId.generate()
        val workspaceId = Random(24).nextInt().toString()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageGroup(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            favorite = false,
            parentId = "parentId",
            isLocked = false
        )

        documentRepository.saveDocument(document)

        val loadedDocument0 = documentRepository.loadDocumentById(id, workspaceId)
        assertEquals(loadedDocument0?.favorite, false)

        documentRepository.favoriteDocumentByIds(setOf(id))
        val loadedDocument1 = documentRepository.loadDocumentById(id, workspaceId)
        assertEquals(loadedDocument1?.favorite, true)

        documentRepository.unFavoriteDocumentByIds(setOf(id))
        val loadedDocument2 = documentRepository.loadDocumentById(id, workspaceId)
        assertEquals(loadedDocument2?.favorite, false)
    }

    suspend fun saveDocumentWithComments(): Document {
        val now = now()
        val conversationId = "conversation-1"
        val document = Document(
            id = GenerateId.generate(),
            title = "Document with comments",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, conversationId),
                        SpanInfo.create(10, 13, Span.COMMENT, "conversation-2"),
                    ),
                    dbPosition = 0.0,
                )
            ),
            commentConversations = listOf(
                CommentConversation(
                    id = conversationId,
                    comments = listOf(
                        Comment(id = "comment-1", text = "First"),
                        Comment(id = "comment-2", text = "Second"),
                    )
                ),
                CommentConversation(
                    id = "conversation-2",
                    comments = listOf(
                        Comment(id = "comment-3", text = "Third"),
                    )
                ),
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false,
        )

        documentRepository.saveDocument(document)
        return document
    }

    suspend fun saveAndLoadDocumentWithComments() {
        val document = saveDocumentWithComments()
        val loadedDocument = documentRepository.loadDocumentById(document.id, document.workspaceId)

        assertEquals(document, loadedDocument)
    }

    suspend fun collectionLoadPreservesComments() {
        val document = saveDocumentWithComments()
        val loadedDocument = documentRepository.loadDocumentsWorkspace(document.workspaceId)
            .first { it.id == document.id }

        assertEquals(document.commentConversations, loadedDocument.commentConversations)
    }

    suspend fun saveSimpleDocumentAndLoadByParentId() {
        val document = Document(
            id = GenerateId.generate(),
            title = "Document1",
            content = emptyMap(),
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "parentId",
            isLocked = false
        )

        val loadedDocument = documentRepository.run {
            saveDocument(document)
            loadDocumentsByParentId("parentId", "workspaceId")
        }.first()

        assertEquals(document.id, loadedDocument.id)
    }
}

private fun now() = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

fun simpleText(): Map<Double, StoryStep> = mapOf(
    0.0 to StoryStep(
        type = StoryTypes.TEXT.type,
        text = "text",
        dbPosition = 0.0
    )
)

fun simpleImage(): Map<Double, StoryStep> = mapOf(
    0.0 to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
        dbPosition = 0.0
    )
)

fun imageStepsList(): Map<Double, StoryStep> = mapOf(
    0.0 to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
        dbPosition = 0.0
    ),
    1.0 to StoryStep(
        localId = "1",
        type = StoryTypes.IMAGE.type,
        dbPosition = 1.0
    ),
    2.0 to StoryStep(
        localId = "2",
        type = StoryTypes.IMAGE.type,
        dbPosition = 2.0
    ),
)

fun imageGroup(): Map<Double, StoryStep> {
    val groupId = GenerateId.generate()

    return mapOf(
        0.0 to StoryStep(
            id = groupId,
            localId = "1",
            type = StoryTypes.GROUP_IMAGE.type,
            dbPosition = 0.0,
            steps = listOf(
                StoryStep(
                    localId = "2",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                ),
                StoryStep(
                    localId = "3",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                ),
                StoryStep(
                    localId = "4",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                )
            )
        ),
    )
}
