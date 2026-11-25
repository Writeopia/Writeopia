package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleDriveFileStoreTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun store(
        tokens: (Boolean) -> String? = { "token" },
        handler: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): GoogleDriveFileStore {
        val engine = MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            if (status.value >= 400) respondError(status, body) else respond(body, status, jsonHeaders)
        }
        return GoogleDriveFileStore(HttpClient(engine)) { force -> tokens(force) }
    }

    @Test
    fun `the listing follows every page of the app data folder`() = runBlocking {
        val store = store { request ->
            if (request.url.parameters["pageToken"] == null) {
                HttpStatusCode.OK to """{"files":[{"id":"1","name":"a.json","version":"3"}],"nextPageToken":"p2"}"""
            } else {
                HttpStatusCode.OK to """{"files":[{"id":"2","name":"b.json","version":"7","extra":1}]}"""
            }
        }

        val files = store.list()

        assertEquals(listOf(CloudFile("1", "a.json", "3"), CloudFile("2", "b.json", "7")), files)
        assertTrue(requests.all { it.url.parameters["spaces"] == "appDataFolder" })
        assertEquals("Bearer token", requests.first().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a new file goes in the app data folder with its content`() = runBlocking {
        val store = store { HttpStatusCode.OK to """{"id":"9","name":"doc.d.x.json","version":"1"}""" }

        val created = store.create("doc.d.x.json", """{"id":"x"}""")

        assertEquals(CloudFile("9", "doc.d.x.json", "1"), created)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("multipart", request.url.parameters["uploadType"])
        val body = (request.body as TextContent).text
        assertTrue(body.contains(""""parents":["appDataFolder"]"""), body)
        assertTrue(body.contains("""{"id":"x"}"""), body)
    }

    @Test
    fun `on 401 the token is refreshed once and the request made again`() = runBlocking {
        var calls = 0
        val store = store(tokens = { force -> if (force) "fresh" else "stale" }) { request ->
            calls++
            if (request.headers[HttpHeaders.Authorization] == "Bearer stale") {
                HttpStatusCode.Unauthorized to "expired"
            } else {
                HttpStatusCode.OK to "content"
            }
        }

        assertEquals("content", store.download("1"))
        assertEquals(2, calls)
    }

    @Test
    fun `an error is thrown, and a missing connection says so`() = runBlocking {
        val failing = store { HttpStatusCode.InternalServerError to "boom" }
        assertFailsWith<CloudStoreException> { failing.download("1") }

        val disconnected = store(tokens = { null }) { HttpStatusCode.OK to "" }
        val error = assertFailsWith<CloudStoreException> { disconnected.list() }
        assertTrue(error.unauthorized)
    }

    @Test
    fun `deleting a file already gone is not an error`() = runBlocking {
        val store = store { HttpStatusCode.NotFound to "not found" }
        store.delete("1")
    }
}
