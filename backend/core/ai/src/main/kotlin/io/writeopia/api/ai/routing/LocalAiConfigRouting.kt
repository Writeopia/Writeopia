package io.writeopia.api.ai.routing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.writeopia.api.ai.model.LocalAiAutoConfigResponse
import io.writeopia.app.endpoints.EndPoints

private const val CACHE_MAX_AGE_SECONDS = 60 * 60 * 24

/**
 * Public endpoint with the configuration for auto-configuring Local AI (Ollama / llmman).
 * The response is the same for every user, so it is safe - and desirable - to cache it at the CDN.
 */
fun Routing.localAiConfigRoute() {
    get("/${EndPoints.aiLocalConfig()}") {
        call.response.header(HttpHeaders.CacheControl, "public, max-age=$CACHE_MAX_AGE_SECONDS")
        call.respond(HttpStatusCode.OK, LocalAiAutoConfigResponse())
    }
}
