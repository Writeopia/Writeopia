package io.writeopia.api.typesafe.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.api.typesafe.model.SystemOneRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypeSafeClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val answer = """
        {"model":"jev-1.13.0","answers":{"LIST":{"type":"noul","noul":0.91}},"usage":{"input_tokens":10,"output_tokens":1}}
    """.trimIndent()

    @Test
    fun `it sends the key, the model and the questions`() = runTest {
        var sentRequest: SystemOneRequest? = null
        var sentAuth: String? = null

        val engine = MockEngine { request ->
            sentAuth = request.headers[HttpHeaders.Authorization]
            sentRequest = json.decodeFromString(SystemOneRequest.serializer(), request.body.toByteArray().decodeToString())
            respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val client = TypeSafeClient(apiKey = "secret", model = "jev-latest", engine = engine)
        val result = client.ask(JsonPrimitive("text"), mapOf("LIST" to SystemOneQuestion.noul("Is it a list?")))

        assertIs<TypeSafeResult.Success>(result)
        assertEquals(0.91, result.response.answers["LIST"]?.noul)
        assertEquals("Bearer secret", sentAuth)
        assertEquals("jev-latest", sentRequest?.model)
        assertEquals(setOf("LIST"), sentRequest?.questions?.keys)
    }

    @Test
    fun `it retries on overload and succeeds`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond("overloaded", HttpStatusCode(529, "Overloaded"))
            } else {
                respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

        val client = TypeSafeClient(apiKey = "secret", engine = engine, retryDelayMillis = 0)
        val result = client.ask(JsonPrimitive("text"), mapOf("LIST" to SystemOneQuestion.noul("Is it a list?")))

        assertIs<TypeSafeResult.Success>(result)
        assertEquals(2, calls)
    }

    @Test
    fun `it fails without a key and without calling the API`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(answer, HttpStatusCode.OK)
        }

        val client = TypeSafeClient(apiKey = null, engine = engine)
        val result = client.ask(JsonPrimitive("text"), emptyMap())

        assertIs<TypeSafeResult.Failure>(result)
        assertEquals(0, calls)
    }

    @Test
    fun `it fails on a rejected key`() = runTest {
        val engine = MockEngine { respond("nope", HttpStatusCode.Unauthorized) }

        val client = TypeSafeClient(apiKey = "bad", engine = engine)
        val result = client.ask(JsonPrimitive("text"), emptyMap())

        assertIs<TypeSafeResult.Failure>(result)
    }
}
