@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentMerger
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.ExperimentalTime

/**
 * UseCase that orchestrates loading documents with merge support from both
 * local database and backend.
 */
class DocumentLoadUseCase(
    private val documentRepository: DocumentRepository,
    private val documentsApi: DocumentsApi,
    private val documentMerger: DocumentMerger,
    private val authRepository: AuthRepository
) {

    /**
     * Fetches document from backend, merges with local, saves to database, and
     * calls the onMergeComplete callback if there were changes to reload.
     *
     * This should be called in the background after the local document is already
     * displayed to the user.
     *
     * @param documentId The ID of the document to fetch
     * @param workspaceId The workspace ID
     * @param onMergeComplete Callback with the merged document if backend had changes
     */
    suspend fun fetchAndMergeFromBackend(
        documentId: String,
        workspaceId: String,
        currentLocalDocument: (() -> Document?)? = null,
        onMergeComplete: suspend (Document) -> Unit
    ) {
        fun currentEditorDocument(): Document? =
            currentLocalDocument
                ?.invoke()
                ?.takeIf { document ->
                    document.id == documentId && document.workspaceId == workspaceId
                }

        val editorBeforeFetch = currentEditorDocument()
        val backendDocument = fetchFromBackend(documentId, workspaceId) ?: return
        if (backendDocument.id != documentId || backendDocument.workspaceId != workspaceId) return

        val persistedLocal = documentRepository.loadDocumentById(documentId, workspaceId)
        var editorSnapshot = currentEditorDocument()

        while (true) {
            val localDocument = editorSnapshot ?: persistedLocal
            val beforeById =
                editorBeforeFetch?.content?.values?.associateBy { step -> step.id }.orEmpty()
            val afterById =
                editorSnapshot?.content?.values?.associateBy { step -> step.id }.orEmpty()
            val localOverrideStepIds =
                if (editorBeforeFetch != null && editorSnapshot != null) {
                    afterById.keys.filterTo(mutableSetOf()) { stepId ->
                        beforeById[stepId] != afterById[stepId]
                    }
                } else {
                    emptySet()
                }
            val localDeletedStepIds =
                if (editorBeforeFetch != null && editorSnapshot != null) {
                    beforeById.keys - afterById.keys
                } else {
                    emptySet()
                }

            val mergedDocument = documentMerger.merge(
                localDocument,
                backendDocument,
                localOverrideStepIds = localOverrideStepIds,
                localDeletedStepIds = localDeletedStepIds,
            ) ?: return

            val hasChanges = localDocument == null ||
                mergedDocument.content != localDocument.content ||
                mergedDocument.commentConversations != localDocument.commentConversations

            if (!hasChanges) return

            documentRepository.saveDocument(mergedDocument)

            val latestEditor = currentEditorDocument()
            if (latestEditor != editorSnapshot) {
                editorSnapshot = latestEditor
                continue
            }

            onMergeComplete(mergedDocument)
            return
        }
    }

    private suspend fun fetchFromBackend(documentId: String, workspaceId: String): Document? =
        try {
            when (val result = documentsApi.getDocumentById(documentId, workspaceId)) {
                is ResultData.Complete -> result.data
                else -> null
            }
        } catch (e: Exception) {
            // Network error, timeout, etc. - graceful degradation
            null
        }
}
