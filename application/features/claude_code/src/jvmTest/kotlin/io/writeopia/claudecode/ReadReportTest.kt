package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.claudecode.mcp.ReadDocument
import io.writeopia.claudecode.mcp.ReadReport
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

/** The same cases as `ReadReportTests.swift`: both apps answer read_document word for word the same. */
class ReadReportTest {

    private val folders = listOf(
        LiveFolder(id = "f-work", name = "Work", parentId = "root"),
        LiveFolder(id = "f-fin", name = "Finance", parentId = "f-work"),
    )

    @Test
    fun aShortDocumentComesWhole() {
        assertEquals(
            "\"Q3 Budget\" (document_id: d1), in Work / Finance (folder_id: f-fin).\n\n" +
                "Content (Markdown):\n# Q3 Budget\n\n- Rent\n- Food",
            ReadReport.describe(ReadDocument("d1", "Q3 Budget", "f-fin", "\n# Q3 Budget\n\n- Rent\n- Food\n"), folders, 0)
        )
    }

    @Test
    fun anEmptyDocumentAtTheTopLevel() {
        assertEquals(
            "\"Untitled\" (document_id: d2), in the top level.\n\nContent: empty.",
            ReadReport.describe(ReadDocument("d2", " ", "root", "  "), folders, 0)
        )
    }

    @Test
    fun aLongDocumentComesInPartsCutAtLineBreaks() {
        // 30 lines of 1,000 characters (999 + the line break).
        val markdown = (1..30).joinToString("\n") { line -> "$line".padEnd(999, 'x') }
        val document = ReadDocument("d3", "Long", null, markdown)

        val first = ReadReport.describe(document, folders, 0)
        assertTrue(
            first.startsWith("\"Long\" (document_id: d3), in the top level.\n\nContent (Markdown), characters 0 to 20000 of 29999:\n1xxx"),
            first
        )
        assertTrue(
            first.endsWith("\n\nThe document goes on: call read_document with this document_id and offset 20000 to read the next part."),
            first
        )

        val second = ReadReport.describe(document, folders, 20_000)
        assertTrue(second.contains("Content (Markdown), characters 20000 to 29999 of 29999:\n21xxx"), second)
        assertTrue(!second.contains("goes on"), second)

        assertEquals(
            "\"Long\" (document_id: d3), in the top level.\n\nContent: nothing from offset 40000: the document has 29999 characters.",
            ReadReport.describe(document, folders, 40_000)
        )
    }

    @Test
    fun theToolReadsThroughTheApp() = runTest {
        val handler = McpHandler(
            LiveDocuments(
                openTimeout = 50.milliseconds,
                isLoading = MutableStateFlow(false),
                reader = { id -> if (id == "d1") ReadDocument("d1", "Trip", "root", "- Book the trip") else null },
            ),
            serverVersion = "test"
        )

        suspend fun call(arguments: String) = Json.parseToJsonElement(
            handler.handle(
                """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_document","arguments":$arguments}}"""
            )!!
        ).jsonObject["result"]!!.jsonObject

        val read = call("""{"document_id":"d1"}""")
        assertTrue(!read["isError"]!!.jsonPrimitive.boolean)
        assertEquals(
            "\"Trip\" (document_id: d1), in the top level.\n\nContent (Markdown):\n- Book the trip",
            read["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        )

        val missing = call("""{"document_id":"nope"}""")
        assertTrue(missing["isError"]!!.jsonPrimitive.boolean)
        assertTrue(call("{}")["isError"]!!.jsonPrimitive.boolean)
    }
}
