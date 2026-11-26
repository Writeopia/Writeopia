package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.McpHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class McpHandlerTest {

    private val handler = McpHandler(
        LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false)),
        serverVersion = "test"
    )

    private suspend fun call(body: String): JsonObject =
        Json.parseToJsonElement(handler.handle(body)!!).jsonObject

    @Test
    fun `initialize answers the version asked for when known`() = runTest {
        val answer = call(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"claude-code","version":"1"}}}"""
        )
        val result = answer["result"]!!.jsonObject

        assertEquals("2025-03-26", result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("writeopia", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(result["capabilities"]!!.jsonObject.containsKey("tools"))
    }

    @Test
    fun `initialize falls back to the newest version`() = runTest {
        val answer = call("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""")

        assertEquals(McpHandler.SUPPORTED_VERSIONS.first(), answer["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun `notifications get no answer`() = runTest {
        assertNull(handler.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    }

    @Test
    fun `lists the two tools`() = runTest {
        val tools = call("""{"jsonrpc":"2.0","id":"a","method":"tools/list"}""")["result"]!!.jsonObject["tools"]!!.jsonArray

        assertEquals(
            listOf(
                "create_document",
                "append_to_document",
                "set_header_image",
                "get_current_place",
                "list_tags",
                "add_tags",
                "finish_writing",
                "list_folders",
                "create_folder"
            ),
            tools.map {
                it.jsonObject["name"]!!.jsonPrimitive.content
            }
        )
    }

    @Test
    fun `appending with no document open is a tool error`() = runTest {
        val result = call(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"append_to_document","arguments":{"markdown":"Hi"}}}"""
        )["result"]!!.jsonObject

        assertTrue(result["isError"]!!.jsonPrimitive.boolean)
        assertTrue(result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content.startsWith("No document is open"))
    }

    @Test
    fun `missing arguments are tool errors`() = runTest {
        val result = call(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"create_document","arguments":{}}}"""
        )["result"]!!.jsonObject

        assertTrue(result["isError"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `unknown methods and bad bodies are JSON-RPC errors`() = runTest {
        assertEquals(
            -32601,
            call("""{"jsonrpc":"2.0","id":4,"method":"resources/list"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int
        )
        assertEquals(-32700, call("not json")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a batch gets one answer per request`() = runTest {
        val answer = Json.parseToJsonElement(
            handler.handle(
                """[{"jsonrpc":"2.0","id":1,"method":"ping"},{"jsonrpc":"2.0","method":"notifications/initialized"}]"""
            )!!
        )

        assertEquals(1, (answer as JsonArray).size)
    }
}
