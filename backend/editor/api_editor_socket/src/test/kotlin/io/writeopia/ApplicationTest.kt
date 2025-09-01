package io.writeopia

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    @Ignore
    fun itShouldBePossibleToGetAnExampleDocument() {
        testApplication {
            defaultConfig()
            val client = clientClient()

            client.get("/api/document/example").run {
                assertEquals(HttpStatusCode.OK, status)
                body<DocumentApi>()
            }
        }
    }

    @Test
    @Ignore
    fun itShouldBePossibleToGetIntroNotes() = testApplication {
        defaultConfig()
        val client = clientClient()

        client.get("/api/${EndPoints.introNotes()}").run {
            assertEquals(HttpStatusCode.OK, status)
            body<List<DocumentApi>>()
        }
    }

    @Test
    @Ignore
    fun itShouldBePossibleToSaveAndGetDocuments() = testApplication {
        defaultConfig()
        val client = clientClient()
        val id = "mockId"

        client.post("/api/document") {
            contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
            setBody(DocumentApi(id = id, workspaceId = "userId"))
        }.run {
            assertEquals(HttpStatusCode.Accepted, status)
        }

        client.get("/api/document/$id").run {
            assertEquals(HttpStatusCode.OK, status)
            body<DocumentApi>()
        }
    }
}

private fun ApplicationTestBuilder.clientClient() = createClient {
    install(ContentNegotiation) {
        json(json = writeopiaJson)
    }
}

private fun ApplicationTestBuilder.defaultConfig() {
    application {
        module(byPassAuth = true, forceDbInMemory = true)
    }
}
