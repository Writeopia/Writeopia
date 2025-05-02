package io.writeopia.connection

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlinx.serialization.json.Json

object ApiInjectorDefaults {
    fun httpClient(
        json: Json = writeopiaJson,
        apiLogger: Logger,
    ) = HttpClient {
        install(HttpTimeout) {
//            requestTimeoutMillis = 300000
//            socketTimeoutMillis = 300000
        }

        install(ContentNegotiation) {
            json(json = json)
        }

        install(Logging) {
            logger = apiLogger
            level = LogLevel.ALL
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }
}

val wrWebClient: HttpClient =
    ApiInjectorDefaults.httpClient(
//            bearerTokenHandler = bearerTokenHandler,
        apiLogger =  Logger.Companion.DEFAULT
    )
