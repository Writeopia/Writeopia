package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.models.document.Document
import kotlinx.coroutines.flow.Flow

expect object MarkdownToDocument {

    fun readDocuments(files: List<String>, userId: String, parentId: String): Flow<Document>

    fun readMarkdown(markdownText: String, userId: String, parentId: String): Document?
}
