package io.writeopia.api.embeddings

import io.writeopia.sdk.models.document.Document

/** The text of a document as the AI reads it: its title apart from its body. */
data class PlainText(val title: String, val body: String) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
}

/** Extracts the readable text of documents, the same way for embeddings and for summaries. */
object DocumentPlainText {

    /** The story step types whose text is read. */
    val TEXT_TYPES = setOf("title", "message", "check_item", "unordered_list_item", "code_block")

    private const val TITLE_TYPE = "title"

    /**
     * How much of a document is embedded. Embedding models read about 2 000 tokens; the title
     * comes first so it always makes the cut.
     */
    const val EMBEDDING_INPUT_LIMIT = 8_000

    /** The title (from metadata, else from the title step) and the body (every other text step, in order). */
    fun of(document: Document): PlainText {
        val steps = document.content.entries.sortedBy { it.key }.map { it.value }
        val titleStepText = steps.firstOrNull { step -> step.type.name == TITLE_TYPE }?.text
        val title = document.title.ifBlank { titleStepText ?: "" }.trim()
        val body = steps
            .filter { step -> step.type.name in TEXT_TYPES && step.type.name != TITLE_TYPE }
            .mapNotNull { step -> step.text?.takeIf { text -> text.isNotBlank() } }
            .joinToString(separator = "\n")

        return PlainText(title = title, body = body)
    }

    /** What is sent to the embedding model for a document: title, blank line, body, capped. */
    fun embeddingInput(title: String, body: String): String =
        buildString {
            if (title.isNotBlank()) append(title.trim())
            if (body.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(body)
            }
        }.take(EMBEDDING_INPUT_LIMIT)

    fun embeddingInput(text: PlainText): String = embeddingInput(text.title, text.body)

    /**
     * The text of several documents for a prompt: each one headed by its title, then every text
     * step in order (the title step included), documents separated by a rule. Every document
     * contributes at least its heading so all of them are represented.
     */
    fun promptText(documents: List<Document>): String =
        documents.joinToString(separator = "\n\n---\n\n") { document ->
            val parts = mutableListOf("# ${document.title}")
            document.content.entries.sortedBy { it.key }.forEach { (_, step) ->
                val text = step.text
                if (step.type.name in TEXT_TYPES && !text.isNullOrBlank()) parts.add(text)
            }
            parts.joinToString(separator = "\n")
        }
}
