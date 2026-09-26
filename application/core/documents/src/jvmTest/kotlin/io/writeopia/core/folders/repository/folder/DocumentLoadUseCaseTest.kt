package io.writeopia.core.folders.repository.folder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentMerger
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class DocumentLoadUseCaseTest {

    @Test
    fun `comment-only backend change is saved and reported`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val conversationId = "conversation-1"
        val content = mapOf(
            0.0 to StoryStep(
                id = "step-1",
                type = StoryTypes.TEXT.type,
                text = "Text",
                spans = setOf(SpanInfo.create(0, 4, Span.COMMENT, conversationId)),
                lastUpdatedAt = 1,
            )
        )
        val local = document(
            lastUpdatedAt = 1,
            content = content,
            commentText = "Local comment",
        )
        val backend = document(
            lastUpdatedAt = 2,
            content = content,
            commentText = "Backend comment",
        )

        coEvery { documentRepository.loadDocumentById(local.id, local.workspaceId) } returns local
        coEvery { documentsApi.getDocumentById(local.id, local.workspaceId) } returns ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )
        var reloaded: Document? = null

        useCase.fetchAndMergeFromBackend(local.id, local.workspaceId) { merged ->
            reloaded = merged
        }

        val merged = assertNotNull(reloaded)
        assertEquals("Backend comment", merged.commentConversations.getValue(conversationId).single().text)
        coVerify(exactly = 1) {
            documentRepository.saveDocument(match { saved -> saved.commentConversations == merged.commentConversations })
        }
    }

    private fun document(
        lastUpdatedAt: Long,
        content: Map<Double, StoryStep>,
        commentText: String,
    ) = Document(
        id = "document-1",
        title = "Document",
        content = content,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = null,
        workspaceId = "workspace-1",
        parentId = "root",
        commentConversations = mapOf(
            "conversation-1" to listOf(Comment(id = "comment-1", text = commentText))
        ),
    )
}
