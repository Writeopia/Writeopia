package io.writeopia.sdk.network.injector

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.network.notes.NotesApi
import io.writeopia.sdk.network.oauth.BearerTokenHandler
import io.writeopia.sdk.network.websocket.MockWebsocketEditionManager
import io.writeopia.sdk.network.websocket.WebsocketEditionManager
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.sharededition.SharedEditionManager
import kotlinx.serialization.json.Json

class WriteopiaConnectionInjector(
    private val baseUrl: String,
    private val bearerTokenHandler: BearerTokenHandler,
    private val apiLogger: Logger = Logger.Companion.DEFAULT,
    private val client: HttpClient =
        ApiInjectorDefaults.httpClient(
            bearerTokenHandler = bearerTokenHandler,
            apiLogger = apiLogger
        ),
    private val disableWebsocket: Boolean = false
) {

    fun notesApi(): NotesApi = NotesApi(client, baseUrl)

    fun liveEditionManager(): SharedEditionManager = if (disableWebsocket) {
        MockWebsocketEditionManager()
    } else {
        WebsocketEditionManager(host = "0.0.0.0", client = client, json = writeopiaJson)
    }
}

private object ApiInjectorDefaults {
    fun httpClient(
        json: Json = writeopiaJson,
        bearerTokenHandler: BearerTokenHandler,
        apiLogger: Logger,
    ) = HttpClient {
        install(ContentNegotiation) {
            json(json = json)
        }

        install(WebSockets)

        install(Logging) {
            logger = apiLogger
            level = LogLevel.ALL
//                sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        bearerTokenHandler.getIdToken() ?: "",
                        "",
                    )
                }
            }
        }
    }
}
