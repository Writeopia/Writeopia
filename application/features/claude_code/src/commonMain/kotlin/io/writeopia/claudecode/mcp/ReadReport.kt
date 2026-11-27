package io.writeopia.claudecode.mcp

/** A document to read whole: [markdown] is its content, written by the app. */
data class ReadDocument(
    val id: String,
    val title: String,
    val parentId: String?,
    val markdown: String,
)

/**
 * The answer of read_document. Mirrored word for word by `ReadReport.swift`, the Markdown of the
 * content aside (each app writes its own). `ReadReportTest.kt` and `ReadReportTests.swift` hold
 * the same cases.
 */
object ReadReport {
    const val PAGE_LENGTH = 20_000

    fun describe(document: ReadDocument, folders: List<LiveFolder>, offset: Int): String = buildString {
        val title = document.title.ifBlank { "Untitled" }
        val place = SearchReport.place(document.parentId, folders.associateBy { it.id })
        append("\"$title\" (document_id: ${document.id}), in $place.")

        val text = document.markdown.trim()
        val from = offset.coerceAtLeast(0)
        when {
            text.isEmpty() -> append("\n\nContent: empty.")

            from >= text.length -> append(
                "\n\nContent: nothing from offset $from: the document has ${text.length} characters."
            )

            from == 0 && text.length <= PAGE_LENGTH -> append("\n\nContent (Markdown):\n$text")

            else -> {
                val to = pageEnd(text, from)
                append("\n\nContent (Markdown), characters $from to $to of ${text.length}:\n")
                append(text.substring(from, to).trimEnd())
                if (to < text.length) {
                    append(
                        "\n\nThe document goes on: call ${McpHandler.READ} with this document_id and offset $to " +
                            "to read the next part."
                    )
                }
            }
        }
    }

    /** Where the part from [from] ends: [PAGE_LENGTH] characters on, back at a line break when one is near. */
    private fun pageEnd(text: String, from: Int): Int {
        val end = (from + PAGE_LENGTH).coerceAtMost(text.length)
        if (end == text.length) return end
        val lineBreak = text.lastIndexOf('\n', end - 1)
        return if (lineBreak > from + PAGE_LENGTH / 2) lineBreak + 1 else end
    }
}
