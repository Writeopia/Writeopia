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
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
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
