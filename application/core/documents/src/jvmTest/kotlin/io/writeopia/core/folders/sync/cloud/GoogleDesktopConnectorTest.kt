package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleDesktopConnectorTest {

    private class MemoryTokenStore(var tokens: GoogleTokens? = null) : GoogleTokenStore {
        override fun load() = tokens

        override fun save(tokens: GoogleTokens) {
            this.tokens = tokens
        }

        override fun clear() {
            tokens = null
        }
    }

    private val requests = mutableListOf<Pair<HttpRequestData, Map<String, String>>>()
    private val tokenStore = MemoryTokenStore()
    private var time = 1_000_000L

    private fun connector(
        openBrowser: (String) -> Unit = {},
        answer: (Map<String, String>) -> Pair<HttpStatusCode, String>,
    ): GoogleDesktopConnector {
        val engine = MockEngine { request ->
            val form = parseQueryString(((request.body as OutgoingContent.ByteArrayContent).bytes()).decodeToString())
                .entries().associate { (key, values) -> key to values.first() }
            requests += request to form
            val (status, body) = answer(form)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return GoogleDesktopConnector(
            clientId = "client",
            clientSecret = "secret",
            httpClient = HttpClient(engine),
            tokenStore = tokenStore,
            openBrowser = openBrowser,
            now = { time },
            signInTimeoutMillis = 10_000,
        )
    }

    /** What the browser does: follows Google's redirect back to the app with a code. */
    private fun googleRedirectsBack(authorizationUrl: String, code: String?, state: String? = null) {
        val parameters = Url(authorizationUrl).parameters
        val redirect = parameters["redirect_uri"]!!
        val query = buildString {
            append("state=${state ?: parameters["state"]}")
            if (code != null) append("&code=$code") else append("&error=access_denied")
        }
        thread {
            // A favicon request first, which the app must not take for the answer.
            (URI("$redirect/favicon.ico").toURL().openConnection() as HttpURLConnection).responseCode
            (URI("$redirect/?$query").toURL().openConnection() as HttpURLConnection).responseCode
        }
    }

    @Test
    fun `connecting exchanges the code with the PKCE verifier and keeps the tokens`() = runBlocking {
        var authorizationUrl = ""
        val connector = connector(
            openBrowser = { url ->
                authorizationUrl = url
                googleRedirectsBack(url, code = "the-code")
            },
        ) {
            HttpStatusCode.OK to """{"access_token":"a1","expires_in":3600,"refresh_token":"r1",
                |"scope":"${GoogleDriveFileStore.SCOPE}","token_type":"Bearer"}
            """.trimMargin()
        }

        assertTrue(connector.connect().isSuccess)

        val authorization = Url(authorizationUrl).parameters
        assertEquals(GoogleDriveFileStore.SCOPE, authorization["scope"])
        assertEquals("offline", authorization["access_type"])
        assertEquals("S256", authorization["code_challenge_method"])
        assertTrue(authorization["redirect_uri"]!!.startsWith("http://127.0.0.1:"))

        val (request, form) = requests.single()
        assertEquals(GoogleDesktopConnector.TOKEN_URL, request.url.toString())
        assertEquals("the-code", form["code"])
        assertEquals("authorization_code", form["grant_type"])
        assertEquals(authorization["redirect_uri"], form["redirect_uri"])
        assertEquals(authorization["code_challenge"], GoogleDesktopConnector.challengeOf(form["code_verifier"]!!))

        assertEquals(GoogleTokens("a1", "r1", time + 3_600_000), tokenStore.tokens)
        assertTrue(connector.isConnected())
    }

    @Test
    fun `a refusal, or a consent without the Drive access, doesn't connect`() = runBlocking {
        val refused = connector(openBrowser = { url -> googleRedirectsBack(url, code = null) }) {
            HttpStatusCode.OK to "{}"
        }
        assertTrue(refused.connect().isFailure)

        val withoutDrive = connector(openBrowser = { url -> googleRedirectsBack(url, code = "c") }) {
            HttpStatusCode.OK to """{"access_token":"a","refresh_token":"r","scope":"openid"}"""
        }
        assertTrue(withoutDrive.connect().isFailure)
        assertNull(tokenStore.tokens)
    }

    @Test
    fun `the access token is refreshed when it expires or when asked`() = runBlocking {
        tokenStore.tokens = GoogleTokens("old", "r1", expiresAtMillis = time + 600_000)
        val connector = connector { HttpStatusCode.OK to """{"access_token":"new","expires_in":3600}""" }

        assertEquals("old", connector.accessToken(forceRefresh = false))
        assertTrue(requests.isEmpty())

        assertEquals("new", connector.accessToken(forceRefresh = true))
        assertEquals("refresh_token", requests.single().second["grant_type"])
        assertEquals("r1", requests.single().second["refresh_token"])
        // The refresh token stays when Google doesn't send another.
        assertEquals("r1", tokenStore.tokens?.refreshToken)

        time += 3_600_000
        connector.accessToken(forceRefresh = false)
        assertEquals(2, requests.size)
    }

    @Test
    fun `a refresh token taken back disconnects`() = runBlocking {
        tokenStore.tokens = GoogleTokens("old", "r1", expiresAtMillis = 0)
        val connector = connector { HttpStatusCode.BadRequest to """{"error":"invalid_grant"}""" }

        assertNull(connector.accessToken(forceRefresh = false))
        assertNull(tokenStore.tokens)
        assertTrue(!connector.isConnected())
    }

    @Test
    fun `disconnecting revokes the access and forgets the tokens`() = runBlocking {
        tokenStore.tokens = GoogleTokens("a", "r1", expiresAtMillis = 0)
        val connector = connector { HttpStatusCode.OK to "" }

        connector.disconnect()

        val (request, form) = requests.single()
        assertEquals(GoogleDesktopConnector.REVOKE_URL, request.url.toString())
        assertEquals("r1", form["token"])
        assertNull(tokenStore.tokens)
    }
}
