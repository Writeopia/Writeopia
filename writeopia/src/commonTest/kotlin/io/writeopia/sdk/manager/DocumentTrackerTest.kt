
@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.manager

import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentTrackerTest {

    @Test
    fun commentEmissionShouldNotCancelLegacyStoryPersistence() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-1",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = "workspace-1",
            parentId = "root",
        )
        val editions = MutableSharedFlow<Pair<StoryState, DocumentInfo>>()
        val workspaceIds = MutableStateFlow(document.workspaceId)
        val comments = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
        val storySaved = CompletableDeferred<Boolean>()
        val tracker = object : DocumentTracker {
            override suspend fun saveOnStoryChanges(
                documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
                workspaceIdFlow: Flow<String>,
            ) {
                documentEditionFlow.first()
                storySaved.complete(true)
            }
        }

        val job = launch {
            tracker.saveOnStoryChanges(editions, workspaceIds, comments)
        }
        runCurrent()

        comments.value = mapOf(
            "conversation-1" to listOf(Comment(id = "comment-1", text = "ignored"))
        )
        runCurrent()
        editions.emit(StoryState(stories = emptyMap()) to document.info())
        runCurrent()

        assertTrue(withTimeout(1_000) { storySaved.await() })
        job.cancel()
    }
}

