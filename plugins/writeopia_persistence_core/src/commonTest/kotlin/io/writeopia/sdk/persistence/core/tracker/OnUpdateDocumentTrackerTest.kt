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

    @Test
    fun tagChangeShouldSaveTheWholeDocumentWithItsTags() = runTest {
        val sourceDocument = sourceDocument(emptyList())
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)
        val documentEditionFlow = MutableStateFlow(
            StoryState(
                stories = sourceDocument.content,
                lastEdit = LastEdit.Nothing,
            ) to sourceDocument.info()
        )
        val tagIdsFlow = MutableStateFlow(setOf("tag-1"))

        val job = launch {
            tracker.saveOnStoryChanges(
                documentEditionFlow,
                MutableStateFlow(sourceDocument.workspaceId),
                MutableStateFlow(emptyMap()),
                tagIdsFlow,
            )
        }
        runCurrent()
        assertFalse(recorder.savedDocument.isCompleted)

        tagIdsFlow.value = setOf("tag-1", "tag-2")

        val persisted = withTimeout(1_000) { recorder.savedDocument.await() }
        job.cancel()

        assertEquals(setOf("tag-1", "tag-2"), persisted.tagIds)
        assertEquals(sourceDocument.content.getValue(0.0).text, persisted.content.getValue(0.0).text)
        assertEquals(true, persisted.lastUpdatedAt > sourceDocument.lastUpdatedAt)
    }

    @Test
    fun wholeDocumentSaveShouldCarryTheTags() = runTest {
        val sourceDocument = sourceDocument(emptyList())
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
                MutableStateFlow(emptyMap()),
                MutableStateFlow(setOf("tag-1")),
            )
        }

        val persisted = withTimeout(1_000) { recorder.savedDocument.await() }
        job.cancel()

        assertEquals(setOf("tag-1"), persisted.tagIds)
    }

    @Test
    fun bulkDeletionShouldDeleteTheStepsAndSaveTheOnesItChanged() = runTest {
        val sourceDocument = sourceDocument(emptyList())
        val recorder = RecordingDocumentUpdate()
        val tracker = OnUpdateDocumentTracker(recorder)
        // An accepted AI answer: the answer goes, its text stays as a new step.
        val accepted = StoryStep(type = StoryTypes.TEXT.type, text = "accepted")
        val skeleton = StoryStep(type = StoryTypes.SKELETON.type, text = "later", ephemeral = true)

        val job = launch {
            tracker.saveOnStoryChanges(
                MutableStateFlow(
                    StoryState(
                        stories = mapOf(0.0 to accepted, 1.0 to skeleton),
                        lastEdit = LastEdit.BulkDeleteEdition(
                            deletedIds = listOf("answer-1"),
                            updatedSteps = listOf(0.0 to accepted, 1.0 to skeleton),
                        ),
                    ) to sourceDocument.info()
                ),
                MutableStateFlow(sourceDocument.workspaceId),
                MutableStateFlow(emptyMap()),
            )
        }

        val saved = withTimeout(1_000) { recorder.savedSteps.await() }
        job.cancel()

        assertEquals(listOf("answer-1"), recorder.deletedStepIds)
        assertEquals(listOf(0.0), saved.map { (position, _) -> position })
        assertEquals(listOf(accepted.id), saved.map { (_, step) -> step.id })
        assertEquals(true, saved.single().second.lastUpdatedAt != null, "The step should be stamped")
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
        val savedSteps = CompletableDeferred<List<Pair<Double, StoryStep>>>()
        val deletedStepIds = mutableListOf<String>()

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
        ) {
            if (!savedSteps.isCompleted) savedSteps.complete(steps)
        }

        override suspend fun deleteStoryStep(storyStepId: String, documentId: String) {
            deletedStepIds.add(storyStepId)
        }
    }
}
