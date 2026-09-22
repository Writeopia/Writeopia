@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.manager.DocumentUpdate
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OnUpdateDocumentTrackerTest {

    @Test
    fun wholeDocumentSaveShouldPreserveCommentConversations() = runTest {
        val now = Clock.System.now()
        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "hello")),
        )
        val sourceDocument = Document(
            id = "document-1",
            content = mapOf(
                0.0 to StoryStep(text = "hello", type = StoryTypes.TEXT.type)
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspace-1",
            parentId = "root",
            commentConversations = listOf(conversation),
        )
        val savedDocument = CompletableDeferred<Document>()
        val documentUpdate = object : DocumentUpdate {
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
        val tracker = OnUpdateDocumentTracker(documentUpdate)
        val documentEditionFlow = MutableStateFlow(
            StoryState(
                stories = sourceDocument.content,
                lastEdit = LastEdit.Whole,
            ) to sourceDocument.info()
        )
        val workspaceIdFlow = MutableStateFlow(sourceDocument.workspaceId)
        val commentConversationsFlow = MutableStateFlow(sourceDocument.commentConversations)

        val job = launch {
            tracker.saveOnStoryChanges(
                documentEditionFlow,
                workspaceIdFlow,
                commentConversationsFlow,
            )
        }

        val persisted = withTimeout(1_000) { savedDocument.await() }
        job.cancel()

        assertEquals(listOf(conversation), persisted.commentConversations)
    }
}
