package io.writeopia.api.typesafe.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderSuggestionsServiceTest {

    private val folders = listOf(
        FolderCandidate("f-meetings", "Meetings"),
        FolderCandidate("f-cooking", "Cooking"),
        FolderCandidate("f-travel", "Travel"),
    )

    private fun service(body: String, onCall: () -> Unit = {}) = FolderSuggestionsService(
        client = TypeSafeClient(
            apiKey = "secret",
            engine = MockEngine {
                onCall()
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )
    )

    @Test
    fun `one noul per folder, keyed by its index, with the names in the state`() {
        val request = FolderSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", folders = folders)

        val questions = FolderSuggestionQuestions.questionsFor(request)
        val state = FolderSuggestionQuestions.stateFor(request)

        assertEquals(setOf("folder_0", "folder_1", "folder_2"), questions.keys)
        assertTrue(questions.getValue("folder_1").instructions.jsonPrimitive.content.contains("Cooking"))
        assertEquals(3, state.getValue("folders").jsonArray.size)
        assertEquals("Pasta", state.getValue("document_title").jsonPrimitive.content)
    }

    @Test
    fun `only the first ten folders are asked about`() {
        val many = (1..15).map { FolderCandidate("f$it", "Folder $it") }

        val questions = FolderSuggestionQuestions.questionsFor(FolderSuggestionsRequest(folders = many))

        assertEquals(FolderSuggestionsRequest.MAX_FOLDERS, questions.size)
        assertTrue(questions.getValue("folder_9").instructions.jsonPrimitive.content.contains("Folder 10"))
    }

    @Test
    fun `the folders above the threshold come back by id, best first`() = runTest {
        val service = service(
            """
            {"model":"jev","answers":{
              "folder_0":{"type":"noul","noul":0.1},
              "folder_1":{"type":"noul","noul":0.97},
              "folder_2":{"type":"noul","noul":0.85},
              "folder_7":{"type":"noul","noul":0.99}
            },"usage":{"input_tokens":80,"output_tokens":3}}
            """.trimIndent()
        )

        val (response, usage) = service.suggest(
            FolderSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", folders = folders)
        )

        assertEquals(listOf("f-cooking", "f-travel"), response.suggestions.map { it.folderId })
        assertEquals(listOf("Cooking", "Travel"), response.suggestions.map { it.folderName })
        assertEquals(0.97, response.suggestions.first().probability)
        assertEquals(80, usage.inputTokens)
    }

    @Test
    fun `the backend's candidates replace the app's and carry their profile`() = runTest {
        val service = service(
            """
            {"model":"jev","answers":{"folder_0":{"type":"noul","noul":0.9}},"usage":{"input_tokens":1,"output_tokens":1}}
            """.trimIndent()
        )
        val ranked = listOf(
            FolderCandidate(
                "f-pasta",
                "Pasta",
                path = "Cooking / Italian",
                recentTitles = listOf("Carbonara", "Pesto"),
                tags = listOf("recipes"),
            ),
            FolderCandidate("f-bare", "Bare"),
        )
        val request = FolderSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", folders = folders)

        val state = FolderSuggestionQuestions.stateFor(request, ranked)
        val profile = state.getValue("folders").jsonArray[0].jsonObject
        assertEquals("Cooking / Italian", profile.getValue("path").jsonPrimitive.content)
        assertEquals(2, profile.getValue("recent_documents").jsonArray.size)
        assertEquals("recipes", profile.getValue("tags").jsonArray[0].jsonPrimitive.content)
        assertEquals(setOf("name"), state.getValue("folders").jsonArray[1].jsonObject.keys)

        val (response, _) = service.suggest(request, ranked)
        assertEquals("f-pasta", response.suggestions.single().folderId)
        assertEquals("Pasta", response.suggestions.single().folderName)
    }

    @Test
    fun `when no folder passes the threshold the answer is empty`() = runTest {
        val service = service(
            """
            {"model":"jev","answers":{
              "folder_0":{"type":"noul","noul":0.4},
              "folder_1":{"type":"noul","noul":0.6},
              "folder_2":{"type":"noul","noul":0.79}
            },"usage":{"input_tokens":80,"output_tokens":3}}
            """.trimIndent()
        )

        val (response, _) = service.suggest(
            FolderSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", folders = folders)
        )

        assertTrue(response.suggestions.isEmpty())
        assertEquals(null, response.error)
    }

    @Test
    fun `without folders nothing is asked`() = runTest {
        var calls = 0
        val service = service("""{"model":"jev","answers":{}}""") { calls++ }

        val (response, _) = service.suggest(FolderSuggestionsRequest(documentText = "Boil water."))

        assertTrue(response.suggestions.isEmpty())
        assertEquals(0, calls)
    }
}
