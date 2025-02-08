package io.writeopia.api.gateway

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.writeopia.api.geteway.module
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.data.DocumentApi
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun `it should be possible to save and query document by id`() = testApplication {
        application {
            module()
        }

        val client = defaultClient()

        val documentApi = DocumentApi(
            id = "testiaskkakakaka",
            title = "Test Note",
            userId = "some user",
            parentId = "parentIdddd",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )

        val response = client.post("/${EndPoints.documents()}") {
            contentType(ContentType.Application.Json)
            setBody(documentApi)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get("${EndPoints.documents()}/${documentApi.id}")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(documentApi, response1.body())
    }

    @Test
    fun `it should be possible to save and query documents by parent id`() = testApplication {
        application {
            module()
        }

        val client = defaultClient()

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            userId = "some user",
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )

        val response = client.post("/${EndPoints.documents()}") {
            contentType(ContentType.Application.Json)
            setBody(documentApi)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 = client.get("${EndPoints.documents()}/parent/${documentApi.parentId}")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(listOf(documentApi), response1.body())
    }

    @Test
    fun `it should be possible to save and query ids by parent id`() = testApplication {
        application {
            module()
        }

        val client = defaultClient()

        val documentApi = DocumentApi(
            id = "testias",
            title = "Test Note",
            userId = "some user",
            parentId = "parentId",
            isLocked = false,
            createdAt = 1000L,
            lastUpdatedAt = 2000L
        )

        val response = client.post("/${EndPoints.documents()}") {
            contentType(ContentType.Application.Json)
            setBody(documentApi)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val response1 =
            client.get("${EndPoints.documents()}/parent/id/${documentApi.parentId}")

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(listOf(documentApi.id), response1.body())
    }
}
