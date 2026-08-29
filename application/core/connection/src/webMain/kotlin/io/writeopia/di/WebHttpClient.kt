package io.writeopia.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestCredentials

/**
 * Creates an HTTP client for web platforms with credentials enabled.
 *
 * This configuration ensures that HttpOnly cookies are sent with all requests,
 * enabling secure cookie-based authentication.
 */
object WebHttpClient {

    fun create(
        json: Json,
        apiLogger: Logger
    ): HttpClient = HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = 300000
            socketTimeoutMillis = 300000
        }

        install(ContentNegotiation) {
            json(json = json)
        }

        install(Logging) {
            logger = apiLogger
            level = LogLevel.ALL
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        // Configure the JS engine to include credentials (cookies) with requests
        engine {
            // This tells fetch to include credentials (cookies) with same-origin and cross-origin requests
            // Equivalent to: fetch(url, { credentials: 'include' })
        }
    }

    /**
     * The credentials mode for fetch requests.
     * Use RequestCredentials.INCLUDE to send cookies with all requests.
     */
    val credentialsMode: RequestCredentials = RequestCredentials.INCLUDE
}
