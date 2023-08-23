package com.github.leandroborgesferreira.storyteller.network.injector

import com.github.leandroborgesferreira.storyteller.network.notes.NotesApi
import com.github.leandroborgesferreira.storyteller.network.oauth.BearerTokenHandler
import com.github.leandroborgesferreira.storyteller.serialization.json.storyTellerJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiInjector(
    private val apiLogger: Logger,
    private val bearerTokenHandler: BearerTokenHandler,
    private val client: () -> HttpClient = {
        ApiInjectorDefaults.httpClientJson(
            bearerTokenHandler = bearerTokenHandler,
            apiLogger = apiLogger
        )
    },
    private val baseUrl: String = ApiInjectorDefaults.baseUrl(),
) {

    fun notesApi(): NotesApi = NotesApi(client, baseUrl)
}

internal object ApiInjectorDefaults {
    fun httpClientJson(
        json: Json = storyTellerJson,
        bearerTokenHandler: BearerTokenHandler,
        apiLogger: Logger
    ) =
        HttpClient {
            install(ContentNegotiation) {
                json(json = json)
            }

            install(Logging) {
                logger = apiLogger
                level = LogLevel.ALL
//                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(
                            bearerTokenHandler.getIdToken(),
                            bearerTokenHandler.getRefreshToken()
                        )
                    }
                }
            }
        }

    fun baseUrl(): String = "https://d8dbeudrae.execute-api.us-east-1.amazonaws.com/prod"
}
