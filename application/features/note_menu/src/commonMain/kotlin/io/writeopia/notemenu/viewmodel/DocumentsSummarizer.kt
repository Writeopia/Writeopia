package io.writeopia.notemenu.viewmodel

import io.writeopia.LocalAiRepository
import io.writeopia.ai.AiClients
import io.writeopia.ai.ResolvedAi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.api.GenerateSummaryApiResult
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.export.DocumentToMarkdown
import io.writeopia.sdk.import.markdown.MarkdownToDocument
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.request.DocumentSyncInfo

/**
 * Summarizes a set of documents into a new one, using the AI the user selected.
 *
 * [AiClients] resolves the provider: the local AI (Ollama) when picked and configured, which only
 * happens on desktop, or the cloud AI of the backend, which is the only option on the phones. Each
 * falls back to the other when it isn't ready.
 */
internal class DocumentsSummarizer(
    private val notesUseCase: NotesUseCase,
    private val authRepository: AuthRepository,
    private val documentsApi: DocumentsApi,
    private val aiClients: AiClients?,
    private val documentToMarkdown: DocumentToMarkdown = DocumentToMarkdown,
) {

    suspend fun summarize(
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
        userId: String,
    ): Result<Unit> =
        when (val ai = aiClients?.resolve(userId)) {
            is ResolvedAi.Local -> summarizeLocally(ai, documentIds, targetFolderId, workspaceId)
            // The cloud summary has its own endpoint, so it also runs where no AI resolved: the
            // failure then explains that the user has to sign in.
            is ResolvedAi.Cloud, null -> summarizeInCloud(documentIds, targetFolderId, workspaceId)
        }

    private suspend fun summarizeLocally(
        ai: ResolvedAi.Local,
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
    ): Result<Unit> {
        val localAiRepository: LocalAiRepository = ai.repository

        val documents = notesUseCase.loadDocumentsByIds(documentIds, workspaceId)
        val prompt = buildString {
            documents.forEach { doc ->
                val documentMd = documentToMarkdown.parse(doc.content)

                appendLine("====================================================")
                appendLine(documentMd)
                appendLine("====================================================")
                appendLine()
            }
        }

        val aiPromptResultMd = localAiRepository.generateCompleteSummary(
            model = ai.model,
            prompt = prompt,
            url = ai.url,
            markdownResult = true
        ) ?: return Result.failure(Exception("AI response was empty"))

        val document = MarkdownToDocument.readMarkdown(
            markdownText = aiPromptResultMd,
            parentId = targetFolderId,
            workspaceId = workspaceId,
        ) ?: return Result.failure(Exception("Failed to parse AI response"))

        notesUseCase.saveDocumentDb(document)
        syncToBackend(listOf(document), workspaceId)

        return Result.success(Unit)
    }

    private suspend fun summarizeInCloud(
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
    ): Result<Unit> {
        if (!authRepository.isLoggedIn() || workspaceId == Workspace.disconnectedWorkspace().id) {
            return Result.failure(Exception("Sign in to summarize with cloud AI"))
        }

        // The backend summarizes its own copy of the documents, so the local edits go first.
        val documents = notesUseCase.loadDocumentsByIds(documentIds, workspaceId)
        if (documents.isNotEmpty()) {
            val sent = documentsApi.sendDocuments(documents = documents, workspaceId = workspaceId)
            if (sent is ResultData.Error) {
                return Result.failure(
                    Exception(sent.exception?.message ?: "Could not sync the documents")
                )
            }
        }

        val result = documentsApi.generateSummary(
            documents = documentIds.map { id -> DocumentSyncInfo(documentId = id, lastSyncedAt = null) },
            targetFolderId = targetFolderId,
            workspaceId = workspaceId,
            summaryTitle = null,
            model = null,
            ignoreSyncCheck = true
        )

        return when (result) {
            is GenerateSummaryApiResult.Success -> {
                notesUseCase.saveDocumentDb(result.document)
                Result.success(Unit)
            }

            is GenerateSummaryApiResult.NeedsSync -> Result.failure(Exception("Documents need sync"))
            is GenerateSummaryApiResult.GenAiUnavailable ->
                Result.failure(Exception("Cloud AI is not available"))

            is GenerateSummaryApiResult.Error ->
                Result.failure(Exception(result.message.ifBlank { "Unknown error" }))
        }
    }

    private suspend fun syncToBackend(
        documents: List<Document>,
        workspaceId: String
    ) {
        if (!authRepository.isLoggedIn()) return
        if (authRepository.getUser().tier != Tier.PREMIUM) return
        if (workspaceId == Workspace.disconnectedWorkspace().id) return

        documentsApi.sendDocuments(documents = documents, workspaceId = workspaceId)
    }
}
