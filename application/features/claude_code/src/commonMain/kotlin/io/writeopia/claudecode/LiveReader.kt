package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.ReadDocument
import io.writeopia.sdk.export.DocumentToMarkdown
import io.writeopia.sdk.repository.DocumentRepository

/** Reads whole documents for Claude Code, without opening them in the editor. */
fun interface LiveReader {
    /** The document with this id as Markdown, or null when there is none (or it was deleted). */
    suspend fun read(documentId: String): ReadDocument?
}

/** The documents of the app's database, the ones the editor opens. */
class RepositoryLiveReader(
    private val documentRepository: () -> DocumentRepository,
    private val workspaceId: suspend () -> String,
) : LiveReader {

    override suspend fun read(documentId: String): ReadDocument? {
        val document = documentRepository().loadDocumentById(documentId, workspaceId())
            ?.takeIf { !it.deleted }
            ?: return null
        return ReadDocument(
            id = document.id,
            title = document.title,
            parentId = document.parentId,
            markdown = DocumentToMarkdown.parse(document.content.filterValues { step -> !step.ephemeral }),
        )
    }
}
