package io.writeopia.api.typesafe.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.app.dto.writingagent.TagCandidate
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagSuggestionsServiceTest {

    private val tags = listOf(
        TagCandidate("t-meetings", "Meetings", "Notes and minutes of meetings"),
        TagCandidate("t-cooking", "Cooking", "Recipes and kitchen tips"),
        TagCandidate("t-travel", "Travel", ""),
    )

    private fun service(body: String, limit: Int = TagSuggestionsService.DEFAULT_LIMIT, onCall: () -> Unit = {}) =
        TagSuggestionsService(
            client = TypeSafeClient(
                apiKey = "secret",
                engine = MockEngine {
                    onCall()
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            ),
            limit = limit,
        )

    @Test
    fun `one noul per tag, keyed by its index, with names and explanations in the state`() {
        val request = TagSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", tags = tags)

        val questions = TagSuggestionQuestions.questionsFor(request)
        val state = TagSuggestionQuestions.stateFor(request)

        assertEquals(setOf("tag_0", "tag_1", "tag_2"), questions.keys)
        val cooking = questions.getValue("tag_1").instructions.jsonPrimitive.content
        assertTrue(cooking.contains("Cooking") && cooking.contains("Recipes and kitchen tips"))
        assertTrue(!questions.getValue("tag_2").instructions.jsonPrimitive.content.contains("which means"))
        val stateTags = state.getValue("tags").jsonArray
        assertEquals(3, stateTags.size)
        assertEquals("Recipes and kitchen tips", stateTags[1].jsonObject.getValue("description").jsonPrimitive.content)
        assertEquals("Pasta", state.getValue("document_title").jsonPrimitive.content)
    }

    @Test
    fun `only the first thirty tags are asked about`() {
        val many = (1..40).map { TagCandidate("t$it", "Tag $it") }

        val questions = TagSuggestionQuestions.questionsFor(TagSuggestionsRequest(tags = many))

        assertEquals(TagSuggestionsRequest.MAX_TAGS, questions.size)
    }

    @Test
    fun `the tags above the threshold come back by id, best first, up to the limit`() = runTest {
        val body = """
            {"model":"jev","answers":{
              "tag_0":{"type":"noul","noul":0.9},
              "tag_1":{"type":"noul","noul":0.97},
              "tag_2":{"type":"noul","noul":0.85},
              "tag_7":{"type":"noul","noul":0.99}
            },"usage":{"input_tokens":80,"output_tokens":3}}
        """.trimIndent()

        val (response, usage) = service(body).suggest(
            TagSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", tags = tags)
        )
        assertEquals(listOf("t-cooking", "t-meetings", "t-travel"), response.suggestions.map { it.tagId })
        assertEquals(0.97, response.suggestions.first().probability)
        assertEquals(80, usage.inputTokens)

        val (limited, _) = service(body, limit = 2).suggest(
            TagSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", tags = tags)
        )
        assertEquals(listOf("t-cooking", "t-meetings"), limited.suggestions.map { it.tagId })
    }

    @Test
    fun `when no tag passes the threshold the answer is empty`() = runTest {
        val service = service(
            """
            {"model":"jev","answers":{
              "tag_0":{"type":"noul","noul":0.4},
              "tag_1":{"type":"noul","noul":0.6},
              "tag_2":{"type":"noul","noul":0.79}
            },"usage":{"input_tokens":80,"output_tokens":3}}
            """.trimIndent()
        )

        val (response, _) = service.suggest(
            TagSuggestionsRequest(documentTitle = "Pasta", documentText = "Boil water.", tags = tags)
        )

        assertTrue(response.suggestions.isEmpty())
        assertEquals(null, response.error)
    }

    @Test
    fun `without tags nothing is asked`() = runTest {
        var calls = 0
        val service = service("""{"model":"jev","answers":{}}""") { calls++ }

        val (response, _) = service.suggest(TagSuggestionsRequest(documentText = "Boil water."))

        assertTrue(response.suggestions.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `the limit comes from the environment or falls back`() {
        assertEquals(5, TagSuggestionsService.limitFromEnv(null))
        assertEquals(8, TagSuggestionsService.limitFromEnv("8"))
        assertEquals(5, TagSuggestionsService.limitFromEnv("zero"))
    }
}
