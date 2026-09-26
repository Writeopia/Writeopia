
@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.manager.DocumentUpdate
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OnUpdateDocumentTrackerTest {

    @Test
    fun wholeDocumentSaveShouldPreserveCommentConversations() = runTest {
        val conversation = conversation()
        val sourceDocument = sourceDocument(listOf(conversation))
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)

        val job = launch {
            tracker.saveOnStoryChanges(
                MutableStateFlow(
                    StoryState(
                        stories = sourceDocument.content,
                        lastEdit = LastEdit.Whole,
                    ) to sourceDocument.info()
                ),
                MutableStateFlow(sourceDocument.workspaceId),
                MutableStateFlow(sourceDocument.commentConversations),
            )
        }

        val persisted = withTimeout(1_000) { recorder.savedDocument.await() }
        job.cancel()

        assertEquals(mapOf(conversation.id to conversation.comments), persisted.commentConversations)
        assertEquals(sourceDocument.workspaceId, persisted.workspaceId)
    }

    @Test
    fun legacySaveShouldRejectCommentBearingDocumentsBeforePersisting() = runTest {
        val conversation = conversation()
        val sourceDocument = sourceDocument(listOf(conversation))
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)

        assertFailsWith<IllegalStateException> {
            tracker.saveOnStoryChanges(
                MutableStateFlow(
                    StoryState(
                        stories = sourceDocument.content,
                        lastEdit = LastEdit.Whole,
                    ) to sourceDocument.info()
                ),
                MutableStateFlow(sourceDocument.workspaceId),
            )
        }

        assertFalse(recorder.savedDocument.isCompleted)
    }

    @Test
    fun legacySaveShouldRejectNestedCommentSpansBeforePersisting() = runTest {
        val now = Clock.System.now()
        val conversation = conversation()
        val document = Document(
            id = "document-nested",
            content = mapOf(
                0.0 to StoryStep(
                    text = "parent",
                    type = StoryTypes.TEXT.type,
                    steps = listOf(
                        StoryStep(
                            text = "nested",
                            type = StoryTypes.TEXT.type,
                            spans = setOf(
                                SpanInfo.create(0, 6, Span.COMMENT, conversation.id)
                            ),
                        )
                    ),
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspace-1",
            parentId = "root",
            commentConversations = mapOf(conversation.id to conversation.comments),
        )
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)

        assertFailsWith<IllegalStateException> {
            tracker.saveOnStoryChanges(
                MutableStateFlow(
                    StoryState(
                        stories = document.content,
                        lastEdit = LastEdit.Whole,
                    ) to document.info()
                ),
                MutableStateFlow(document.workspaceId),
            )
        }

        assertFalse(recorder.savedDocument.isCompleted)
    }

    @Test
    fun commentOnlyChangeShouldPersistThroughDocumentTracker() = runTest {
        val firstComment = Comment(id = "comment-1", text = "first")
        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(firstComment),
        )
        val sourceDocument = sourceDocument(listOf(conversation))
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)
        val documentEditionFlow = MutableStateFlow(
            StoryState(
                stories = sourceDocument.content,
                lastEdit = LastEdit.Nothing,
            ) to sourceDocument.info()
        )
        val commentConversationsFlow = MutableStateFlow(sourceDocument.commentConversations)

        val job = launch {
            tracker.saveOnStoryChanges(
                documentEditionFlow,
                MutableStateFlow(sourceDocument.workspaceId),
                commentConversationsFlow,
            )
        }
        runCurrent()

        val reply = Comment(id = "comment-2", text = "reply")
        val updatedConversation = conversation.copy(comments = conversation.comments + reply)
        commentConversationsFlow.value = mapOf(
            updatedConversation.id to updatedConversation.comments
        )

        val persisted = withTimeout(1_000) { recorder.savedDocument.await() }
        job.cancel()

        assertEquals(mapOf(updatedConversation.id to updatedConversation.comments), persisted.commentConversations)
        assertEquals(sourceDocument.workspaceId, persisted.workspaceId)
    }

    private fun conversation(): CommentConversation =
        CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "hello")),
        )

    private fun sourceDocument(
        conversations: List<CommentConversation>,
    ): Document {
        val now = Clock.System.now()
        val conversationId = conversations.firstOrNull()?.id

        return Document(
            id = "document-1",
            content = mapOf(
                0.0 to StoryStep(
                    text = "hello",
                    type = StoryTypes.TEXT.type,
                    spans = conversationId?.let { id ->
                        setOf(SpanInfo.create(0, 5, Span.COMMENT, id))
                    } ?: emptySet(),
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspace-1",
            parentId = "root",
            commentConversations = conversations.associate { conversation ->
                conversation.id to conversation.comments
            },
        )
    }

    private class RecordingDocumentUpdate : DocumentUpdate {
        val savedDocument = CompletableDeferred<Document>()

        override suspend fun saveDocument(document: Document) {
            if (!savedDocument.isCompleted) savedDocument.complete(document)
        }

        override suspend fun saveDocumentMetadata(document: Document) = Unit

        override suspend fun saveStoryStep(
            storyStep: StoryStep,
            position: Double,
            documentId: String,
        ) = Unit

        override suspend fun updateStoryStep(
            storyStep: StoryStep,
            position: Double,
            documentId: String,
        ) = Unit

        override suspend fun saveStorySteps(
            steps: List<Pair<Double, StoryStep>>,
            documentId: String,
        ) = Unit

        override suspend fun deleteStoryStep(storyStepId: String, documentId: String) = Unit
    }
}

