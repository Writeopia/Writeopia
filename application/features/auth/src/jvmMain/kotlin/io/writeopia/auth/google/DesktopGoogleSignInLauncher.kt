package io.writeopia.auth.google

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Desktop "installed app" flow: open the system browser on Google's consent page, receive the
 * authorization code on a loopback HTTP server (`http://127.0.0.1:<random port>/callback`),
 * and hand the code plus PKCE verifier to the backend, which exchanges it with Google.
 */
class DesktopGoogleSignInLauncher(
    private val openUrl: (String) -> Unit,
    private val clientId: String = GoogleOAuthConfig.DESKTOP_CLIENT_ID,
    private val timeout: Duration = 2.minutes,
    private val authEndpoint: String = "https://accounts.google.com/o/oauth2/v2/auth",
) : GoogleSignInLauncher {

    override suspend fun signIn(): GoogleSignInResult = withContext(Dispatchers.IO) {
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        val callback = CompletableDeferred<CallbackParams>()

        val server = try {
            HttpServer.create(InetSocketAddress(LOOPBACK_HOST, 0), 0)
        } catch (e: Exception) {
            return@withContext GoogleSignInResult.Failure(e)
        }

        try {
            server.createContext(CALLBACK_PATH) { exchange ->
                val params = parseQuery(exchange.requestURI.rawQuery)
                respondHtml(exchange, if (params.containsKey("error")) ERROR_HTML else SUCCESS_HTML)
                callback.complete(CallbackParams(params))
            }
            server.start()

            val redirectUri = "http://$LOOPBACK_HOST:${server.address.port}$CALLBACK_PATH"
            openUrl(buildAuthUrl(redirectUri, Pkce.challenge(verifier), state))

            val received = withTimeoutOrNull(timeout) { callback.await() }
                ?: return@withContext GoogleSignInResult.Cancelled

            when {
                received.error != null ->
                    if (received.error == "access_denied") {
                        GoogleSignInResult.Cancelled
                    } else {
                        GoogleSignInResult.Failure(IllegalStateException("Google returned: ${received.error}"))
                    }

                received.state != state ->
                    GoogleSignInResult.Failure(IllegalStateException("OAuth state mismatch"))

                received.code.isNullOrBlank() ->
                    GoogleSignInResult.Failure(IllegalStateException("No authorization code received"))

                else -> GoogleSignInResult.Success(
                    GoogleCredential.AuthCode(
                        code = received.code,
                        codeVerifier = verifier,
                        redirectUri = redirectUri,
                        clientId = clientId,
                    )
                )
            }
        } finally {
            server.stop(0)
        }
    }

    private fun buildAuthUrl(redirectUri: String, challenge: String, state: String): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to "openid email profile",
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "prompt" to "select_account",
        )
        val query = params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        return "$authEndpoint?$query"
    }

    private class CallbackParams(params: Map<String, String>) {
        val code: String? = params["code"]
        val state: String? = params["state"]
        val error: String? = params["error"]
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty()
            .split("&")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val (key, value) = pair.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }

    private fun respondHtml(exchange: HttpExchange, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val CALLBACK_PATH = "/callback"

        val SUCCESS_HTML = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Writeopia</title></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding-top: 80px;">
            <h2>Signed in to Writeopia</h2><p>You can close this window and return to the app.</p>
            </body></html>
        """.trimIndent()

        val ERROR_HTML = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Writeopia</title></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding-top: 80px;">
            <h2>Sign in was not completed</h2><p>You can close this window and try again in the app.</p>
            </body></html>
        """.trimIndent()
    }
}
