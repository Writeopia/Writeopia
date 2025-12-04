package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.models.document.Document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual object MarkdownToDocument {
    actual fun readDocuments(
        files: List<String>,
        parentId: String,
        workspaceId: String
    ): Flow<Document> = flow { }

    actual fun readMarkdown(markdownText: String, parentId: String, workspaceId: String): Document? = null
}
