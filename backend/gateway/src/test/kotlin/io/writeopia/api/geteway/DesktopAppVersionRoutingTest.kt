package io.writeopia.api.geteway

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
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAppVersionRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun Application.testModule() {
        install(ContentNegotiation) {
            json()
        }
        routing {
            desktopAppVersionRoute()
        }
    }

    @Test
    fun `it should return the latest desktop app version without authentication`() = testApplication {
        application {
            testModule()
        }

        val response = client.get("/${EndPoints.desktopAppVersion()}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<DesktopAppVersionResponse>(response.bodyAsText())
        assertEquals(DesktopAppVersionConfig.CURRENT, body.version)
    }

    @Test
    fun `it should mark the response as publicly cacheable for the CDN`() = testApplication {
        application {
            testModule()
        }

        val response = client.get("/${EndPoints.desktopAppVersion()}")

        val cacheControl = requireNotNull(response.headers[HttpHeaders.CacheControl]) {
            "Cache-Control header should be present"
        }
        assertTrue(cacheControl.contains("public"))
        assertTrue(cacheControl.contains("max-age=3600"))
    }
}
