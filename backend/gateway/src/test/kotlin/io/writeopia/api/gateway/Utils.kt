package io.writeopia.api.gateway

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.writeopia.api.core.auth.utils.JwtConfig
import io.writeopia.sdk.serialization.json.writeopiaJson

fun ApplicationTestBuilder.defaultClient(
    debugMode: Boolean = System.getenv("WRITEOPIA_DEBUG_MODE")?.toBoolean() ?: false
) = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}
