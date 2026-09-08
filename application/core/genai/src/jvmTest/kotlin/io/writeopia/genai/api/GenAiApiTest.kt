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
import kotlinx.coroutines.launch
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

    private fun createMockClient(
        delayMs: Long = 0,
        responseBody: String = """{"available": true}"""
    ): HttpClient {
        return HttpClient(MockEngine) {
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
    }

    @Test
    fun `getUsage should propagate CancellationException when cancelled`() = runTest {
        val client = createMockClient(
            delayMs = 10_000, // Long delay so we can cancel
            responseBody = """{"totalInputTokens":0,"totalOutputTokens":0,"totalTokens":0,"requestCount":0,"periodStart":0,"periodEnd":0}"""
        )
        val api = GenAiApi(client, json, "http://localhost")

        val job = launch {
            api.getUsage()
        }

        // Give the coroutine a moment to start the request
        delay(50)

        // Cancel the job
        job.cancel()

        // The job should be cancelled
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `checkStatus should propagate CancellationException when cancelled`() = runTest {
        val client = createMockClient(delayMs = 10_000)
        val api = GenAiApi(client, json, "http://localhost")

        val job = launch {
            api.checkStatus()
        }

        delay(50)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `generate should propagate CancellationException when cancelled`() = runTest {
        val client = createMockClient(
            delayMs = 10_000,
            responseBody = """{"response":"test","done":true}"""
        )
        val api = GenAiApi(client, json, "http://localhost")

        val job = launch {
            api.generate("test prompt")
        }

        delay(50)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `getUsage should return Complete when request succeeds`() = runTest {
        val client = createMockClient(
            responseBody = """{"totalInputTokens":100,"totalOutputTokens":200,"totalTokens":300,"requestCount":5,"periodStart":1000,"periodEnd":2000}"""
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

    @Test
    fun `CancellationException thrown from getUsage should not be wrapped in ResultData Error`() = runTest {
        val client = createMockClient(delayMs = 10_000)
        val api = GenAiApi(client, json, "http://localhost")

        val deferred = async {
            api.getUsage()
        }

        delay(50)
        deferred.cancel()

        // If CancellationException is properly rethrown, the deferred will be cancelled
        // If it was wrapped in ResultData.Error, awaiting would return that error instead
        assertFailsWith<CancellationException> {
            deferred.await()
        }
    }
}
