package io.writeopia.api.typesafe.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WritingSuggestionsServiceTest {

    private fun service(body: String, onCall: () -> Unit = {}) = WritingSuggestionsService(
        client = TypeSafeClient(
            apiKey = "secret",
            engine = MockEngine {
                onCall()
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )
    )

    @Test
    fun `it maps the answers to the confident actions`() = runTest {
        val service = service(
            """
            {"model":"jev","answers":{
              "LIST":{"type":"noul","noul":0.93},
              "CHECK_LIST":{"type":"noul","noul":0.85},
              "CODE_BLOCK":{"type":"noul","noul":0.2},
              "SOMETHING_NEW":{"type":"noul","noul":0.99}
            },"usage":{"input_tokens":120,"output_tokens":9}}
            """.trimIndent()
        )

        val (response, usage) = service.suggest(
            WritingSuggestionsRequest(scope = WritingSuggestionScope.WRITING, text = "Buy milk, eggs and bread.")
        )

        assertEquals(
            listOf(WritingSuggestionAction.LIST, WritingSuggestionAction.CHECK_LIST),
            response.suggestions.map { it.action }
        )
        assertEquals(120, usage.inputTokens)
    }

    @Test
    fun `it does not call the API for a blank paragraph`() = runTest {
        var calls = 0
        val service = service("{}") { calls++ }

        val (response, _) = service.suggest(
            WritingSuggestionsRequest(scope = WritingSuggestionScope.WRITING, text = "   ")
        )

        assertTrue(response.suggestions.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `it answers an error when not configured`() = runTest {
        val service = WritingSuggestionsService(client = TypeSafeClient(apiKey = null))

        val (response, _) = service.suggest(
            WritingSuggestionsRequest(scope = WritingSuggestionScope.WRITING, text = "Some text.")
        )

        assertNotNull(response.error)
    }
}
