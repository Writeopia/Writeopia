package io.writeopia.core.folders.repository.folder

import io.writeopia.sdk.utils.iterables.Rank
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
import io.writeopia.sdk.models.story.StoryStepTombstone
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
            Rank.at(0) to StoryStep(
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

    @Test
    fun `backend document with mismatched identity is ignored`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val backend = document(
            lastUpdatedAt = 2,
            content = emptyMap(),
            commentText = "Backend comment",
        ).copy(workspaceId = "other-workspace")

        coEvery { documentRepository.loadDocumentById("document-1", "workspace-1") } returns null
        coEvery { documentsApi.getDocumentById("document-1", "workspace-1") } returns
            ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )

        useCase.fetchAndMergeFromBackend("document-1", "workspace-1") {
            error("Mismatched backend document must not be merged")
        }

        coVerify(exactly = 0) { documentRepository.saveDocument(any()) }
    }

    @Test
    fun `tag-only backend change is saved and reported`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val content = mapOf(
            Rank.at(0) to StoryStep(id = "step-1", type = StoryTypes.TEXT.type, text = "Text", lastUpdatedAt = 1)
        )
        val local = document(lastUpdatedAt = 1, content = content, commentText = "Same", tagIds = emptySet())
        val backend = document(lastUpdatedAt = 2, content = content, commentText = "Same", tagIds = setOf("tag-1"))

        coEvery { documentRepository.loadDocumentById(local.id, local.workspaceId) } returns local
        coEvery { documentsApi.getDocumentById(local.id, local.workspaceId) } returns ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )
        var reloaded: Document? = null

        useCase.fetchAndMergeFromBackend(local.id, local.workspaceId) { merged -> reloaded = merged }

        assertEquals(setOf("tag-1"), assertNotNull(reloaded).tagIds)
        coVerify(exactly = 1) { documentRepository.saveDocument(match { saved -> saved.tagIds == setOf("tag-1") }) }
    }

    @Test
    fun `a step deleted here is not brought back from the backend`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        // The comment span keeps the conversation of document() alive through the merge.
        val kept = StoryStep(
            id = "step-1",
            type = StoryTypes.TEXT.type,
            text = "Kept",
            spans = setOf(SpanInfo.create(0, 4, Span.COMMENT, "conversation-1")),
            lastUpdatedAt = 1,
        )
        val deletedHere = StoryStep(id = "step-2", type = StoryTypes.AI_ANSWER.type, text = "Gone", lastUpdatedAt = 5)
        val local = document(lastUpdatedAt = 1, content = mapOf(Rank.at(0) to kept), commentText = "Same")
        val backend = document(lastUpdatedAt = 1, content = mapOf(Rank.at(0) to kept, Rank.at(1) to deletedHere), commentText = "Same")

        coEvery { documentRepository.loadDocumentById(local.id, local.workspaceId) } returns local
        coEvery { documentRepository.loadStoryStepTombstones(local.workspaceId) } returns listOf(
            StoryStepTombstone(stepId = "step-2", documentId = local.id, workspaceId = local.workspaceId, deletedAt = 6)
        )
        coEvery { documentsApi.getDocumentById(local.id, local.workspaceId) } returns ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )

        useCase.fetchAndMergeFromBackend(local.id, local.workspaceId) {
            error("The backend copy only differs by the step deleted here: nothing to reload")
        }

        coVerify(exactly = 0) { documentRepository.saveDocument(any()) }
    }

    @Test
    fun `a newer backend parent does not bring back a nested step deleted here`() = runBlocking {
        val documentRepository = mockk<DocumentRepository>(relaxed = true)
        val documentsApi = mockk<DocumentsApi>()
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val keptChild = StoryStep(id = "child-1", type = StoryTypes.TEXT.type, text = "Kept", lastUpdatedAt = 1)
        val deletedChild = StoryStep(id = "child-2", type = StoryTypes.TEXT.type, text = "Gone", lastUpdatedAt = 1)
        val localParent = StoryStep(id = "parent", type = StoryTypes.TEXT.type, steps = listOf(keptChild), lastUpdatedAt = 1)
        val backendParent = localParent.copy(steps = listOf(keptChild, deletedChild), lastUpdatedAt = 2)
        val local = document(lastUpdatedAt = 1, content = mapOf(Rank.at(0) to localParent), commentText = "Same")
        val backend = document(lastUpdatedAt = 2, content = mapOf(Rank.at(0) to backendParent), commentText = "Same")

        coEvery { documentRepository.loadDocumentById(local.id, local.workspaceId) } returns local
        coEvery { documentRepository.loadStoryStepTombstones(local.workspaceId) } returns listOf(
            StoryStepTombstone(stepId = "child-2", documentId = local.id, workspaceId = local.workspaceId, deletedAt = 3)
        )
        coEvery { documentsApi.getDocumentById(local.id, local.workspaceId) } returns ResultData.Complete(backend)

        val useCase = DocumentLoadUseCase(
            documentRepository = documentRepository,
            documentsApi = documentsApi,
            documentMerger = DocumentMerger(),
            authRepository = authRepository,
        )
        var reloaded: Document? = null

        useCase.fetchAndMergeFromBackend(local.id, local.workspaceId) { merged -> reloaded = merged }

        val parent = assertNotNull(reloaded).content.getValue(Rank.at(0))
        assertEquals(listOf("child-1"), parent.steps.map { step -> step.id })
        coVerify(exactly = 1) {
            documentRepository.saveDocument(
                match { saved -> saved.content.getValue(Rank.at(0)).steps.none { step -> step.id == "child-2" } }
            )
        }
    }

    private fun document(
        lastUpdatedAt: Long,
        content: Map<String, StoryStep>,
        commentText: String,
        tagIds: Set<String> = emptySet(),
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
        tagIds = tagIds,
    )
}
