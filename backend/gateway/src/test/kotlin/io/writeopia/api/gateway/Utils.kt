package io.writeopia.api.gateway

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.writeopia.sdk.serialization.json.writeopiaJson

fun ApplicationTestBuilder.defaultClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}
