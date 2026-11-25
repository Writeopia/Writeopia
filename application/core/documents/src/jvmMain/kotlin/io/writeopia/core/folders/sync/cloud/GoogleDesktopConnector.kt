package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.writeopia.auth.core.repository.EncryptedTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.coroutineContext

/** The Google tokens of the desktop app. */
data class GoogleTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)

interface GoogleTokenStore {
    fun load(): GoogleTokens?

    fun save(tokens: GoogleTokens)

    fun clear()
}

/** Encrypted with a key of this machine, like the Writeopia tokens. */
object EncryptedGoogleTokenStore : GoogleTokenStore {
    private const val KEY = "google_drive"

    override fun load(): GoogleTokens? {
        val data = EncryptedTokenStorage.getTokenData(KEY) ?: return null
        val refreshToken = data.refreshToken ?: return null
        return GoogleTokens(data.accessToken, refreshToken, data.accessTokenExpiresAt ?: 0)
    }

    override fun save(tokens: GoogleTokens) {
        EncryptedTokenStorage.saveTokens(KEY, tokens.accessToken, tokens.refreshToken, tokens.expiresAtMillis)
    }

    override fun clear() {
        EncryptedTokenStorage.clearTokens(KEY)
    }
}

/**
 * Google sign-in of the desktop app, as Google asks of installed apps: the browser opens Google's
 * consent page, which sends the user back to a one-time address on this machine (127.0.0.1, a free
 * port) with a code; the code, with the PKCE verifier, is exchanged for the tokens.
 *
 * The client secret of a Desktop client isn't secret (Google knows it ships in the app); PKCE and
 * the loopback address are what keep the code to this app.
 */
class GoogleDesktopConnector(
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient,
    private val tokenStore: GoogleTokenStore = EncryptedGoogleTokenStore,
    private val openBrowser: (String) -> Unit = ::browse,
    private val now: () -> Long = System::currentTimeMillis,
    private val signInTimeoutMillis: Long = 5 * 60 * 1000L,
) : CloudAccountConnector {

    override val providerName: String = "Google Drive"

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()
    private val random = SecureRandom()

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) { tokenStore.load() != null }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = randomToken()
            val state = randomToken()

            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val redirectUri = "http://127.0.0.1:${server.localPort}"
                openBrowser(authorizationUrl(redirectUri, challengeOf(verifier), state))

                val code = awaitCode(server, state)
                val tokens = exchange(
                    "code" to code,
                    "code_verifier" to verifier,
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code",
                )
                if (!tokens.scope.orEmpty().split(" ").contains(GoogleDriveFileStore.SCOPE)) {
                    throw CloudStoreException(
                        "Writeopia needs access to its own folder in your Google Drive. " +
                            "Connect again and allow it.",
                    )
                }
                val refreshToken = tokens.refreshToken
                    ?: throw CloudStoreException("Google sent no refresh token")
                tokenLock.withLock { tokenStore.save(tokens.toGoogleTokens(refreshToken)) }
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val tokens = tokenLock.withLock { tokenStore.load().also { tokenStore.clear() } } ?: return@withContext
        // Revoking the refresh token takes the app's access back; the user can also do it in
        // their Google account. A failure leaves the token unused here.
        runCatching {
            httpClient.submitForm(REVOKE_URL, parameters { append("token", tokens.refreshToken) })
        }
        Unit
    }

    override suspend fun accessToken(forceRefresh: Boolean): String? = withContext(Dispatchers.IO) {
        tokenLock.withLock {
            val tokens = tokenStore.load() ?: return@withLock null
            if (!forceRefresh && tokens.expiresAtMillis - EXPIRY_MARGIN_MILLIS > now()) {
                return@withLock tokens.accessToken
            }

            val response = httpClient.submitForm(
                TOKEN_URL,
                parameters {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("refresh_token", tokens.refreshToken)
                    append("grant_type", "refresh_token")
                },
            )
            val body = response.bodyAsText()
            when {
                response.status.isSuccess() -> {
                    val refreshed = json.decodeFromString<TokenResponse>(body)
                    // Google may send a new refresh token; otherwise the old one stays good.
                    refreshed.toGoogleTokens(refreshed.refreshToken ?: tokens.refreshToken)
                        .also(tokenStore::save)
                        .accessToken
                }

                // Taken back by the user, or expired: only a new sign-in helps.
                response.status == HttpStatusCode.BadRequest && body.contains("invalid_grant") -> {
                    tokenStore.clear()
                    null
                }

                else -> throw CloudStoreException("Google token refresh failed: ${response.status.value}")
            }
        }
    }

    private fun authorizationUrl(redirectUri: String, challenge: String, state: String): String =
        URLBuilder(AUTH_URL).apply {
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", GoogleDriveFileStore.SCOPE)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
            // A refresh token, so the sync goes on without signing in again.
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
        }.buildString()

    /**
     * Answers the browser's requests to the loopback address until one comes back from Google
     * with this [state]. Other requests (a favicon) get a 404.
     */
    private suspend fun awaitCode(server: ServerSocket, state: String): String {
        val deadline = now() + signInTimeoutMillis
        server.soTimeout = 1000

        while (true) {
            coroutineContext.ensureActive()
            if (now() > deadline) throw CloudStoreException("Google sign-in timed out")

            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }

            socket.use {
                val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
                val target = requestLine.split(" ").getOrNull(1).orEmpty()
                val query = Url("http://127.0.0.1$target").parameters
                val output = socket.getOutputStream()

                if (query["state"] != state) {
                    output.write(httpResponse(404, "Not found").toByteArray())
                    return@use
                }

                val code = query["code"]
                val message = if (code != null) {
                    "Writeopia is connected to your Google Drive. You can close this tab."
                } else {
                    "Writeopia wasn't connected to Google Drive. You can close this tab."
                }
                output.write(httpResponse(200, page(message)).toByteArray())
                output.flush()

                return code ?: throw CloudStoreException("Google sign-in: ${query["error"] ?: "no code"}")
            }
        }
    }

    private suspend fun exchange(vararg fields: Pair<String, String>): TokenResponse {
        val response = httpClient.submitForm(
            TOKEN_URL,
            parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                fields.forEach { (name, value) -> append(name, value) }
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw CloudStoreException("Google sign-in failed: ${response.status.value} ${body.take(300)}")
        }
        return json.decodeFromString(body)
    }

    private fun TokenResponse.toGoogleTokens(refreshToken: String) =
        GoogleTokens(accessToken, refreshToken, now() + expiresIn * 1000)

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val scope: String? = null,
    )

    companion object {
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        internal const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        internal const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        private const val EXPIRY_MARGIN_MILLIS = 60_000L

        internal fun challengeOf(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private fun httpResponse(status: Int, body: String): String {
            val reason = if (status == 200) "OK" else "Not Found"
            val bytes = body.toByteArray(Charsets.UTF_8).size
            return "HTTP/1.1 $status $reason\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: $bytes\r\n" +
                "Connection: close\r\n\r\n" +
                body
        }

        private fun page(message: String): String =
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>Writeopia</title></head>" +
                "<body style=\"font-family: sans-serif; margin: 4em auto; max-width: 32em;\">" +
                "<h2>Writeopia</h2><p>$message</p></body></html>"

        private fun browse(url: String) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                // Linux desktops where AWT can't open the browser.
                ProcessBuilder("xdg-open", url).start()
            }
        }
    }
}
