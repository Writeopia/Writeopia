package io.writeopia.auth.google

import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DesktopGoogleSignInLauncherTest {

    private val http: HttpClient = HttpClient.newHttpClient()

    /** The browser's requests, kept so each test waits for them and sees their answers. */
    private val browserThreads = mutableListOf<Thread>()
    private val browserStatuses = mutableListOf<Int>()
    private val browserErrors = mutableListOf<Throwable>()

    /** Simulates the browser: parses the consent URL and immediately hits the redirect URI. */
    private fun fakeBrowser(
        onParams: (Map<String, String>) -> Unit = {},
        callbackQuery: (Map<String, String>) -> String,
    ): (String) -> Unit = fakeBrowserSending(onParams) { params -> listOf(callbackQuery(params)) }

    /** The browser hitting the redirect URI with each query, one after the other. */
    private fun fakeBrowserSending(
        onParams: (Map<String, String>) -> Unit = {},
        callbackQueries: (Map<String, String>) -> List<String>,
    ): (String) -> Unit = { url ->
        val params = query(URI(url).rawQuery)
        onParams(params)
        val redirect = params.getValue("redirect_uri")
        browserThreads += Thread {
            try {
                callbackQueries(params).forEach { callbackQuery ->
                    val response = http.send(
                        HttpRequest.newBuilder(URI("$redirect?$callbackQuery")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                    )
                    synchronized(browserStatuses) { browserStatuses += response.statusCode() }
                }
            } catch (e: Throwable) {
                synchronized(browserErrors) { browserErrors += e }
            }
        }.also(Thread::start)
    }

    /** Waits for the browser's requests, failing the test if one failed or hung. */
    private fun browserAnswers(): List<Int> {
        browserThreads.forEach { thread ->
            thread.join(5_000)
            assertTrue(!thread.isAlive, "The browser request never finished")
        }
        browserErrors.firstOrNull()?.let { throw AssertionError("The browser request failed", it) }
        return browserStatuses.toList()
    }

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").filter { it.isNotBlank() }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
        }

    @Test
    fun `returns the auth code with pkce verifier and redirect uri`() = runBlocking<Unit> {
        var consentParams: Map<String, String> = emptyMap()
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = fakeBrowser(onParams = { consentParams = it }) { params -> "code=abc123&state=${params["state"]}" },
            clientId = "desktop-client",
            timeout = 5.seconds,
        )

        val result = launcher.signIn()

        assertEquals(listOf(200), browserAnswers())
        assertIs<GoogleSignInResult.Success>(result)
        val credential = assertIs<GoogleCredential.AuthCode>(result.credential)
        assertEquals("abc123", credential.code)
        assertEquals("desktop-client", credential.clientId)
        assertTrue(credential.redirectUri.startsWith("http://127.0.0.1:"))
        assertTrue(credential.redirectUri.endsWith("/callback"))
        assertEquals(consentParams["redirect_uri"], credential.redirectUri)

        assertEquals("code", consentParams["response_type"])
        assertEquals("S256", consentParams["code_challenge_method"])
        assertEquals("openid email profile", consentParams["scope"])
        assertEquals("desktop-client", consentParams["client_id"])
        assertEquals(Pkce.challenge(credential.codeVerifier!!), consentParams["code_challenge"])
    }

    @Test
    fun `after a forged callback, the real one still signs in`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = fakeBrowserSending { params ->
                listOf("code=forged-code&state=forged", "code=real-code&state=${params["state"]}")
            },
            clientId = "desktop-client",
            timeout = 5.seconds,
        )

        val result = launcher.signIn()

        assertEquals(listOf(404, 200), browserAnswers())
        val success = assertIs<GoogleSignInResult.Success>(result)
        assertEquals("real-code", assertIs<GoogleCredential.AuthCode>(success.credential).code)
    }

    @Test
    fun `ignores a callback whose state does not match and keeps waiting`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = fakeBrowser { "code=abc123&state=forged" },
            clientId = "desktop-client",
            timeout = 500.milliseconds,
        )

        val result = launcher.signIn()

        // Turned away, not taken: the attempt runs out of time with no credential.
        assertEquals(listOf(404), browserAnswers())
        assertIs<GoogleSignInResult.Cancelled>(result)
    }

    @Test
    fun `a browser that can't be opened fails the sign-in`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = { throw UnsupportedOperationException("No browser") },
            clientId = "desktop-client",
            timeout = 5.seconds,
        )

        val result = launcher.signIn()

        assertIs<GoogleSignInResult.Failure>(result)
    }

    @Test
    fun `treats access_denied as cancellation`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = fakeBrowser { params -> "error=access_denied&state=${params["state"]}" },
            clientId = "desktop-client",
            timeout = 5.seconds,
        )

        assertIs<GoogleSignInResult.Cancelled>(launcher.signIn())
        assertEquals(listOf(200), browserAnswers())
    }

    @Test
    fun `times out as cancellation when the browser never calls back`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = { },
            clientId = "desktop-client",
            timeout = 200.milliseconds,
        )

        assertIs<GoogleSignInResult.Cancelled>(launcher.signIn())
    }
}
