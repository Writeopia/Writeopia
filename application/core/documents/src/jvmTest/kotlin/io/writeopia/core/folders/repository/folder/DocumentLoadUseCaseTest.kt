[Reading 86 lines from start (total: 86 lines, 0 remaining)]

@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentMerger
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class DocumentLoadUseCaseTest {

    @Test
    fun `comment only backend change should reload document`() = runTest {
        val repository = InMemoryDocumentRepository()
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val workspaceId = "workspace"
        val documentId = "document"
        val content = mapOf(
            0.0 to StoryStep(
                id = "step",
                type = StoryTypes.TEXT.type,
                text = "Same text",
                lastUpdatedAt = 1,
            )
        )
        val local = Document(
            id = documentId,
            content = content,
            commentConversations = mapOf(
                "conversation" to listOf(Comment(id = "old", text = "Old"))
            ),
            createdAt = Instant.fromEpochMilliseconds(1),
            lastUpdatedAt = Instant.fromEpochMilliseconds(1),
            lastSyncedAt = Instant.fromEpochMilliseconds(1),
            workspaceId = workspaceId,
            parentId = "root",
        )
        val backend = local.copy(
            commentConversations = mapOf(
                "conversation" to listOf(Comment(id = "new", text = "New"))
            ),
            lastUpdatedAt = Instant.fromEpochMilliseconds(2),
            lastSyncedAt = Instant.fromEpochMilliseconds(2),
        )
        repository.saveDocument(local)
        coEvery { documentsApi.getDocumentById(documentId, workspaceId) } returns
            ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = repository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )

        var mergedCallback: Document? = null
        useCase.fetchAndMergeFromBackend(documentId, workspaceId) { merged ->
            mergedCallback = merged
        }

        val stored = repository.loadDocumentById(documentId, workspaceId)
        assertNotNull(stored)
        assertEquals(backend.commentConversations, stored.commentConversations)
        assertEquals(backend.commentConversations, mergedCallback?.commentConversations)
    }
}

[executed on device: DESKTOP-HJO2US6 (d972d8cd-06d7-4dea-9656-237da7d66e93)]