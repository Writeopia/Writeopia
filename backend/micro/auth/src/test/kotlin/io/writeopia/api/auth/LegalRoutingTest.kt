package io.writeopia.api.auth

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.legal.LegalDocuments
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.LegalDocumentResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun Application.testModule() {
        install(ContentNegotiation) { json() }
        routing { legalRoute() }
    }

    @Test
    fun `it should serve the terms without authentication`() = testApplication {
        application { testModule() }

        val response = client.get("/api/${EndPoints.legalDocument("terms")}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<LegalDocumentResponse>(response.bodyAsText())
        assertEquals("terms", body.type)
        assertEquals("Terms of Service", body.title)
        assertEquals(LegalDocuments.VERSION, body.version)
        assertTrue(body.sections.isNotEmpty())
    }

    @Test
    fun `it should serve the privacy policy`() = testApplication {
        application { testModule() }

        val response = client.get("/api/${EndPoints.legalDocument("privacy")}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<LegalDocumentResponse>(response.bodyAsText())
        assertEquals("privacy", body.type)
        assertEquals("Privacy Policy", body.title)
    }

    @Test
    fun `it should be cacheable by the CDN`() = testApplication {
        application { testModule() }

        val response = client.get("/api/${EndPoints.legalDocument("terms")}")

        assertEquals("public, max-age=3600, s-maxage=86400", response.headers[HttpHeaders.CacheControl])
        assertEquals("\"terms-${LegalDocuments.VERSION}\"", response.headers[HttpHeaders.ETag])
    }

    @Test
    fun `it should answer not modified when the etag matches`() = testApplication {
        application { testModule() }
        val etag = "\"privacy-${LegalDocuments.VERSION}\""

        val response = client.get("/api/${EndPoints.legalDocument("privacy")}") {
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals("public, max-age=3600, s-maxage=86400", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `it should answer not found for an unknown document`() = testApplication {
        application { testModule() }

        val response = client.get("/api/${EndPoints.legalDocument("cookies")}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
