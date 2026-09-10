package io.writeopia.api.ai.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.writeopia.sdk.serialization.response.LocalAiAutoConfigResponse
import io.writeopia.app.endpoints.EndPoints
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAiConfigRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun Application.testModule() {
        install(ContentNegotiation) {
            json()
        }
        routing {
            localAiConfigRoute()
        }
    }

    @Test
    fun `it should return the local ai configuration without requiring authentication`() = testApplication {
        application {
            testModule()
        }

        val response = client.get("/${EndPoints.aiLocalConfig()}")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<LocalAiAutoConfigResponse>(response.bodyAsText())
        assertEquals(LocalAiAutoConfigResponse(), body)
    }

    @Test
    fun `it should mark the response as publicly cacheable for the CDN`() = testApplication {
        application {
            testModule()
        }

        val response = client.get("/${EndPoints.aiLocalConfig()}")

        val cacheControl = requireNotNull(response.headers[HttpHeaders.CacheControl]) {
            "Cache-Control header should be present"
        }
        assertTrue(cacheControl.contains("public"), "Cache-Control should be public so it can be cached by the CDN")
        assertTrue(cacheControl.contains("max-age=86400"), "Cache-Control should define max-age=86400 (24 hours)")
    }

    @Test
    fun `it should return the same configuration for every request`() = testApplication {
        application {
            testModule()
        }

        val first = json.decodeFromString<LocalAiAutoConfigResponse>(
            client.get("/${EndPoints.aiLocalConfig()}").bodyAsText()
        )
        val second = json.decodeFromString<LocalAiAutoConfigResponse>(
            client.get("/${EndPoints.aiLocalConfig()}").bodyAsText()
        )

        assertEquals(first, second)
    }
}
