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

/**
 * Creates an HTTP client for web platforms with credentials enabled.
 *
 * This configuration ensures that HttpOnly cookies are sent with all requests,
 * enabling secure cookie-based authentication.
 *
 * Note: Ktor's JS engine doesn't expose a direct way to set credentials mode.
 * We override the global fetch function to include credentials with all requests.
 * See: https://youtrack.jetbrains.com/issue/KTOR-2886
 */
actual object ApiInjectorDefaults {

    private var fetchOverrideApplied = false

    actual fun httpClient(
        json: Json,
        apiLogger: Logger
    ): HttpClient {
        // Apply the fetch override once to ensure credentials are included
        applyFetchCredentialsOverride()

        return HttpClient(Js) {
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
        }
    }

    /**
     * Overrides the global fetch function to include credentials (cookies) with all requests.
     * This is necessary because Ktor's JS engine doesn't expose a way to configure
     * the fetch credentials mode directly.
     */
    private fun applyFetchCredentialsOverride() {
        if (fetchOverrideApplied) return
        fetchOverrideApplied = true

        js(
            """
            if (typeof window !== 'undefined' && window.fetch && !window._writeopiaFetchOverrideApplied) {
                window._writeopiaOriginalFetch = window.fetch;
                window.fetch = function(resource, init) {
                    var newInit = Object.assign({}, init || {});
                    if (!newInit.credentials) {
                        newInit.credentials = 'include';
                    }
                    return window._writeopiaOriginalFetch(resource, newInit);
                };
                window._writeopiaFetchOverrideApplied = true;
            }
            """
        )
    }
}
