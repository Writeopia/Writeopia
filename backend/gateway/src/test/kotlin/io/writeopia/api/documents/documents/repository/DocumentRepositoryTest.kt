
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
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.request.StoryStepChangeApi
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
            commentConversations = commentMap(conversation),
        )

        database.saveDocument(document)
        val loaded = database.getDocumentWithContentById(documentId, workspaceId)

        assertEquals(commentMap(conversation), loaded?.commentConversations)
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
    fun `deleted comment tombstone survives reload and stale active update`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversationId = GenerateId.generate()
        val commentId = GenerateId.generate()

        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                commentConversations = mapOf(
                    conversationId to listOf(Comment(id = commentId, text = "Active"))
                ),
            )
        )

        database.applyCommentDelta(
            documentId = documentId,
            conversations = mapOf(
                conversationId to listOf(Comment(id = commentId, text = "Deleted", deleted = true))
            ),
            deletedConversationIds = emptyList(),
            deletedCommentIds = emptyList(),
        )
        var loaded = database.getDocumentWithContentById(documentId, workspaceId)!!
        assertTrue(loaded.commentConversations.getValue(conversationId).single().deleted)

        database.applyCommentDelta(
            documentId = documentId,
            conversations = mapOf(
                conversationId to listOf(Comment(id = commentId, text = "Stale active"))
            ),
            deletedConversationIds = emptyList(),
            deletedCommentIds = emptyList(),
        )
        loaded = database.getDocumentWithContentById(documentId, workspaceId)!!
        assertTrue(loaded.commentConversations.getValue(conversationId).single().deleted)

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `comment id collision should not move a comment between documents`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val firstWorkspaceId = GenerateId.generate()
        val secondWorkspaceId = GenerateId.generate()
        val firstDocumentId = GenerateId.generate()
        val secondDocumentId = GenerateId.generate()
        val sharedCommentId = GenerateId.generate()

        database.saveDocument(
            Document(
                id = firstDocumentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = firstWorkspaceId,
                parentId = "root",
                commentConversations = commentMap(
                    CommentConversation(
                        id = GenerateId.generate(),
                        comments = listOf(Comment(id = sharedCommentId, text = "owner")),
                    )
                ),
            )
        )

        assertFailsWith<IllegalArgumentException> {
            database.saveDocument(
                Document(
                    id = secondDocumentId,
                    createdAt = now,
                    lastUpdatedAt = now,
                    lastSyncedAt = now,
                    workspaceId = secondWorkspaceId,
                    parentId = "root",
                    commentConversations = commentMap(
                        CommentConversation(
                            id = GenerateId.generate(),
                            comments = listOf(Comment(id = sharedCommentId, text = "collision")),
                        )
                    ),
                )
            )
        }

        val owner = database.getDocumentWithContentById(firstDocumentId, firstWorkspaceId)
        assertEquals("owner", owner?.commentConversations?.values?.single()?.single()?.text)
        assertEquals(null, database.getDocumentWithContentById(secondDocumentId, secondWorkspaceId))

        database.deleteDocumentById(firstDocumentId)
    }

    @Test
    fun `duplicate comment ids should be rejected before persistence`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val commentId = GenerateId.generate()

        assertFailsWith<IllegalArgumentException> {
            database.saveDocument(
                Document(
                    id = documentId,
                    createdAt = now,
                    lastUpdatedAt = now,
                    lastSyncedAt = now,
                    workspaceId = workspaceId,
                    parentId = "root",
                    commentConversations = commentMap(
                        CommentConversation(
                            id = GenerateId.generate(),
                            comments = listOf(
                                Comment(id = commentId, text = "first"),
                                Comment(id = commentId, text = "second"),
                            ),
                        )
                    ),
                )
            )
        }

        assertEquals(null, database.getDocumentById(documentId, workspaceId))
    }

    @Test
    fun `full document write should reject document owned by another workspace`() = runTest {
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
            DocumentsService.documentFromApiForWrite(
                document = DocumentApi(
                    id = documentId,
                    workspaceId = otherWorkspaceId,
                    parentId = "root",
                    commentConversations = emptyList(),
                ),
                workspaceId = otherWorkspaceId,
                writeopiaDb = database,
            )
        }

        assertEquals(ownerWorkspaceId, database.getDocumentById(documentId, ownerWorkspaceId)?.workspaceId)
        assertEquals(null, database.getDocumentById(documentId, otherWorkspaceId))

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
                commentConversations = commentMap(conversation),
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
            commentMap(conversation),
            database.getDocumentWithContentById(documentId, workspaceId)?.commentConversations,
        )

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `delete documents should skip unknown ids and delete owned ids`() = runTest {
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

        DocumentsService.deleteDocuments(
            documentIds = listOf(documentId, "missing-document"),
            workspaceId = workspaceId,
            userId = "test-user",
            writeopiaDb = database,
        )

        assertEquals(null, database.getDocumentWithContentById(documentId, workspaceId))
    }

    @Test
    fun `delete documents should accept only unknown ids`() = runTest {
        val database = configurePersistence()

        DocumentsService.deleteDocuments(
            documentIds = listOf("missing-document"),
            workspaceId = GenerateId.generate(),
            userId = "test-user",
            writeopiaDb = database,
        )
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
                commentConversations = commentMap(otherConversation),
            ),
        )

        val loaded = database.getDocumentsByParentId("root", workspaceId)

        assertTrue(loaded.any { it.id == ownDocumentId })
        assertTrue(loaded.none { it.id == otherDocumentId })
        assertTrue(loaded.none { it.commentConversations.containsKey(otherConversation.id) })

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
            comments = listOf(Comment(id = GenerateId.generate(), text = "First", deleted = true)),
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
                        SpanInfo.create(0, 7, Span.COMMENT, conversation.id),
                        SpanInfo.create(8, 9, Span.COMMENT, "missing-conversation"),
                    ),
                )
            ),
            commentConversations = commentMap(conversation),
        )

        database.saveDocument(original)
        val clone = DocumentsService.cloneDocuments(
            documentIds = listOf(documentId),
            workspaceId = workspaceId,
            writeopiaDb = database,
            useAi = false,
        ).single()

        val (clonedConversationId, clonedComments) = clone.commentConversations.entries.single()
        val clonedSpan = clone.content.values.single().spans.single { it.span == Span.COMMENT }

        assertEquals(conversation.comments.map { it.text }, clonedComments.map { it.text })
        assertEquals(conversation.comments.map { it.deleted }, clonedComments.map { it.deleted })
        assertTrue(clonedConversationId != conversation.id)
        assertTrue(clonedComments.single().id != conversation.comments.single().id)
        assertEquals(clonedConversationId, clonedSpan.extra)

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

        assertEquals(conversationId, changed.commentConversations.keys.single())
        assertTrue(changed.lastSyncedAt!!.toEpochMilliseconds() > 1)

        database.deleteDocumentById(documentId)
    }

    @Test
    fun `stale comment delta should preserve unseen remote data and apply explicit deletions`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val conversationA = "conversation-a"
        val conversationB = "conversation-b"
        val sharedId = "comment-shared"
        val remoteReplyId = "comment-remote-reply"
        val unseenId = "comment-unseen"
        database.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                commentConversations = mapOf(
                    conversationA to listOf(
                        Comment(id = sharedId, text = "Shared"),
                        Comment(id = remoteReplyId, text = "Remote reply"),
                    ),
                    conversationB to listOf(
                        Comment(id = unseenId, text = "Unseen thread"),
                    ),
                ),
            )
        )

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = StoryStepSyncRequest(
                documentId = documentId,
                workspaceId = workspaceId,
                lastSyncTimestamp = 0,
                requestTimestamp = 10,
                changes = emptyList(),
                deletions = emptyList(),
                commentConversations = listOf(
                    CommentConversationApi(
                        id = conversationA,
                        comments = listOf(
                            CommentApi(id = sharedId, text = "Shared edited"),
                            CommentApi(id = "comment-local-reply", text = "Local reply"),
                        ),
                    )
                ),
            ),
            writeopiaDb = database,
        )

        var loaded = database.getDocumentWithContentById(documentId, workspaceId)!!
        assertEquals(setOf(conversationA, conversationB), loaded.commentConversations.keys)
        assertEquals(
            setOf(sharedId, remoteReplyId, "comment-local-reply"),
            loaded.commentConversations.getValue(conversationA).map { it.id }.toSet(),
        )
        assertEquals(
            listOf(unseenId),
            loaded.commentConversations.getValue(conversationB).map { it.id },
        )

        DocumentsService.syncStorySteps(
            documentId = documentId,
            workspaceId = workspaceId,
            request = StoryStepSyncRequest(
                documentId = documentId,
                workspaceId = workspaceId,
                lastSyncTimestamp = 10,
                requestTimestamp = 20,
                changes = emptyList(),
                deletions = emptyList(),
                commentConversations = null,
                deletedCommentConversationIds = listOf(conversationB),
                deletedCommentIds = listOf(remoteReplyId),
            ),
            writeopiaDb = database,
        )

        loaded = database.getDocumentWithContentById(documentId, workspaceId)!!
        assertEquals(setOf(conversationA), loaded.commentConversations.keys)
        assertEquals(
            setOf(sharedId, "comment-local-reply"),
            loaded.commentConversations.getValue(conversationA).map { it.id }.toSet(),
        )

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
    fun `step sync cannot move a step from another document`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val targetDocumentId = GenerateId.generate()
        val victimDocumentId = GenerateId.generate()
        val victimStep = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "victim",
            lastUpdatedAt = 1,
        )
        database.saveDocument(Document(id = targetDocumentId, createdAt = now, lastUpdatedAt = now, lastSyncedAt = now, workspaceId = workspaceId, parentId = "root"))
        database.saveDocument(
            Document(
                id = victimDocumentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0.0 to victimStep),
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DocumentsService.syncStorySteps(
                documentId = targetDocumentId,
                workspaceId = workspaceId,
                request = StoryStepSyncRequest(
                    documentId = targetDocumentId,
                    workspaceId = workspaceId,
                    lastSyncTimestamp = 0,
                    requestTimestamp = 10,
                    changes = listOf(
                        StoryStepChangeApi(
                            storyStep = victimStep.copy(text = "hijacked", lastUpdatedAt = 10).toApi(0.0),
                            position = 0.0,
                        )
                    ),
                    deletions = emptyList(),
                    commentConversations = null,
                ),
                writeopiaDb = database,
            )
        }

        val victimReloaded = database.getDocumentWithContentById(victimDocumentId, workspaceId)
        val targetReloaded = database.getDocumentWithContentById(targetDocumentId, workspaceId)
        assertEquals("victim", victimReloaded?.content?.get(0.0)?.text)
        assertTrue(targetReloaded?.content?.values?.none { step -> step.id == victimStep.id } == true)

        database.deleteDocumentById(targetDocumentId)
        database.deleteDocumentById(victimDocumentId)
    }

    @Test
    fun `step sync cannot delete a step from another document`() = runTest {
        val database = configurePersistence()
        val now = Clock.System.now()
        val workspaceId = GenerateId.generate()
        val targetDocumentId = GenerateId.generate()
        val victimDocumentId = GenerateId.generate()
        val victimStep = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "victim",
            lastUpdatedAt = 1,
        )
        database.saveDocument(Document(id = targetDocumentId, createdAt = now, lastUpdatedAt = now, lastSyncedAt = now, workspaceId = workspaceId, parentId = "root"))
        database.saveDocument(
            Document(
                id = victimDocumentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0.0 to victimStep),
            )
        )

        DocumentsService.syncStorySteps(
            documentId = targetDocumentId,
            workspaceId = workspaceId,
            request = StoryStepSyncRequest(
                documentId = targetDocumentId,
                workspaceId = workspaceId,
                lastSyncTimestamp = 0,
                requestTimestamp = 10,
                changes = emptyList(),
                deletions = listOf(victimStep.id),
                commentConversations = null,
            ),
            writeopiaDb = database,
        )

        val victimReloaded = database.getDocumentWithContentById(victimDocumentId, workspaceId)
        assertEquals("victim", victimReloaded?.content?.get(0.0)?.text)

        database.deleteDocumentById(targetDocumentId)
        database.deleteDocumentById(victimDocumentId)
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
            commentConversations = commentMap(conversation),
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

    private fun commentMap(
        vararg conversations: CommentConversation,
    ): Map<String, List<Comment>> =
        conversations.associate { conversation ->
            conversation.id to conversation.comments
        }

}
