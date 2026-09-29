package io.writeopia.sdk.network.injector

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.network.api.StoryStepSyncApi
import io.writeopia.sdk.network.api.StoryStepSyncApiImpl
import io.writeopia.sdk.network.notes.NotesApi
import io.writeopia.sdk.network.oauth.BearerTokenHandler
import io.writeopia.sdk.network.oauth.TokenRefreshResult
import io.writeopia.sdk.network.websocket.MockWebsocketEditionManager
import io.writeopia.sdk.network.websocket.WebsocketEditionManager
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.sharededition.SharedEditionManager
import kotlinx.serialization.json.Json

private val consoleLogger = object : Logger {
    override fun log(message: String) {
        println("[Writeopia HTTP] $message")
    }
}

class WriteopiaConnectionInjector private constructor(
    private val baseUrl: String,
    private val apiLogger: Logger = consoleLogger,
    private val client: HttpClient = ApiInjectorDefaults.httpClient(apiLogger = apiLogger),
    private val disableWebsocket: Boolean = false
) {

    fun baseUrl(): String = baseUrl

    fun httpClient(): HttpClient = client

    fun notesApi(): NotesApi = NotesApi(client, baseUrl)

    fun storyStepSyncApi(): StoryStepSyncApi = StoryStepSyncApiImpl(client, baseUrl)

    fun liveEditionManager(): SharedEditionManager = if (disableWebsocket) {
        MockWebsocketEditionManager()
    } else {
        WebsocketEditionManager(host = "0.0.0.0", client = client, json = writeopiaJson)
    }

    companion object {
        var instance: WriteopiaConnectionInjector? = null

        private var baseUrl: String? = null
        private var disableWebsocket: Boolean = false
        private var bearerTokenHandler: BearerTokenHandler? = null

        fun setBaseUrl(baseUrl: String) {
            this.baseUrl = baseUrl
        }

        /**
         * Gets the base URL without creating the singleton instance.
         * Use this when you need the URL before the singleton is fully configured.
         */
        fun getBaseUrl(): String =
            baseUrl ?: throw IllegalStateException("Base url was not set!")

        fun setDisableWebsocket(disable: Boolean) {
            this.disableWebsocket = disable
        }

        fun setBearerTokenHandler(handler: BearerTokenHandler) {
            this.bearerTokenHandler = handler
        }

        /**
         * Looked up dynamically on every request (instead of being captured once at
         * HttpClient construction time) so callers can build the singleton and set the
         * bearer token handler in either order without permanently baking in "no auth".
         */
        internal fun currentBearerTokenHandler(): BearerTokenHandler? = bearerTokenHandler

        /**
         * Clears the singleton instance and closes the HttpClient.
         * Call this on logout to ensure cached bearer tokens are invalidated.
         */
        fun clearInstance() {
            instance?.client?.close()
            instance = null
        }

        fun singleton(): WriteopiaConnectionInjector {
            instance?.let { return it }

            val thisBaseUrl = baseUrl ?: throw IllegalStateException("Base url was not set!")

            return WriteopiaConnectionInjector(
                baseUrl = thisBaseUrl,
                disableWebsocket = disableWebsocket
            ).also { instance = it }
        }
    }
}

private object ApiInjectorDefaults {
    fun httpClient(
        json: Json = writeopiaJson,
        apiLogger: Logger,
    ) = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 300_000
            socketTimeoutMillis = 300_000
        }

        install(ContentNegotiation) {
            json(json = json)
        }

        install(WebSockets)

        install(Logging) {
            logger = apiLogger
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        // The handler is looked up dynamically (not captured as a parameter) so this
        // client works correctly regardless of whether it's built before or after
        // WriteopiaConnectionInjector.setBearerTokenHandler() is called.
        install(Auth) {
            bearer {
                // No token means no Authorization header at all. An empty "Bearer " would
                // stop the web BFF from bridging the session cookie into the header.
                loadTokens {
                    val handler = WriteopiaConnectionInjector.currentBearerTokenHandler()
                    val accessToken = handler?.getIdToken()?.takeIf { it.isNotBlank() }
                        ?: return@loadTokens null

                    BearerTokens(accessToken, handler.getRefreshToken())
                }

                refreshTokens {
                    val handler = WriteopiaConnectionInjector.currentBearerTokenHandler()

                    when (val result = handler?.refreshTokens()) {
                        is TokenRefreshResult.Success -> {
                            BearerTokens(result.accessToken, result.refreshToken)
                        }
                        else -> null
                    }
                }
            }
        }
    }.also(::retryAfterSessionRefresh)

    /**
     * Sessions held in cookies the client can't read (the web app) are renewed through
     * [BearerTokenHandler.refreshSession] rather than the bearer plugin, which can only retry
     * by attaching a token it holds. Auth endpoints are skipped: their 401s mean bad
     * credentials or an expired session, which a refresh can't fix.
     */
    private fun retryAfterSessionRefresh(client: HttpClient) {
        client.plugin(HttpSend).intercept { request ->
            val call = execute(request)
            val handler = WriteopiaConnectionInjector.currentBearerTokenHandler()

            if (
                call.response.status == HttpStatusCode.Unauthorized &&
                !request.url.encodedPath.startsWith("/api/auth/") &&
                handler?.refreshSession() == true
            ) {
                execute(request)
            } else {
                call
            }
        }
    }
}
