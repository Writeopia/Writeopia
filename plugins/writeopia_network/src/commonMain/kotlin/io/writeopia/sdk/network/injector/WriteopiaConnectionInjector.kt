package io.writeopia.sdk.network.injector

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
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
    private val bearerTokenHandler: BearerTokenHandler? = null,
    private val apiLogger: Logger = consoleLogger,
    private val client: HttpClient =
        ApiInjectorDefaults.httpClient(
            bearerTokenHandler = bearerTokenHandler,
            apiLogger = apiLogger
        ),
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

        fun setDisableWebsocket(disable: Boolean) {
            this.disableWebsocket = disable
        }

        fun setBearerTokenHandler(handler: BearerTokenHandler) {
            this.bearerTokenHandler = handler
        }

        /**
         * Clears the singleton instance and closes the HttpClient.
         * Call this on logout to ensure cached bearer tokens are invalidated.
         */
        fun clearInstance() {
            instance?.client?.close()
            instance = null
        }

        fun singleton(): WriteopiaConnectionInjector {
            val thisBaseUrl = baseUrl ?: throw IllegalStateException("Base url was not set!")

            return WriteopiaConnectionInjector(
                baseUrl = thisBaseUrl,
                bearerTokenHandler = bearerTokenHandler,
                disableWebsocket = disableWebsocket
            )
        }
    }
}

private object ApiInjectorDefaults {
    fun httpClient(
        json: Json = writeopiaJson,
        bearerTokenHandler: BearerTokenHandler?,
        apiLogger: Logger,
    ) = HttpClient {
        install(ContentNegotiation) {
            json(json = json)
        }

        install(WebSockets)

        install(Logging) {
            logger = apiLogger
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        if (bearerTokenHandler != null) {
            install(Auth) {
                bearer {
                    loadTokens {
                        val accessToken = bearerTokenHandler.getIdToken() ?: ""
                        val refreshToken = bearerTokenHandler.getRefreshToken() ?: ""
                        BearerTokens(accessToken, refreshToken)
                    }

                    refreshTokens {
                        when (val result = bearerTokenHandler.refreshTokens()) {
                            is TokenRefreshResult.Success -> {
                                BearerTokens(result.accessToken, result.refreshToken)
                            }
                            else -> null
                        }
                    }
                }
            }
        }
    }
}
