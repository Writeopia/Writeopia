package io.writeopia.genai.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenAiApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val usageResponseWithDataJson = """
        |{"totalInputTokens":100,"totalOutputTokens":200,"totalTokens":300,
        |"requestCount":5,"periodStart":1000,"periodEnd":2000,"quota":100000}
    """.trimMargin().replace("\n", "")

    private fun createMockClient(
        delayMs: Long = 0,
        responseBody: String = """{"available": true}"""
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    private fun createCancellingMockClient(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler {
                throw CancellationException("Simulated cancellation")
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Test
    fun `getUsage should propagate CancellationException when thrown by engine`() = runTest {
        val client = createCancellingMockClient()
        val api = GenAiApi(client, json, "http://localhost")

        val deferred = async {
            api.getUsage()
        }

        // Await without cancelling - CancellationException should propagate from the engine
        assertFailsWith<CancellationException> {
            deferred.await()
        }
    }

    @Test
    fun `checkStatus should propagate CancellationException when thrown by engine`() = runTest {
        val client = createCancellingMockClient()
        val api = GenAiApi(client, json, "http://localhost")

        val deferred = async {
            api.checkStatus()
        }

        // Await without cancelling - CancellationException should propagate from the engine
        assertFailsWith<CancellationException> {
            deferred.await()
        }
    }

    @Test
    fun `generate should propagate CancellationException when thrown by engine`() = runTest {
        val client = createCancellingMockClient()
        val api = GenAiApi(client, json, "http://localhost")

        val deferred = async {
            api.generate("test prompt")
        }

        // Await without cancelling - CancellationException should propagate from the engine
        assertFailsWith<CancellationException> {
            deferred.await()
        }
    }

    @Test
    fun `getUsage should return Complete when request succeeds`() = runTest {
        val client = createMockClient(
            responseBody = usageResponseWithDataJson
        )
        val api = GenAiApi(client, json, "http://localhost")

        val result = api.getUsage()

        assertTrue(result is ResultData.Complete)
        val usage = result.data
        assertTrue(usage.totalInputTokens == 100L)
        assertTrue(usage.totalOutputTokens == 200L)
        assertTrue(usage.totalTokens == 300L)
        assertTrue(usage.requestCount == 5L)
    }

    @Test
    fun `checkStatus should return Complete when request succeeds`() = runTest {
        val client = createMockClient(responseBody = """{"available": true}""")
        val api = GenAiApi(client, json, "http://localhost")

        val result = api.checkStatus()

        assertTrue(result is ResultData.Complete)
        assertTrue(result.data)
    }
}
