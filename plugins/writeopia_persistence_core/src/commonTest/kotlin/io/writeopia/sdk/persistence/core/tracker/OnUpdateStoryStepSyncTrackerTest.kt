
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
        val commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
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
        commentsFlow.value = mapOf(conversation.id to conversation.comments)

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
    fun commentChangesShouldSendTombstoneDeltaAfterInitialResync() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-delta",
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
        val initialComments = mapOf(
            "conversation-a" to listOf(
                Comment(id = "comment-1", text = "One"),
                Comment(id = "comment-2", text = "Delete me"),
            ),
            "conversation-b" to listOf(
                Comment(id = "comment-3", text = "Delete thread"),
            ),
        )
        val commentsFlow = MutableStateFlow(initialComments)
        val requests = mutableListOf<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
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
        advanceTimeBy(20)
        runCurrent()

        commentsFlow.value = mapOf(
            "conversation-a" to listOf(
                Comment(id = "comment-1", text = "One"),
                Comment(id = "comment-2", text = "Delete me", deleted = true),
                Comment(id = "comment-4", text = "New reply"),
            ),
            "conversation-b" to listOf(
                Comment(id = "comment-3", text = "Delete thread", deleted = true),
            ),
        )
        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { secondRequest.await() }
        job.cancel()

        assertEquals(2, requests.size)
        assertEquals(
            setOf("conversation-a", "conversation-b"),
            synced.commentConversations?.map { it.id }?.toSet(),
        )
        assertTrue(synced.deletedCommentConversationIds.isEmpty())
        assertTrue(synced.deletedCommentIds.isEmpty())
        assertTrue(
            synced.commentConversations
                ?.single { it.id == "conversation-b" }
                ?.comments
                ?.all { it.deleted } == true
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
        val commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
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

        commentsFlow.value = mapOf(conversation.id to conversation.comments)
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
    fun initialPersistedCommentsShouldResyncWithoutUserEdit() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-1",
            content = mapOf(
                0.0 to StoryStep(
                    id = "step-1",
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
        val conversation = CommentConversation(
            id = "conversation-1",
            comments = listOf(Comment(id = "comment-1", text = "Persisted locally")),
        )
        val commentsFlow = MutableStateFlow(mapOf(conversation.id to conversation.comments))
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

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()
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
        val commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
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

        commentsFlow.value = mapOf(conversation.id to conversation.comments)
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
    fun disconnectedWorkspaceShouldNeverRebindPendingCommentsToAnotherWorkspace() = runTest {
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
        val commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
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

        commentsFlow.value = mapOf(conversation.id to conversation.comments)
        advanceTimeBy(30)
        runCurrent()

        assertFalse(request.isCompleted)

        workspaceIdFlow.value = "workspace-online"
        advanceTimeBy(30)
        runCurrent()

        assertFalse(request.isCompleted)
        job.cancel()
    }

    @Test
    fun nestedOnlyLineEditsShouldBothReachBackendSync() = runTest {
        val now = Clock.System.now()
        val childV1 = StoryStep(
            id = "child-1",
            type = StoryTypes.TEXT.type,
            text = "v1",
        )
        val parentV1 = StoryStep(
            id = "parent-1",
            type = StoryTypes.TEXT.type,
            text = "sheet",
            steps = listOf(childV1),
        )
        val document = Document(
            id = "document-nested-sync",
            content = mapOf(0.0 to parentV1),
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
        val requests = mutableListOf<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val secondRequest =
            CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { request ->
                requests += request
                if (requests.size == 2 && !secondRequest.isCompleted) {
                    secondRequest.complete(request)
                }
                StoryStepSyncResponse(
                    serverTimestamp = request.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to parentV1),
            lastEdit = LastEdit.LineEdition(0.0, parentV1),
        ) to document.info()
        advanceTimeBy(20)
        runCurrent()

        val parentV2 = parentV1.copy(
            steps = listOf(childV1.copy(text = "v2"))
        )
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to parentV2),
            lastEdit = LastEdit.LineEdition(0.0, parentV2),
        ) to document.info()
        advanceTimeBy(20)
        runCurrent()

        val synced = withTimeout(1_000) { secondRequest.await() }
        job.cancel()

        assertEquals(2, requests.size)
        assertEquals("v2", synced.changes.single().storyStep.steps.single().text)
    }

    @Test
    fun failedChangeRetryShouldNotOverwriteNewerPendingChange() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-retry-order",
            type = StoryTypes.TEXT.type,
            text = "v0",
        )
        val document = Document(
            id = "document-retry-order",
            content = mapOf(0.0 to initialStep),
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
        val firstRequestStarted = CompletableDeferred<Unit>()
        val failFirstRequest = CompletableDeferred<Unit>()
        val successfulRequest =
            CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        var attempts = 0
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { request ->
                attempts++
                if (attempts == 1) {
                    firstRequestStarted.complete(Unit)
                    failFirstRequest.await()
                    error("first request failed")
                }
                if (!successfulRequest.isCompleted) {
                    successfulRequest.complete(request)
                }
                StoryStepSyncResponse(
                    serverTimestamp = request.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        val v1 = initialStep.copy(text = "v1")
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to v1),
            lastEdit = LastEdit.LineEdition(0.0, v1),
        ) to document.info()
        advanceTimeBy(20)
        runCurrent()
        withTimeout(1_000) { firstRequestStarted.await() }

        val v2 = initialStep.copy(text = "v2")
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to v2),
            lastEdit = LastEdit.LineEdition(0.0, v2),
        ) to document.info()
        runCurrent()

        failFirstRequest.complete(Unit)
        advanceTimeBy(40)
        runCurrent()

        val synced = withTimeout(1_000) { successfulRequest.await() }
        job.cancel()

        assertEquals("v2", synced.changes.single().storyStep.text)
        assertTrue(synced.deletions.isEmpty())
    }

    @Test
    fun failedChangeRetryShouldNotResurrectChangeOverNewerDeletion() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-delete-order",
            type = StoryTypes.TEXT.type,
            text = "v0",
        )
        val document = Document(
            id = "document-delete-order",
            content = mapOf(0.0 to initialStep),
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
        val firstRequestStarted = CompletableDeferred<Unit>()
        val failFirstRequest = CompletableDeferred<Unit>()
        val successfulRequest =
            CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        var attempts = 0
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { request ->
                attempts++
                if (attempts == 1) {
                    firstRequestStarted.complete(Unit)
                    failFirstRequest.await()
                    error("first request failed")
                }
                if (!successfulRequest.isCompleted) {
                    successfulRequest.complete(request)
                }
                StoryStepSyncResponse(
                    serverTimestamp = request.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        val v1 = initialStep.copy(text = "v1")
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to v1),
            lastEdit = LastEdit.LineEdition(0.0, v1),
        ) to document.info()
        advanceTimeBy(20)
        runCurrent()
        withTimeout(1_000) { firstRequestStarted.await() }

        documentEditionFlow.value = StoryState(
            stories = emptyMap(),
            lastEdit = LastEdit.DeleteEdition(initialStep.id, document.id),
        ) to document.info()
        runCurrent()

        failFirstRequest.complete(Unit)
        advanceTimeBy(40)
        runCurrent()

        val synced = withTimeout(1_000) { successfulRequest.await() }
        job.cancel()

        assertTrue(synced.changes.isEmpty())
        assertEquals(setOf(initialStep.id), synced.deletions.toSet())
    }

    @Test
    fun failedDeletionRetryShouldNotDeleteNewerPendingChange() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-change-order",
            type = StoryTypes.TEXT.type,
            text = "v0",
        )
        val document = Document(
            id = "document-change-order",
            content = mapOf(0.0 to initialStep),
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
        val firstRequestStarted = CompletableDeferred<Unit>()
        val failFirstRequest = CompletableDeferred<Unit>()
        val successfulRequest =
            CompletableDeferred<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        var attempts = 0
        val tracker = OnUpdateStoryStepSyncTracker(
            syncBuffer = StoryStepSyncBuffer(syncIntervalMs = 10),
            syncApi = { request ->
                attempts++
                if (attempts == 1) {
                    firstRequestStarted.complete(Unit)
                    failFirstRequest.await()
                    error("first request failed")
                }
                if (!successfulRequest.isCompleted) {
                    successfulRequest.complete(request)
                }
                StoryStepSyncResponse(
                    serverTimestamp = request.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),
                )
            },
        )

        val job = launch {
            tracker.syncStorySteps(documentEditionFlow, workspaceIdFlow)
        }
        runCurrent()

        documentEditionFlow.value = StoryState(
            stories = emptyMap(),
            lastEdit = LastEdit.DeleteEdition(initialStep.id, document.id),
        ) to document.info()
        advanceTimeBy(20)
        runCurrent()
        withTimeout(1_000) { firstRequestStarted.await() }

        val v2 = initialStep.copy(text = "v2")
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to v2),
            lastEdit = LastEdit.LineEdition(0.0, v2),
        ) to document.info()
        runCurrent()

        failFirstRequest.complete(Unit)
        advanceTimeBy(40)
        runCurrent()

        val synced = withTimeout(1_000) { successfulRequest.await() }
        job.cancel()

        assertEquals("v2", synced.changes.single().storyStep.text)
        assertTrue(synced.deletions.isEmpty())
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
        val commentsFlow = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
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

        commentsFlow.value = mapOf(conversation.id to conversation.comments)
        advanceTimeBy(120)
        runCurrent()

        val synced = withTimeout(1_000) { success.await() }
        job.cancel()

        assertTrue(attempts >= 3)
        assertEquals(listOf(conversation.id), synced.commentConversations?.map { it.id })
    }
}
