package io.writeopia.writingagent.folders

import io.writeopia.ai.AiStreaming
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskStatus
import io.writeopia.ai.task.AiTaskType
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.di.ApiLogger
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.writingagent.actions.AiTextWriter
import io.writeopia.writingagent.actions.AiTextWriter.askOneLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Files the open document with the app's folder repository. The new folder is created at the
 * root of the workspace, named by the AI the user picked, as a task the editor's indicator shows.
 */
class WriteopiaDocumentFolders(
    private val documentId: () -> String,
    private val documentParentId: () -> String,
    private val resolveWorkspaceId: suspend () -> String,
    private val folderRepository: FolderRepository,
    private val moveDocument: suspend (documentId: String, folderId: String) -> Unit,
    private val resolveAi: suspend () -> AiStreaming?,
    private val aiTaskManager: AiTaskManager? = null,
    private val taskIdPrefix: () -> String = { "writing-agent" },
    private val log: (String) -> Unit = { message -> ApiLogger.log("[WritingAgent] $message") },
) : DocumentFolders {

    override suspend fun currentFolderId(): String = documentParentId()

    override suspend fun workspaceId(): String = resolveWorkspaceId()

    override suspend fun candidates(limit: Int): List<FolderCandidate> =
        folderRepository.getFolderByParentId(Folder.ROOT_PATH, workspaceId())
            .filter { folder -> !folder.deleted && folder.title.isNotBlank() }
            .take(limit)
            .map { folder -> FolderCandidate(id = folder.id, name = folder.title) }

    override suspend fun moveTo(folderId: String) {
        log("moving document ${documentId()} to folder $folderId")
        moveDocument(documentId(), folderId)
    }

    override suspend fun createFolderAndMove(documentTitle: String, documentText: String): Result<String> {
        val taskManager = aiTaskManager
        if (taskManager == null) return createAndMove(documentTitle, documentText)

        // The task runs on the manager's own scope; the dialog waits here for its outcome.
        val outcome = CompletableDeferred<Result<String>>()
        val taskId = "${taskIdPrefix()}-${GenerateId.generate()}"
        taskManager.enqueueTask(
            id = taskId,
            type = AiTaskType.TEXT_GENERATION,
            description = "Creating a folder for the document..."
        ) {
            val result = runCatching { createAndMove(documentTitle, documentText).getOrThrow() }
            outcome.complete(result)
            result.map { }
        }

        return coroutineScope {
            // A task cancelled while still queued never runs, so nothing would complete the
            // outcome: the task list says when that happens.
            val watcher = launch {
                taskManager.tasks.first { tasks ->
                    val task = tasks.firstOrNull { it.id == taskId }
                    task == null || task.status == AiTaskStatus.CANCELLED
                }
                outcome.complete(Result.failure(Exception("Creating the folder was cancelled")))
            }
            val result = outcome.await()
            watcher.cancel()
            result
        }
    }

    private suspend fun createAndMove(documentTitle: String, documentText: String): Result<String> {
        val ai = resolveAi() ?: return Result.failure(Exception(AiTextWriter.NO_AI_MESSAGE))
        val context = "Title: $documentTitle\n\n$documentText"

        return ai.askOneLine(FOLDER_NAME_PROMPT, context)
            .mapCatching { name -> name.ifBlank { throw Exception("The AI answered no folder name") } }
            .onSuccess { name ->
                val folder = Folder.fromName(name, workspaceId())
                log("creating folder \"$name\" (${folder.id}) for document ${documentId()}")
                folderRepository.createFolder(folder)
                moveTo(folder.id)
            }
            .onFailure { log("creating a folder failed: ${it.message}") }
    }

    companion object {
        const val FOLDER_NAME_PROMPT =
            "Name a folder that would hold the following document and others like it: a broad " +
                "category of at most three words, like \"Meetings\", \"Recipes\" or \"Project " +
                "Atlas\", not a title for this one document. Answer only with the name: no " +
                "quotes, no Markdown, no punctuation. Use the language of the text."
    }
}
