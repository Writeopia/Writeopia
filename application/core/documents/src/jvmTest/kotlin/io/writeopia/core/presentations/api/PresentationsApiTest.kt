package io.writeopia.core.presentations.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.core.presentations.PresentationException
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PresentationsApiTest {

    private val presentationJson = """
        {"id":"p1","documentId":"d1","title":"Sky","createdAt":1700000000000,"slides":[
          {"title":"Sky","content":[
            {"id":"s2","type":{"name":"unordered_list_item","number":16},"text":"Second","position":2.0},
            {"id":"s1","type":{"name":"message","number":0},"text":"First","position":1.0}
          ]},
          {"title":"Bye","content":[]}
        ]}
    """.trimIndent()

    /** The requests the API sent, checked after the call: an assertion inside the engine is lost. */
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        PresentationsApi(
            HttpClient(
                MockEngine { request ->
                    requests.add(request)
                    handler(request)
                }
            ),
            "https://api.example.com"
        )

    @Test
    fun `lists the presentations of a document`() = runTest {
        val api = api {
            respond("""{"presentations":[$presentationJson]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = api.presentations("d1", "w1")

        assertEquals("/api/docs/workspace/w1/document/d1/presentations", requests.single().url.encodedPath)
        assertEquals(HttpMethod.Get, requests.single().method)
        val presentations = assertIs<ResultData.Complete<*>>(result).data as List<*>
        val presentation = presentations.single() as io.writeopia.sdk.models.presentation.Presentation
        assertEquals("Sky", presentation.title)
        assertEquals("w1", presentation.workspaceId)
        assertEquals(listOf("Sky", "Bye"), presentation.slides.map { it.title })
        assertEquals(
            listOf("First", "Second"),
            presentation.slides[0].content.map {
                it.text
            },
            "steps come in the order of their positions"
        )
    }

    @Test
    fun `generates a presentation`() = runTest {
        val api = api {
            respond(
                """{"presentation":$presentationJson}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = api.generatePresentation("d1", "w1")

        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals(
            "p1",
            assertIs<ResultData.Complete<*>>(result).let {
                (it.data as io.writeopia.sdk.models.presentation.Presentation).id
            }
        )
    }

    @Test
    fun `the error of the backend is reported`() = runTest {
        val api = api {
            respond(
                """{"error":"AI didn't return any slide"}""",
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = api.generatePresentation("d1", "w1")

        val error = assertIs<ResultData.Error<*>>(result)
        assertEquals("AI didn't return any slide", assertIs<PresentationException>(error.exception).message)
    }

    @Test
    fun `a missing presentation is null and a deleted one completes`() = runTest {
        val api = api { request ->
            if (request.method == HttpMethod.Delete) respond("", HttpStatusCode.OK) else respond("Not found", HttpStatusCode.NotFound)
        }

        assertEquals(null, assertIs<ResultData.Complete<*>>(api.presentation("missing", "w1")).data)
        assertTrue(api.deletePresentation("p1", "w1") is ResultData.Complete)
    }
}
