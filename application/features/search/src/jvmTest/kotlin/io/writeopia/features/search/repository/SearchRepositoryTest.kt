package io.writeopia.features.search.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.features.search.api.SearchApi
import io.writeopia.models.interfaces.search.FolderSearch
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.search.DocumentSearch
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.serialization.response.SearchResponse
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SearchRepositoryTest {

    private val workspace = Workspace.disconnectedWorkspace().copy(id = "ws")
    private val authRepository = mockk<AuthRepository> {
        every { listenForWorkspace() } returns flowOf(workspace)
    }

    private val folderSearch = object : FolderSearch {
        override suspend fun search(query: String, workspaceId: String): List<Folder> = emptyList()

        override suspend fun getLastUpdated(): List<Folder> = emptyList()
    }

    private val titleSearch = object : DocumentSearch {
        override suspend fun search(query: String, workspaceId: String): List<Document> =
            listOf(document("by-title", "Zebra care"))

        override suspend fun getLastUpdatedAt(workspaceId: String): List<Document> = emptyList()
    }

    @Test
    fun `the local search lists the documents found by title, then the ones found by their words`() = runTest {
        val repository = SearchRepository(
            folderSearch,
            titleSearch,
            searchApi(SearchResponse()),
            authRepository,
            documentContentSearch = { query, workspaceId ->
                assertEquals("zebra" to "ws", query to workspaceId)
                listOf(SearchItem.DocumentInfo("by-content", "Notes", snippet = "The zebra is striped"))
            }
        )

        assertEquals(
            listOf(
                SearchItem.DocumentInfo("by-title", "Zebra care"),
                SearchItem.DocumentInfo("by-content", "Notes", snippet = "The zebra is striped"),
            ),
            repository.searchNotesAndFoldersLocally("zebra").first()
        )
    }

    @Test
    fun `the backend's matching step becomes the snippet, a document without content has none`() = runTest {
        val byContent = document("remote-content", "Weekly").copy(
            content = mapOf(Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "Feed the zebra"))
        )
        val byTitle = document("remote-title", "Zebra lab")
        val repository = SearchRepository(
            folderSearch,
            titleSearch,
            searchApi(SearchResponse(documents = listOf(byContent.toApi(), byTitle.toApi()))),
            authRepository,
        )

        assertEquals(
            listOf(
                SearchItem.DocumentInfo("remote-content", "Weekly", snippet = "Feed the zebra"),
                SearchItem.DocumentInfo("remote-title", "Zebra lab"),
            ),
            repository.searchNotesAndFoldersRemotely("zebra").first()
        )
    }

    private fun searchApi(response: SearchResponse): SearchApi {
        val engine = MockEngine {
            respond(
                content = writeopiaJson.encodeToString<SearchResponse>(response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(writeopiaJson) } }
        return SearchApi(client, "https://api.example.com")
    }

    private fun document(id: String, title: String): Document = Document(
        id = id,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(1_000),
        lastUpdatedAt = Instant.fromEpochMilliseconds(2_000),
        lastSyncedAt = null,
        workspaceId = "ws",
        parentId = "root",
    )
}
