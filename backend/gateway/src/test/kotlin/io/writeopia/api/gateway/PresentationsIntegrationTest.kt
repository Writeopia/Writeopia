package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.api.geteway.module
import io.writeopia.sdk.serialization.request.GeneratePresentationRequest
import io.writeopia.sdk.serialization.response.PresentationsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationsIntegrationTest {

    private val db = configurePersistence()

    @Test
    fun `a document without presentations lists none`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val response = client.get("/api/docs/workspace/ws-presentations/document/no-presentations/presentations")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<PresentationsResponse>().presentations.isEmpty())
    }

    @Test
    fun `an unknown presentation is not found`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()

        assertEquals(HttpStatusCode.NotFound, client.get("/api/docs/workspace/ws-presentations/presentation/missing").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/docs/workspace/ws-presentations/presentation/missing").status)
    }

    @Test
    fun `generating needs the cloud AI`() = testApplication {
        application {
            module(db, debugMode = true)
        }

        val client = defaultClient()
        val response = client.post("/api/docs/workspace/ws-presentations/document/some-doc/presentations") {
            contentType(ContentType.Application.Json)
            setBody(GeneratePresentationRequest())
        }

        // Without WRITEOPIA_USE_CLOUD_AI there's no GenAI service; with it but unconfigured, GenAI is unavailable.
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }
}
