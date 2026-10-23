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
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import kotlinx.serialization.json.Json

/**
 * Creates an HTTP client for web platforms with credentials enabled.
 *
 * This configuration ensures that HttpOnly cookies are sent with the API requests,
 * enabling secure cookie-based authentication.
 *
 * Note: Ktor's JS engine doesn't expose a direct way to set credentials mode.
 * We override the global fetch function to include credentials with the API requests.
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
                level = LogLevel.HEADERS
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }
    }

    /**
     * Overrides the global fetch function to include credentials (cookies) with the requests
     * to the API. This is necessary because Ktor's JS engine doesn't expose a way to configure
     * the fetch credentials mode directly.
     */
    private fun applyFetchCredentialsOverride() {
        if (fetchOverrideApplied) return
        fetchOverrideApplied = true

        val apiBaseUrl = runCatching { WriteopiaConnectionInjector.getBaseUrl() }.getOrNull()
        overrideFetchCredentials(apiBaseUrl)
    }
}

/**
 * Only the page's origin and the API's origin get credentials. Any other origin, like the CDN
 * of the header pictures, answers without Access-Control-Allow-Credentials, so the browser
 * would block a request sent to it with credentials.
 */
private fun overrideFetchCredentials(apiBaseUrl: String?): Unit = js(
    """
    (function() {
        if (typeof window === 'undefined' || !window.fetch || window._writeopiaFetchOverrideApplied) return;

        var credentialOrigins = [window.location.origin];
        if (apiBaseUrl) {
            try {
                credentialOrigins.push(new URL(apiBaseUrl, window.location.href).origin);
            } catch (e) {}
        }

        window._writeopiaOriginalFetch = window.fetch;
        window.fetch = function(resource, init) {
            var newInit = Object.assign({}, init || {});
            if (!newInit.credentials) {
                var url = typeof resource === 'string' ? resource : (resource && resource.url) || String(resource);
                try {
                    if (credentialOrigins.indexOf(new URL(url, window.location.href).origin) !== -1) {
                        newInit.credentials = 'include';
                    }
                } catch (e) {}
            }
            return window._writeopiaOriginalFetch(resource, newInit);
        };
        window._writeopiaFetchOverrideApplied = true;
    })()
    """
)
