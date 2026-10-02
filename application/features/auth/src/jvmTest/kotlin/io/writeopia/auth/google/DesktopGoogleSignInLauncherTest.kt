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

    /** Simulates the browser: parses the consent URL and immediately hits the redirect URI. */
    private fun fakeBrowser(
        onParams: (Map<String, String>) -> Unit = {},
        callbackQuery: (Map<String, String>) -> String,
    ): (String) -> Unit = { url ->
        val params = query(URI(url).rawQuery)
        onParams(params)
        val redirect = params.getValue("redirect_uri")
        Thread {
            val response = http.send(
                HttpRequest.newBuilder(URI("$redirect?${callbackQuery(params)}")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            assertEquals(200, response.statusCode())
        }.start()
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
    fun `rejects a callback whose state does not match`() = runBlocking<Unit> {
        val launcher = DesktopGoogleSignInLauncher(
            openUrl = fakeBrowser { "code=abc123&state=forged" },
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
