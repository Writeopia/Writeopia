package io.writeopia.api.auth

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.writeopia.sdk.serialization.json.writeopiaJson

fun ApplicationTestBuilder.defaultClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}

/**
 * Creates a client that stores and sends cookies automatically.
 * This is needed for testing HttpOnly cookie-based authentication.
 */
fun ApplicationTestBuilder.cookieClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
    install(HttpCookies)
}
