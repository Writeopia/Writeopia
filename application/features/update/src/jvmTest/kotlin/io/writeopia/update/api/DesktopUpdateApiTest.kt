package io.writeopia.update.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUpdateApiTest {

    @Test
    fun `reads latest version through ktor content negotiation`() = runTest {
        var requestedUrl: String? = null
        val client = HttpClient(
            MockEngine { request ->
                requestedUrl = request.url.toString()
                respond(
                    content = """{"version":"0.48.0"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = DesktopUpdateApi(
            client = client,
            baseUrl = "https://writeopia.io/"
        ).latestVersion()

        assertEquals("0.48.0", response.version)
        assertEquals(
            "https://writeopia.io/api/app/desktop-version",
            requestedUrl
        )
    }
}
