package io.writeopia.auth.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceApiTest {

    @Test
    fun `the users of a workspace are cached for that workspace only`() = runTest {
        val requestedWorkspaces = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val workspaceId = request.url.encodedPath.removePrefix("/api/workspace/").removeSuffix("/users")
            requestedWorkspaces += workspaceId
            val name = if (workspaceId == "ws1") "Alice" else "Bob"
            respond(
                content = """[{"id":"u","email":"$name@mail.com","name":"$name","role":"ADMIN","username":"$name"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val workspaceApi = WorkspaceApi(client, "https://api.example.com")

        assertEquals(ResultData.Complete(listOf("Alice")), workspaceApi.getUsersOfWorkspace("ws1").value)
        // Switching workspaces shows that workspace's users instead of the cached ones.
        assertEquals(ResultData.Complete(listOf("Bob")), workspaceApi.getUsersOfWorkspace("ws2").value)
        assertEquals(ResultData.Complete(listOf("Alice")), workspaceApi.getUsersOfWorkspace("ws1").value)
        // The same workspace again comes from the cache.
        assertEquals(ResultData.Complete(listOf("Alice")), workspaceApi.getUsersOfWorkspace("ws1").value)

        assertEquals(listOf("ws1", "ws2", "ws1"), requestedWorkspaces)
    }
}
