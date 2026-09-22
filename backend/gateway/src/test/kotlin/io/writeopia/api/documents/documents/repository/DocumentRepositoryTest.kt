@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.api.documents.documents.DocumentsService
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.CommentApi
import io.writeopia.sdk.serialization.data.CommentConversationApi
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class DocumentRepositoryTest {

    @Test
    fun `comments should round trip through backend persistence`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversation = CommentConversation(
            id = GenerateId.generate(),
            comments = listOf(
                Comment(id = GenerateId.generate(), text = "First"),
                Comment(id = GenerateId.generate(), text = "Reply"),
            ),
        )
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 7, Span.COMMENT, conversation.id)
                    ),
                )
            ),
            commentConversations = listOf(conversation),
        )

        database.saveDocument(document)
        val loaded = database.getDocumentWithContentById(documentId, workspaceId)

        assertEquals(listOf(conversation), loaded?.commentConversations)
        assertEquals(
            conversation.id,
            loaded?.content?.values?.firstOrNull()
                ?.spans
                ?.firstOrNull { it.span == Span.COMMENT }
                ?.extra,
        )

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `legacy full document write should not erase existing comments`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversation = CommentConversation(
            id = GenerateId.generate(),
            comments = listOf(Comment(id = GenerateId.generate(), text = "keep")),
        )

        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                commentConversations = listOf(conversation),
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DocumentsService.documentFromApiForWrite(
                document = DocumentApi(
                    id = documentId,
                    workspaceId = workspaceId,
                    parentId = "root",
                ),
                workspaceId = workspaceId,
                writeopiaDb = database,
            )
        }

        val explicitModernPayload = DocumentsService.documentFromApiForWrite(
            document = DocumentApi(
                id = documentId,
                workspaceId = workspaceId,
                parentId = "root",
                commentConversations = emptyList(),
            ),
            workspaceId = workspaceId,
            writeopiaDb = database,
        )
        assertTrue(explicitModernPayload.commentConversations.isEmpty())

        assertEquals(
            listOf(conversation),
            database.getDocumentWithContentById(documentId, workspaceId)?.commentConversations,
        )

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `parent document loading should stay inside the requested workspace`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val otherWorkspaceId = GenerateId.generate()
        val ownDocumentId = GenerateId.generate()
        val otherDocumentId = GenerateId.generate()
        val otherConversation = CommentConversation(
            id = GenerateId.generate(),
            comments = listOf(Comment(id = GenerateId.generate(), text = "private")),
        )

        database.saveDocument(
            Document(
                id = ownDocumentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
            ),
            Document(
                id = otherDocumentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = otherWorkspaceId,
                parentId = "root",
                commentConversations = listOf(otherConversation),
            ),
        )

        val loaded = database.getDocumentsByParentId("root", workspaceId)

        assertTrue(loaded.any { it.id == ownDocumentId })
        assertTrue(loaded.none { it.id == otherDocumentId })
        assertTrue(loaded.flatMap { it.commentConversations }.none { it.id == otherConversation.id })

        database.deleteDocumentById(ownDocumentId)
        database.deleteDocumentById(otherDocumentId)
    }

    @Test
    fun `cloning should remap comment and conversation ids`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversation = CommentConversation(
            id = GenerateId.generate(),
            comments = listOf(Comment(id = GenerateId.generate(), text = "First")),
        )
        val original = Document(
            id = documentId,
            title = "Original",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 7, Span.COMMENT, conversation.id)
                    ),
                )
            ),
            commentConversations = listOf(conversation),
        )

        database.saveDocument(original)
        val clone = DocumentsService.cloneDocuments(
            documentIds = listOf(documentId),
            workspaceId = workspaceId,
            writeopiaDb = database,
            useAi = false,
        ).single()

        val clonedConversation = clone.commentConversations.single()
        val clonedSpan = clone.content.values.single().spans.single { it.span == Span.COMMENT }

        assertEquals(conversation.comments.map { it.text }, clonedConversation.comments.map { it.text })
        assertTrue(clonedConversation.id != conversation.id)
        assertTrue(clonedConversation.comments.single().id != conversation.comments.single().id)
        assertEquals(clonedConversation.id, clonedSpan.extra)

        database.deleteDocumentById(documentId)
        database.deleteDocumentById(clone.id)
    }

    @Test
    fun `comment only sync should publish document to workspace diff`() = runTest {
        val database = configurePersistence()
        val initial = kotlin.time.Instant.fromEpochMilliseconds(1)
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val document = Document(
            id = documentId,
            createdAt = initial,
            lastUpdatedAt = initial,
            lastSyncedAt = initial,
            workspaceId = workspaceId,
            parentId = "root",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Text",
                )
            ),
        )
        database.saveDocument(document)

        val conversationId = GenerateId.generate()
        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = StoryStepSyncRequest(
                documentId = documentId,
                workspaceId = workspaceId,
                lastSyncTimestamp = 1,
                requestTimestamp = 2,
                changes = emptyList(),
                deletions = emptyList(),
                commentConversations = listOf(
                    CommentConversationApi(
                        id = conversationId,
                        comments = listOf(
                            CommentApi(id = GenerateId.generate(), text = "Remote comment")
                        ),
                    )
                ),
            ),
            writeopiaDb = database,
        )

        val changedDocuments = database.documentsDiffByWorkspace(workspaceId, 1)
        val changed = changedDocuments.single { it.id == documentId }

        assertEquals(conversationId, changed.commentConversations.single().id)
        assertTrue(changed.lastSyncedAt!!.toEpochMilliseconds() > 1)

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `delete should reject document owned by another workspace`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val ownerWorkspaceId = GenerateId.generate()
        val otherWorkspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = ownerWorkspaceId,
                parentId = "root",
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DocumentsService.deleteDocuments(
                documentIds = listOf(documentId),
                workspaceId = otherWorkspaceId,
                userId = "user",
                writeopiaDb = database,
            )
        }

        assertEquals(ownerWorkspaceId, database.getDocumentById(documentId, ownerWorkspaceId)?.workspaceId)

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `step sync should reject document owned by another workspace`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val ownerWorkspaceId = GenerateId.generate()
        val otherWorkspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = ownerWorkspaceId,
                parentId = "root",
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DocumentsService.syncStorySteps(
                documentId = documentId,
                workspaceId = otherWorkspaceId,
                request = StoryStepSyncRequest(
                    documentId = documentId,
                    workspaceId = otherWorkspaceId,
                    lastSyncTimestamp = 0,
                    requestTimestamp = 1,
                    changes = emptyList(),
                    deletions = emptyList(),
                    commentConversations = emptyList(),
                ),
                writeopiaDb = database,
            )
        }

        assertEquals(ownerWorkspaceId, database.getDocumentById(documentId, ownerWorkspaceId)?.workspaceId)
        assertEquals(null, database.getDocumentById(documentId, otherWorkspaceId))

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `step sync should reject empty comment conversations`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DocumentsService.syncStorySteps(
                documentId = documentId,
                workspaceId = workspaceId,
                request = StoryStepSyncRequest(
                    documentId = documentId,
                    workspaceId = workspaceId,
                    lastSyncTimestamp = 0,
                    requestTimestamp = 1,
                    changes = emptyList(),
                    deletions = emptyList(),
                    commentConversations = listOf(
                        CommentConversationApi(
                            id = GenerateId.generate(),
                            comments = emptyList(),
                        )
                    ),
                ),
                writeopiaDb = database,
            )
        }

        assertTrue(
            database.getDocumentWithContentById(documentId, workspaceId)
                ?.commentConversations
                .orEmpty()
                .isEmpty()
        )

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `published documents should not expose editor comments`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversation = CommentConversation(
            id = GenerateId.generate(),
            comments = listOf(Comment(id = GenerateId.generate(), text = "Private note")),
        )
        val nestedStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "Nested text",
            spans = setOf(
                SpanInfo.create(0, 6, Span.COMMENT, conversation.id)
            ),
        )
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            published = true,
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Public text",
                    spans = setOf(
                        SpanInfo.create(0, 6, Span.COMMENT, conversation.id)
                    ),
                    steps = listOf(nestedStep),
                )
            ),
            commentConversations = listOf(conversation),
        )

        database.saveDocument(document)
        val published = DocumentsService.getPublishedDocument(documentId, database)

        assertTrue(published != null)
        assertTrue(published.commentConversations.isEmpty())
        val publishedStep = published.content.values.single()
        assertTrue(publishedStep.spans.none { it.span == Span.COMMENT })
        assertTrue(publishedStep.steps.single().spans.none { it.span == Span.COMMENT })

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `when getting a document, the order of steps should be correct`() = runTest {
        val database = configurePersistence()

        val now = Clock.System.now()

        val content: Map<Double, StoryStep> = mapOf(
            0.0 to StoryStep(type = StoryTypes.TEXT.type, text = "message1"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "message2"),
            2.0 to StoryStep(type = StoryTypes.TEXT.type, text = "message3"),
            3.0 to StoryStep(type = StoryTypes.TEXT.type, text = "message4"),
        )

        val workspaceId = GenerateId.generate()

        val document = listOf(
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
        )

        database.saveDocument(*document.toTypedArray())
        val documentFromDb = database.documentsDiffByFolder("root", workspaceId, 0L)

        assertTrue(documentFromDb.isNotEmpty())
    }
}
