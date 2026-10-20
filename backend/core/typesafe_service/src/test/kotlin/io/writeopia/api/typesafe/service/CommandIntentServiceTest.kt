package io.writeopia.api.typesafe.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommandIntentServiceTest {

    private fun service(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        threshold: Double = CommandIntentService.DEFAULT_REPLACE_THRESHOLD,
        onCall: () -> Unit = {},
    ) = CommandIntentService(
        client = TypeSafeClient(
            apiKey = "secret",
            engine = MockEngine {
                onCall()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        ),
        replaceThreshold = threshold,
    )

    private fun answer(replace: Double) =
        """
        {"model":"jev","answers":{"intent":{"type":"choice","choice":"${if (replace >= 0.5) "replace" else "add"}",
        "confidence":0.8,"probabilities":{"replace":$replace,"add":${1 - replace}}}},
        "usage":{"input_tokens":80,"output_tokens":4}}
        """.trimIndent()

    private val request = AiCommandRequest(
        command = "fix the grammar",
        selectedText = "Their going to the park tomorow.",
        documentText = "Notes of the day.\nTheir going to the park tomorow.",
    )

    @Test
    fun `a rewrite replaces the text`() = runTest {
        val (response, usage) = service(answer(replace = 0.91)).decide(request)

        assertEquals(AiCommandIntent.REPLACE, response.intent)
        assertEquals(0.91, response.replaceProbability)
        assertEquals(80, usage.inputTokens)
    }

    @Test
    fun `new content is added after the text`() = runTest {
        val (response, _) = service(answer(replace = 0.12)).decide(request.copy(command = "give me three examples"))

        assertEquals(AiCommandIntent.ADD, response.intent)
        assertEquals(0.12, response.replaceProbability)
    }

    @Test
    fun `replacing needs the threshold`() = runTest {
        val (below, _) = service(answer(replace = 0.6), threshold = 0.7).decide(request)
        val (above, _) = service(answer(replace = 0.6), threshold = 0.55).decide(request)

        assertEquals(AiCommandIntent.ADD, below.intent)
        assertEquals(AiCommandIntent.REPLACE, above.intent)
    }

    @Test
    fun `without a selection an edit rewrites the document`() = runTest {
        val (response, _) = service(answer(replace = 0.8))
            .decide(request.copy(selectedText = "", command = "fix the typo in the last line"))

        assertEquals(AiCommandIntent.REWRITE, response.intent)
        assertEquals(0.8, response.replaceProbability)
    }

    @Test
    fun `without a selection new content is added`() = runTest {
        val (response, _) = service(answer(replace = 0.1))
            .decide(request.copy(selectedText = "", command = "write a conclusion"))

        assertEquals(AiCommandIntent.ADD, response.intent)
    }

    @Test
    fun `it does not ask when there is no text at all`() = runTest {
        var calls = 0
        val (response, _) = service(answer(replace = 0.9)) { calls++ }
            .decide(request.copy(selectedText = "  ", documentText = ""))

        assertEquals(AiCommandIntent.ADD, response.intent)
        assertEquals(0, calls)
    }

    @Test
    fun `an empty command is refused`() = runTest {
        var calls = 0
        val (response, _) = service(answer(replace = 0.9)) { calls++ }.decide(request.copy(command = " "))

        assertNull(response.intent)
        assertNotNull(response.error)
        assertEquals(0, calls)
    }

    @Test
    fun `a failure of the API is reported`() = runTest {
        val (response, _) = service("""{"error":"boom"}""", status = HttpStatusCode.InternalServerError)
            .decide(request)

        assertNull(response.intent)
        assertNotNull(response.error)
    }

    @Test
    fun `the threshold comes from the environment`() {
        assertEquals(0.65, CommandIntentService.replaceThresholdFromEnv("0.65"))
        assertEquals(
            CommandIntentService.DEFAULT_REPLACE_THRESHOLD,
            CommandIntentService.replaceThresholdFromEnv("nope")
        )
        assertEquals(CommandIntentService.DEFAULT_REPLACE_THRESHOLD, CommandIntentService.replaceThresholdFromEnv(null))
    }
}
