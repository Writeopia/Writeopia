package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.claudecode.mcp.SearchMatch
import io.writeopia.claudecode.mcp.SearchReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The same cases as `SearchReportTests.swift`: both apps answer search_documents word for word the same. */
class SearchReportTest {

    private val work = LiveFolder(id = "f-work", name = "Work", parentId = "root")
    private val finance = LiveFolder(id = "f-fin", name = "Finance", parentId = "f-work")
    private val budgets = LiveFolder(id = "f-bud", name = "Budgets 2026", parentId = "root")
    private val folders = listOf(work, finance, budgets)

    @Test
    fun tokensAreTheLowercasedWordsOnce() {
        assertEquals(listOf("budget", "2026", "plan"), SearchReport.tokens("  Budget, 2026 — plan budget!"))
        assertEquals(emptyList(), SearchReport.tokens(" ?! "))
    }

    @Test
    fun shortTextIsKeptWholeWithTheWordsInBold() {
        assertEquals(
            "The **budget** for the next **planning** cycle.",
            SearchReport.excerpt("The budget  for the next\nplanning cycle.", listOf("budget", "plan"))
        )
    }

    @Test
    fun onlyTheStartOfAWordMatches() {
        assertEquals("Unplanned **plans**", SearchReport.excerpt("Unplanned plans", listOf("plan")))
    }

    @Test
    fun longTextIsCutAroundTheFirstMatchAtWordBoundaries() {
        val text = (1..40).joinToString(" ") { "word$it" } + " budget " + (41..80).joinToString(" ") { "word$it" }
        assertEquals(
            "…word33 word34 word35 word36 word37 word38 word39 word40 **budget** word41 word42 word43 word44 " +
                "word45 word46 word47 word48 word49 word50 word51 word52 word53 word54…",
            SearchReport.excerpt(text, listOf("budget"))
        )
    }

    @Test
    fun describesDocumentsWithTheirFolderAndPassage() {
        val matches = listOf(
            SearchMatch("d1", "Q3 Budget", "f-fin", null),
            SearchMatch("d2", "Ideas", "root", "We should budget the trip."),
            SearchMatch("d1", "Q3 Budget", "f-fin", "duplicate"),
            SearchMatch("d3", "", null, "Budget review"),
        )

        assertEquals(
            """
            Documents matching "budget", best first (3):

            1. Q3 Budget (document_id: d1)
               In: Work / Finance (folder_id: f-fin)
               Found by its title.

            2. Ideas (document_id: d2)
               In: the top level
               "We should **budget** the trip."

            3. Untitled (document_id: d3)
               In: the top level
               "**Budget** review"

            Folders matching "budget":
            - Budgets 2026 (folder_id: f-bud), in the top level

            Read a whole document with read_document. Pass a document_id to append_to_document, add_tags or set_header_image, and a folder_id to create_document.
            """.trimIndent(),
            SearchReport.describe(" budget ", matches, folders, limit = 10)
        )
    }

    @Test
    fun tellsWhenTheListIsCut() {
        val matches = (1..4).map { SearchMatch("d$it", "Plan $it", "f-work", null) }

        val text = SearchReport.describe("plan", matches, folders, limit = 2)

        assertTrue(text.startsWith("Documents matching \"plan\", best first (4):\n\n1. Plan 1 (document_id: d1)"), text)
        assertTrue(text.contains("\n\n2. Plan 2 (document_id: d2)\n   In: Work (folder_id: f-work)\n   Found by its title."), text)
        assertTrue(!text.contains("Plan 3"), text)
        assertTrue(
            text.contains("\n\nShowing the first 2 of 4: add words to narrow the search, or raise limit (up to 25)."),
            text
        )
    }

    @Test
    fun onlyFoldersMatch() {
        assertEquals(
            """
            No document matches "finance".

            Folders matching "finance":
            - Finance (folder_id: f-fin), in Work (folder_id: f-work)

            Read a whole document with read_document. Pass a document_id to append_to_document, add_tags or set_header_image, and a folder_id to create_document.
            """.trimIndent(),
            SearchReport.describe("finance", emptyList(), folders, limit = 10)
        )
    }

    @Test
    fun nothingMatches() {
        assertEquals(
            "Nothing in the workspace matches \"zebra\". The search looks for every word at the start of a word " +
                "(\"plan\" finds \"planning\"), in the titles and the text of the documents. Try fewer or other words.",
            SearchReport.describe("zebra", emptyList(), folders, limit = 10)
        )
    }

    @Test
    fun theToolSearchesThroughTheApp() = runTest {
        var asked: String? = null
        val handler = McpHandler(
            LiveDocuments(
                openTimeout = 50.milliseconds,
                isLoading = MutableStateFlow(false),
                search = { query ->
                    asked = query
                    listOf(SearchMatch("d1", "Trip", "root", "Book the trip"))
                },
            ),
            serverVersion = "test"
        )

        suspend fun call(arguments: String) = Json.parseToJsonElement(
            handler.handle(
                """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_documents","arguments":$arguments}}"""
            )!!
        ).jsonObject["result"]!!.jsonObject

        val found = call("""{"query":"trip"}""")
        assertEquals("trip", asked)
        assertTrue(!found["isError"]!!.jsonPrimitive.boolean)
        assertTrue(
            found["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
                .contains("1. Trip (document_id: d1)\n   In: the top level\n   \"Book the **trip**\"")
        )

        assertTrue(call("{}")["isError"]!!.jsonPrimitive.boolean)
        assertTrue(call("""{"query":" ?! "}""")["isError"]!!.jsonPrimitive.boolean)
    }
}
