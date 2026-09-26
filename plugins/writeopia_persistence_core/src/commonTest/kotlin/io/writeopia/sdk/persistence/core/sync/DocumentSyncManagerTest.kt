@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package io.writeopia.sdk.persistence.core.sync

import io.writeopia.sdk.manager.DocumentTracker
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class DocumentSyncManagerTest {

    @Test
    fun dbSyncStaysBoundToInitialWorkspace() = runTest {
        val observedWorkspaces = mutableListOf<String>()
        val tracker = object : DocumentTracker {
            override suspend fun saveOnStoryChanges(
                documentEditionFlow: Flow<Pair<StoryState, DocumentInfo>>,
                workspaceIdFlow: Flow<String>,
            ) {
                workspaceIdFlow.toList(observedWorkspaces)
            }
        }
        val workspaceIdFlow = MutableStateFlow("workspace-a")
        val manager = DocumentSyncManager(
            dispatcher = StandardTestDispatcher(testScheduler),
            scope = this,
        )

        manager.registerForDbSync(
            documentId = "document-1",
            documentEditionFlow = emptyFlow(),
            workspaceIdFlow = workspaceIdFlow,
            documentTracker = tracker,
        )
        workspaceIdFlow.value = "workspace-b"
        advanceUntilIdle()
        assertEquals(listOf("workspace-a"), observedWorkspaces)

        advanceUntilIdle()
        assertEquals(listOf("workspace-a"), observedWorkspaces)
    }

    @Test
    fun backendSyncStaysBoundToInitialWorkspace() = runTest {
        val now = Clock.System.now()
        val initialStep = StoryStep(
            id = "step-1",
            type = StoryTypes.TEXT.type,
            text = "one",
        )
        val document = Document(
            id = "document-1",
            content = mapOf(0.0 to initialStep),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = "workspace-a",
            parentId = "root",
        )
        val documentEditionFlow = MutableStateFlow(
            StoryState(

                stories = document.content,
                lastEdit = LastEdit.LineEdition(0.0, initialStep),
            ) to document.info()
        )
        val workspaceIdFlow = MutableStateFlow(document.workspaceId)
        val requests =
            mutableListOf<io.writeopia.sdk.serialization.request.StoryStepSyncRequest>()
        val manager = DocumentSyncManager(
            dispatcher = StandardTestDispatcher(testScheduler),
            scope = this,
        )

        manager.registerForBackendSync(
            documentId = document.id,
            documentEditionFlow = documentEditionFlow,
            workspaceIdFlow = workspaceIdFlow,
            syncApi = { request ->
                requests += request
                StoryStepSyncResponse(
                    serverTimestamp = request.requestTimestamp,
                    updatedSteps = emptyList(),
                    deletedIds = emptyList(),

                )
            },
        )
        workspaceIdFlow.value = "workspace-b"
        advanceUntilIdle()

        val updatedStep = initialStep.copy(text = "two")
        documentEditionFlow.value = StoryState(
            stories = mapOf(0.0 to updatedStep),
            lastEdit = LastEdit.LineEdition(0.0, updatedStep),
        ) to document.info()
        advanceUntilIdle()

        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { request -> request.workspaceId == "workspace-a" })

        manager.unregisterFromSync(document.id)
    }
}
