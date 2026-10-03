@file:OptIn(ExperimentalTime::class)

package io.writeopia.notemenu.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.LocalAiRepository
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.api.GenerateSummaryApiResult
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentsSummarizerTest {

    private val notesUseCase: NotesUseCase = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val documentsApi: DocumentsApi = mockk()
    private val localAiRepository: LocalAiRepository = mockk()

    private val userId = "user"
    private val workspaceId = "workspace"

    private fun document(id: String) = Document(
        id = id,
        title = "Doc $id",
        createdAt = Clock.System.now(),
        lastUpdatedAt = Clock.System.now(),
        lastSyncedAt = null,
        workspaceId = workspaceId,
        parentId = "root"
    )

    private fun summarizer(localAi: LocalAiRepository? = localAiRepository) = DocumentsSummarizer(
        notesUseCase = notesUseCase,
        authRepository = authRepository,
        documentsApi = documentsApi,
        localAiRepository = localAi,
    )

    @Test
    fun `without a local AI repository the cloud is selected`() = runTest {
        assertEquals(DocumentsSummarizer.Source.Cloud, summarizer(localAi = null).resolveSource(userId))
    }

    @Test
    fun `local AI without a model falls back to the cloud`() = runTest {
        coEvery { localAiRepository.getConfiguredUrl(userId) } returns "http://localhost:11434"
        coEvery { localAiRepository.getSelectedModel(userId) } returns null

        assertEquals(DocumentsSummarizer.Source.Cloud, summarizer().resolveSource(userId))
    }

    @Test
    fun `local AI with url and model is selected`() = runTest {
        coEvery { localAiRepository.getConfiguredUrl(userId) } returns " http://localhost:11434 "
        coEvery { localAiRepository.getSelectedModel(userId) } returns "llama3"

        assertEquals(
            DocumentsSummarizer.Source.Local(url = "http://localhost:11434", model = "llama3"),
            summarizer().resolveSource(userId)
        )
    }

    @Test
    fun `cloud summary sends the documents, saves the result and skips local AI`() = runTest {
        val documents = listOf(document("1"), document("2"))
        val summary = document("summary")

        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { notesUseCase.loadDocumentsByIds(listOf("1", "2"), workspaceId) } returns documents
        coEvery { documentsApi.sendDocuments(documents, workspaceId) } returns ResultData.Complete(Unit)
        coEvery {
            documentsApi.generateSummary(any(), "folder", workspaceId, null, null, true)
        } returns GenerateSummaryApiResult.Success(summary)

        val result = summarizer(localAi = null).summarize(
            documentIds = listOf("1", "2"),
            targetFolderId = "folder",
            workspaceId = workspaceId,
            userId = userId
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { notesUseCase.saveDocumentDb(summary) }
        coVerify(exactly = 0) { localAiRepository.generateCompleteSummary(any(), any(), any(), any()) }
    }

    @Test
    fun `cloud summary fails when the user is signed out`() = runTest {
        coEvery { authRepository.isLoggedIn() } returns false

        val result = summarizer(localAi = null).summarize(
            documentIds = listOf("1"),
            targetFolderId = "folder",
            workspaceId = workspaceId,
            userId = userId
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { documentsApi.generateSummary(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cloud summary fails for the disconnected workspace`() = runTest {
        coEvery { authRepository.isLoggedIn() } returns true

        val result = summarizer(localAi = null).summarize(
            documentIds = listOf("1"),
            targetFolderId = "folder",
            workspaceId = Workspace.disconnectedWorkspace().id,
            userId = userId
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { documentsApi.generateSummary(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cloud summary reports the backend error`() = runTest {
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { notesUseCase.loadDocumentsByIds(any(), workspaceId) } returns emptyList()
        coEvery {
            documentsApi.generateSummary(any(), any(), any(), any(), any(), any())
        } returns GenerateSummaryApiResult.GenAiUnavailable

        val result = summarizer(localAi = null).summarize(
            documentIds = listOf("1"),
            targetFolderId = "folder",
            workspaceId = workspaceId,
            userId = userId
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { notesUseCase.saveDocumentDb(any()) }
    }

    @Test
    fun `configured local AI is used instead of the cloud`() = runTest {
        coEvery { localAiRepository.getConfiguredUrl(userId) } returns "http://localhost:11434"
        coEvery { localAiRepository.getSelectedModel(userId) } returns "llama3"
        coEvery { notesUseCase.loadDocumentsByIds(listOf("1"), workspaceId) } returns listOf(document("1"))
        coEvery {
            localAiRepository.generateCompleteSummary("llama3", any(), "http://localhost:11434", true)
        } returns "# Summary\n\nA short summary."
        coEvery { authRepository.isLoggedIn() } returns false

        val result = summarizer().summarize(
            documentIds = listOf("1"),
            targetFolderId = "folder",
            workspaceId = workspaceId,
            userId = userId
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { notesUseCase.saveDocumentDb(match { it.parentId == "folder" }) }
        coVerify(exactly = 0) { documentsApi.generateSummary(any(), any(), any(), any(), any(), any()) }
    }
}
