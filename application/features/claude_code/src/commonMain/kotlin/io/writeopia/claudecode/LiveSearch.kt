package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.SearchMatch

/** The search of the app, for Claude Code: what the global search of the app finds. */
fun interface LiveSearch {
    /**
     * The documents of the workspace on screen that match [query], best first, like the global
     * search: by title, then by the words inside them, then what only the backend has.
     */
    suspend fun search(query: String): List<SearchMatch>
}
