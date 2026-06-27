package io.writeopia.auth.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthApiTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `enableUser should call correct endpoint with admin key header`() = runTest {
        var capturedRequest: io.ktor.client.request.HttpRequestData? = null

        val mockEngine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = "User enabled",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val authApi = AuthApi(client, "https://api.example.com")
        val result = authApi.enableUser("test@example.com", "my-admin-key")

        assertIs<ResultData.Complete<Unit>>(result)

        // Verify the request
        val request = capturedRequest!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.example.com/api/auth/admin/enable-user", request.url.toString())
        assertEquals("my-admin-key", request.headers["X-Admin-Key"])

        // Verify the body contains the email
        val bodyBytes = request.body.toByteArray()
        val bodyString = bodyBytes.decodeToString()
        assertTrue(bodyString.contains("test@example.com"))
    }

    @Test
    fun `enableUser should return Complete on success`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "User enabled",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val authApi = AuthApi(client, "https://api.example.com")
        val result = authApi.enableUser("test@example.com", "admin-key")

        assertIs<ResultData.Complete<Unit>>(result)
    }

    @Test
    fun `enableUser should return Error on non-success status`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val authApi = AuthApi(client, "https://api.example.com")
        val result = authApi.enableUser("test@example.com", "wrong-key")

        assertIs<ResultData.Error<Unit>>(result)
    }

    @Test
    fun `enableUser should return Error on network exception`() = runTest {
        val mockEngine = MockEngine {
            throw java.io.IOException("Network error")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val authApi = AuthApi(client, "https://api.example.com")
        val result = authApi.enableUser("test@example.com", "admin-key")

        assertIs<ResultData.Error<Unit>>(result)
    }

    @Test
    fun `enableUser should send correct JSON body`() = runTest {
        var capturedBody: String? = null

        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = "User enabled",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val authApi = AuthApi(client, "https://api.example.com")
        authApi.enableUser("user@test.com", "key123")

        // Verify the JSON body
        val expectedBody = """{"email":"user@test.com"}"""
        assertEquals(expectedBody, capturedBody)
    }
}
