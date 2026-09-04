@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.usecase

import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.api.GenerateSummaryApiResult
import io.writeopia.core.folders.sync.DocumentSyncService
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.request.DocumentSyncInfo
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import io.writeopia.sdk.serialization.response.UnsyncedDocumentInfo
import kotlin.time.ExperimentalTime

/**
 * Result type for the generate summary operation.
 */
sealed class GenerateSummaryResult {
    data class Success(val document: Document) : GenerateSummaryResult()

    data class NeedsSync(val unsyncedDocuments: List<UnsyncedDocumentInfo>) : GenerateSummaryResult()

    data object GenAiUnavailable : GenerateSummaryResult()

    data class Error(val message: String) : GenerateSummaryResult()
}

/**
 * UseCase for generating summary documents from multiple source documents.
 * Handles sync verification and automatic retry after sync.
 */
class GenerateSummaryUseCase(
    private val documentsApi: DocumentsApi,
    private val documentRepository: DocumentRepository
) {

    /**
     * Generates a summary document from the specified documents.
     *
     * If documents are not synced, returns NeedsSync with the list of documents
     * that need to be synced before the summary can be generated.
     *
     * @param documentIds List of document IDs to summarize
     * @param targetFolderId The folder where the summary document will be created
     * @param workspaceId The workspace ID
     * @param summaryTitle Optional custom title for the summary document
     * @param model Optional AI model override
     * @return GenerateSummaryResult
     */
    suspend fun generateSummary(
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
        summaryTitle: String? = null,
        model: String? = null
    ): GenerateSummaryResult {
        // Load documents to get their lastSyncedAt values
        val documents = documentIds.mapNotNull { documentId ->
            documentRepository.loadDocumentById(documentId, workspaceId)
        }

        if (documents.size != documentIds.size) {
            val missingIds = documentIds.filter { id -> documents.none { it.id == id } }
            return GenerateSummaryResult.Error("Documents not found: ${missingIds.joinToString()}")
        }

        // Build sync info with client's lastSyncedAt values
        val documentSyncInfos = documents.map { document ->
            DocumentSyncInfo(
                documentId = document.id,
                lastSyncedAt = document.lastSyncedAt?.toEpochMilliseconds()
            )
        }

        // Call the API
        val result = documentsApi.generateSummary(
            documents = documentSyncInfos,
            targetFolderId = targetFolderId,
            workspaceId = workspaceId,
            summaryTitle = summaryTitle,
            model = model
        )

        return when (result) {
            is GenerateSummaryApiResult.Success -> {
                // Save the new document to local repository
                documentRepository.saveDocument(result.document)
                GenerateSummaryResult.Success(result.document)
            }
            is GenerateSummaryApiResult.NeedsSync -> {
                GenerateSummaryResult.NeedsSync(result.unsyncedDocuments)
            }
            is GenerateSummaryApiResult.GenAiUnavailable -> {
                GenerateSummaryResult.GenAiUnavailable
            }
            is GenerateSummaryApiResult.Error -> {
                GenerateSummaryResult.Error(result.message)
            }
        }
    }

    /**
     * Generates a summary document, automatically syncing documents if needed.
     *
     * This method will:
     * 1. Attempt to generate the summary
     * 2. If documents need sync, call the provided sync function for each document
     * 3. Retry the summary generation after sync
     *
     * @param documentIds List of document IDs to summarize
     * @param targetFolderId The folder where the summary document will be created
     * @param workspaceId The workspace ID
     * @param summaryTitle Optional custom title for the summary document
     * @param model Optional AI model override
     * @param syncDocument Function to sync a single document by ID, returns true if sync succeeded
     * @param maxRetries Maximum number of retry attempts after sync (default: 1)
     * @return GenerateSummaryResult
     */
    suspend fun generateSummaryWithAutoSync(
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
        summaryTitle: String? = null,
        model: String? = null,
        syncDocument: suspend (documentId: String) -> Boolean,
        maxRetries: Int = 1
    ): GenerateSummaryResult {
        repeat(maxRetries + 1) { attempt ->
            val result = generateSummary(
                documentIds = documentIds,
                targetFolderId = targetFolderId,
                workspaceId = workspaceId,
                summaryTitle = summaryTitle,
                model = model
            )

            when {
                result !is GenerateSummaryResult.NeedsSync -> return result
                attempt == maxRetries -> return result
                else -> {
                    // Sync each unsynced document
                    val allSynced = result.unsyncedDocuments.all { unsyncedDoc ->
                        syncDocument(unsyncedDoc.documentId)
                    }

                    if (!allSynced) {
                        return GenerateSummaryResult.Error("Failed to sync some documents")
                    }
                }
            }
        }

        error("Unreachable: all code paths return from within the loop")
    }

    /**
     * Generates a summary document, automatically syncing documents if needed.
     * Uses the provided DocumentSyncService to sync outdated documents.
     *
     * This is a convenience method that wraps generateSummaryWithAutoSync
     * with a DocumentSyncService-based sync function.
     *
     * @param documentIds List of document IDs to summarize
     * @param targetFolderId The folder where the summary document will be created
     * @param workspaceId The workspace ID
     * @param summaryTitle Optional custom title for the summary document
     * @param model Optional AI model override
     * @param documentSyncService Service to sync documents
     * @param maxRetries Maximum number of retry attempts after sync (default: 1)
     * @return GenerateSummaryResult
     */
    suspend fun generateSummaryWithAutoSync(
        documentIds: List<String>,
        targetFolderId: String,
        workspaceId: String,
        summaryTitle: String? = null,
        model: String? = null,
        documentSyncService: DocumentSyncService,
        maxRetries: Int = 1
    ): GenerateSummaryResult =
        generateSummaryWithAutoSync(
            documentIds = documentIds,
            targetFolderId = targetFolderId,
            workspaceId = workspaceId,
            summaryTitle = summaryTitle,
            model = model,
            syncDocument = { documentId ->
                documentSyncService.syncDocument(documentId, workspaceId)
            },
            maxRetries = maxRetries
        )

    companion object {
        fun create(
            documentsApi: DocumentsApi,
            documentRepository: DocumentRepository
        ): GenerateSummaryUseCase = GenerateSummaryUseCase(
            documentsApi = documentsApi,
            documentRepository = documentRepository
        )

        /**
         * Creates a GenerateSummaryUseCase with a pre-configured DocumentSyncService.
         *
         * @param documentsApi API for document operations
         * @param documentRepository Repository for local document operations
         * @param syncApi API for syncing story steps with the backend
         * @return Pair of (GenerateSummaryUseCase, DocumentSyncService) for use together
         */
        fun createWithSyncService(
            documentsApi: DocumentsApi,
            documentRepository: DocumentRepository,
            syncApi: suspend (StoryStepSyncRequest) -> StoryStepSyncResponse
        ): Pair<GenerateSummaryUseCase, DocumentSyncService> {
            val useCase = GenerateSummaryUseCase(
                documentsApi = documentsApi,
                documentRepository = documentRepository
            )
            val syncService = DocumentSyncService.create(
                documentRepository = documentRepository,
                syncApi = syncApi
            )
            return useCase to syncService
        }
    }
}
