package io.writeopia.claudecode.mcp

/**
 * A document the search of the app found. [text] is the step the words were found in, when it
 * was found by them; null when it was found by its title. [parentId] is its folder, null when
 * unknown (found on the backend only).
 */
data class SearchMatch(
    val documentId: String,
    val title: String,
    val parentId: String?,
    val text: String?,
)

/**
 * The answer of search_documents. Mirrored word for word by `SearchReport.swift`: the same
 * words of the query, the same excerpt around them, the same text. `SearchReportTest.kt` and
 * `SearchReportTests.swift` hold the same cases.
 */
object SearchReport {
    const val DEFAULT_LIMIT = 10
    const val MAX_LIMIT = 25
    const val EXCERPT_LENGTH = 160
    private const val EXCERPT_LEAD = 60
    private const val MAX_TOKENS = 8
    private const val ROOT = "root"

    /** The words of [query], lowercased, each looked for at the start of a word. */
    fun tokens(query: String): List<String> =
        words(query).map { (start, end) -> query.substring(start, end).lowercase() }.distinct().take(MAX_TOKENS)

    fun describe(query: String, matches: List<SearchMatch>, folders: List<LiveFolder>, limit: Int): String {
        val tokens = tokens(query)
        val byId = folders.associateBy { it.id }
        val shownLimit = limit.coerceIn(1, MAX_LIMIT)
        val documents = matches.distinctBy { it.documentId }
        val matchingFolders = folders
            .filter { folder -> matchesAll(folder.name, tokens) }
            .sortedBy { folder -> folder.name.lowercase() }
        val quoted = "\"${query.trim()}\""

        if (documents.isEmpty() && matchingFolders.isEmpty()) {
            return "Nothing in the workspace matches $quoted. The search looks for every word at the start " +
                "of a word (\"plan\" finds \"planning\"), in the titles and the text of the documents. Try " +
                "fewer or other words."
        }

        return buildString {
            if (documents.isEmpty()) {
                append("No document matches $quoted.")
            } else {
                val shown = documents.take(shownLimit)
                append("Documents matching $quoted, best first (${documents.size}):")
                shown.forEachIndexed { index, match ->
                    append("\n\n${index + 1}. ${match.title.ifBlank { "Untitled" }} (document_id: ${match.documentId})")
                    append("\n   In: ${place(match.parentId, byId)}")
                    val text = match.text?.takeIf { it.isNotBlank() }
                    append(
                        if (text == null) {
                            "\n   Found by its title."
                        } else {
                            "\n   \"${excerpt(text, tokens)}\""
                        }
                    )
                }
                if (documents.size > shown.size) {
                    append(
                        "\n\nShowing the first ${shown.size} of ${documents.size}: add words to narrow the " +
                            "search, or raise limit (up to $MAX_LIMIT)."
                    )
                }
            }

            if (matchingFolders.isNotEmpty()) {
                append("\n\nFolders matching $quoted:")
                matchingFolders.forEach { folder ->
                    append("\n- ${folder.name} (folder_id: ${folder.id}), in ${place(folder.parentId, byId)}")
                }
            }

            append(
                "\n\nRead a whole document with ${McpHandler.READ}. Pass a document_id to " +
                    "${McpHandler.APPEND}, ${McpHandler.ADD_TAGS} or ${McpHandler.SET_HEADER_IMAGE}, and a " +
                    "folder_id to ${McpHandler.CREATE}."
            )
        }
    }

    /**
     * [text] on one line, cut to about [EXCERPT_LENGTH] characters around the first word that
     * matched (at word boundaries, "…" where cut), with the matching words in **bold**.
     */
    fun excerpt(text: String, tokens: List<String>): String {
        val clean = oneLine(text)
        val words = words(clean)
        val hits = words.filter { (start, end) -> isHit(clean.substring(start, end), tokens) }

        var from = 0
        var to = clean.length
        if (clean.length > EXCERPT_LENGTH) {
            val first = hits.firstOrNull()?.first ?: 0
            from = (first - EXCERPT_LEAD).coerceAtLeast(0)
            if (from > 0) from = words.firstOrNull { (start, _) -> start >= from }?.first ?: from
            to = (from + EXCERPT_LENGTH).coerceAtMost(clean.length)
            if (to < clean.length) {
                to = words.lastOrNull { (start, end) -> end <= to && start > from }?.second ?: to
            }
        }

        return buildString {
            if (from > 0) append("…")
            var cursor = from
            hits.filter { (start, end) -> start >= from && end <= to }.forEach { (start, end) ->
                append(clean, cursor, start)
                append("**").append(clean, start, end).append("**")
                cursor = end
            }
            append(clean, cursor, to)
            if (to < clean.length) append("…")
        }
    }

    /** Where a document or folder is: its folders from the top level down, or the top level. */
    internal fun place(parentId: String?, byId: Map<String, LiveFolder>): String {
        val path = mutableListOf<LiveFolder>()
        var id = parentId
        while (id != null && id != ROOT && path.size < byId.size) {
            val folder = byId[id] ?: break
            path.add(0, folder)
            id = folder.parentId
        }
        return if (path.isEmpty()) {
            "the top level"
        } else {
            path.joinToString(" / ") { it.name } + " (folder_id: ${path.last().id})"
        }
    }

    /** Every token starts a word of [text]. */
    private fun matchesAll(text: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val textWords = words(text).map { (start, end) -> text.substring(start, end).lowercase() }
        return tokens.all { token -> textWords.any { word -> word.startsWith(token) } }
    }

    private fun isHit(word: String, tokens: List<String>): Boolean {
        val lower = word.lowercase()
        return tokens.any { token -> lower.startsWith(token) }
    }

    /** [text] with every run of whitespace (line breaks too) made one space, trimmed. */
    private fun oneLine(text: String): String = buildString {
        var space = false
        text.forEach { char ->
            if (char.isWhitespace()) {
                space = isNotEmpty()
            } else {
                if (space) append(' ')
                space = false
                append(char)
            }
        }
    }

    /** The start and end of each run of letters and digits. */
    private fun words(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = -1
        text.forEachIndexed { index, char ->
            if (char.isLetterOrDigit()) {
                if (start < 0) start = index
            } else if (start >= 0) {
                ranges.add(start to index)
                start = -1
            }
        }
        if (start >= 0) ranges.add(start to text.length)
        return ranges
    }
}
