@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.tracker

import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.persistence.core.sync.StoryStepSyncBuffer
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OnUpdateStoryStepSyncTrackerTest {

    @Test
    fun commentOnlyChangeShouldTriggerBackendSync() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-1",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Text",
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace-1",
            parentId = "root",
        )
        val documentEditionFlow = MutableStateFlow(
            StoryState(
                stories = document.content,
                lastEdit = LastEdit.Nothing,
            ) to document.info()
        )
        val workspaceIdFlow = MutableStateFlow(document.workspaceId)
        val commentsFlow = MutableStateFlow<List<CommentConversation>>(emptyList())
        val request = CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { syncRequest ->
                if (!request.isCompleted) {
                    request.complete(syncRequest)
                }
                StoryStepSyncResponse(
                    serverTimestamp = syncRequest.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
            commentConversationsFlow = commentsFlow,
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "Hello")),
        )
        commentsFlow.value = listOf(conversation)

        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { request.await() }
        job.cancel()

        assertTrue(synced.changes.isEmpty())
        assertTrue(synced.deletions.isEmpty())
        assertEquals(
            listOf(conversation.id),
            synced.commentConversations?.map { it.id },
        )
        assertEquals(
            listOf("Hello"),
            synced.commentConversations?.single()?.comments?.map { it.text },
        )
    }
}
