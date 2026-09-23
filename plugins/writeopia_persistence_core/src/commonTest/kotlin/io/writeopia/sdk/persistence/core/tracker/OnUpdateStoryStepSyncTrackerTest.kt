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
import io.writeopia.sdk.models.workspace.Workspace
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
import kotlin.test.assertFalse
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
            lastSyncedAt = now,
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

    @Test
    fun commentChangeBeforeCollectorStartsShouldTriggerBackendSync() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-race",
            content = mapOf(
                0.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Text")
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = "workspace-1",
            parentId = "root",
        )
        val documentEditionFlow = MutableStateFlow(
            StoryState(stories = document.content, lastEdit = LastEdit.Nothing) to document.info()
        )
        val workspaceIdFlow = MutableStateFlow(document.workspaceId)
        val commentsFlow = MutableStateFlow<List<CommentConversation>>(emptyList())
        val request = CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { syncRequest ->
                if (!request.isCompleted) request.complete(syncRequest)
                StoryStepSyncResponse(
                    serverTimestamp = syncRequest.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
            commentConversationsFlow = commentsFlow,
        )
        val conversation = CommentConversation(
            id = "conversation-race",
            comments = listOf(Comment(id = "comment-race", text = "Before collect")),
        )

        commentsFlow.value = listOf(conversation)
        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()
        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { request.await() }
        job.cancel()

        assertEquals(
            listOf(conversation.id),
            synced.commentConversations?.map { it.id },
        )
    }

    @Test
    fun initialCommentsShouldNotBeSentWithFirstStoryOnlySync() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-1",
            type = StoryTypes.TEXT.type,
            text = "Text",
        )
        val document = Document(
            id = "document-1",
            content = mapOf(0.0 to initialStep),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
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
        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "Already loaded")),
        )
        val commentsFlow = MutableStateFlow(listOf(conversation))
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

        val changedStep = initialStep.copy(text = "Updated")
        documentEditionFlow.value =
            StoryState(
                stories = mapOf(0.0 to changedStep),
                lastEdit = LastEdit.LineEdition(0.0, changedStep),
            ) to document.info()

        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { request.await() }
        job.cancel()

        assertEquals(null, synced.commentConversations)
        assertEquals(listOf("Updated"), synced.changes.map { it.storyStep.text })
    }

    @Test
    fun unchangedCommentsShouldNotBeResentWithStoryStepChanges() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-1",
            type = StoryTypes.TEXT.type,
            text = "Text",
        )
        val document = Document(
            id = "document-1",
            content = mapOf(0.0 to initialStep),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
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
        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "Hello")),
        )
        val commentsFlow = MutableStateFlow<List<CommentConversation>>(emptyList())
        val requests =
            mutableListOf<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val secondRequest =
            CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { syncRequest ->
                requests += syncRequest
                if (requests.size == 2 && !secondRequest.isCompleted) {
                    secondRequest.complete(syncRequest)
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

        commentsFlow.value = listOf(conversation)
        advanceTimeBy(20)
        runCurrent()

        val changedStep = initialStep.copy(text = "Updated")
        documentEditionFlow.value =
            StoryState(
                stories = mapOf(0.0 to changedStep),
                lastEdit = LastEdit.LineEdition(0.0, changedStep),
            ) to document.info()

        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { secondRequest.await() }
        job.cancel()

        assertEquals(2, requests.size)
        assertEquals(null, synced.commentConversations)
        assertEquals(listOf("Updated"), synced.changes.map { it.storyStep.text })
    }
    @Test
    fun disconnectedWorkspaceShouldNotSendCommentsUntilWorkspaceBecomesOnline() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-offline",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = Workspace.disconnectedWorkspace().id,
            parentId = "root",
        )
        val documentEditionFlow = MutableStateFlow(
            StoryState(stories = emptyMap(), lastEdit = LastEdit.Nothing) to document.info()
        )
        val workspaceIdFlow = MutableStateFlow(Workspace.disconnectedWorkspace().id)
        val commentsFlow = MutableStateFlow<List<CommentConversation>>(emptyList())
        val request = CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { syncRequest ->
                if (!request.isCompleted) request.complete(syncRequest)
                StoryStepSyncResponse(
                    serverTimestamp = syncRequest.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
            commentConversationsFlow = commentsFlow,
        )
        val conversation = CommentConversation(
            id = "conversation-offline",
            comments = listOf(Comment(id = "comment-offline", text = "Offline")),
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        commentsFlow.value = listOf(conversation)
        advanceTimeBy(30)
        runCurrent()

        assertFalse(request.isCompleted)

        workspaceIdFlow.value = "workspace-online"
        advanceTimeBy(30)
        runCurrent()

        val synced = withTimeout(1_000) { request.await() }
        job.cancel()

        assertEquals("workspace-online", synced.workspaceId)
        assertEquals(listOf(conversation.id), synced.commentConversations?.map { it.id })
    }

    @Test
    fun commentSyncShouldKeepRetryingAfterRetryLimit() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-retry",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = "workspace-1",
            parentId = "root",
        )
        val documentEditionFlow = MutableStateFlow(
            StoryState(stories = emptyMap(), lastEdit = LastEdit.Nothing) to document.info()
        )
        val workspaceIdFlow = MutableStateFlow(document.workspaceId)
        val commentsFlow = MutableStateFlow<List<CommentConversation>>(emptyList())
        var attempts = 0
        val success = CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            maxRetries = 2,
            syncApi = { syncRequest ->
                attempts++
                if (attempts <= 2) {
                    error("temporary sync failure")
                }
                if (!success.isCompleted) success.complete(syncRequest)
                StoryStepSyncResponse(
                    serverTimestamp = syncRequest.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
            commentConversationsFlow = commentsFlow,
        )
        val conversation = CommentConversation(
            id = "conversation-retry",
            comments = listOf(Comment(id = "comment-retry", text = "Retry me")),
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        commentsFlow.value = listOf(conversation)
        advanceTimeBy(120)
        runCurrent()

        val synced = withTimeout(1_000) { success.await() }
        job.cancel()

        assertTrue(attempts >= 3)
        assertEquals(listOf(conversation.id), synced.commentConversations?.map { it.id })
    }


}
