package io.writeopia.api.core.auth.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.writeopia.api.core.auth.models.GoogleTokenException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleHttpCodeExchangerTest {

    private val secrets = mapOf("desktop-client" to "desktop-secret")

    private fun exchanger(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        GoogleHttpCodeExchanger(
            clientSecrets = secrets,
            client = HttpClient(MockEngine(handler)),
            tokenEndpoint = "https://token.example/token",
        )

    private suspend fun HttpRequestData.formParameters() =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString().parseUrlEncodedParameters()

    @Test
    fun `posts the authorization code form and returns the id token`() = runBlocking {
        var captured: HttpRequestData? = null
        val exchanger = exchanger { request ->
            captured = request
            respond(
                content = """{"access_token":"at","id_token":"the-id-token","token_type":"Bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val idToken = exchanger.exchange(
            code = "code-123",
            codeVerifier = "verifier-xyz",
            redirectUri = "http://127.0.0.1:4242/callback",
            clientId = "desktop-client",
        )

        assertEquals("the-id-token", idToken)
        val request = captured!!
        assertEquals("https://token.example/token", request.url.toString())
        val form = request.formParameters()
        assertEquals("desktop-client", form["client_id"])
        assertEquals("desktop-secret", form["client_secret"])
        assertEquals("code-123", form["code"])
        assertEquals("verifier-xyz", form["code_verifier"])
        assertEquals("http://127.0.0.1:4242/callback", form["redirect_uri"])
        assertEquals("authorization_code", form["grant_type"])
    }

    @Test
    fun `omits the code verifier when absent`() = runBlocking {
        var captured: HttpRequestData? = null
        val exchanger = exchanger { request ->
            captured = request
            respond("""{"id_token":"tok"}""", HttpStatusCode.OK)
        }

        exchanger.exchange("code", null, "https://app.example", "desktop-client")

        assertNull(captured!!.formParameters()["code_verifier"])
    }

    @Test
    fun `fails without a network call for an unknown client id`() = runBlocking {
        var called = false
        val exchanger = exchanger {
            called = true
            respond("""{"id_token":"tok"}""", HttpStatusCode.OK)
        }

        assertFailsWith<GoogleTokenException> {
            exchanger.exchange("code", null, "https://app.example", "unknown-client")
        }
        assertTrue(!called)
    }

    @Test
    fun `surfaces Google error descriptions`() = runBlocking {
        val exchanger = exchanger {
            respond(
                """{"error":"invalid_grant","error_description":"Bad Request"}""",
                HttpStatusCode.BadRequest,
            )
        }

        val error = assertFailsWith<GoogleTokenException> {
            exchanger.exchange("code", null, "https://app.example", "desktop-client")
        }
        assertTrue(error.message!!.contains("Bad Request"), error.message)
    }

    @Test
    fun `fails when the response has no id token`() = runBlocking {
        val exchanger = exchanger { respond("""{"access_token":"only"}""", HttpStatusCode.OK) }

        assertFailsWith<GoogleTokenException> {
            exchanger.exchange("code", null, "https://app.example", "desktop-client")
        }
        Unit
    }
}
