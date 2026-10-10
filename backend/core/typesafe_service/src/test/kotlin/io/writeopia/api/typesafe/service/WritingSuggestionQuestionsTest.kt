package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.model.SystemOneQuestion
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WritingSuggestionQuestionsTest {

    @Test
    fun `while writing, every paragraph action is one noul keyed by its name`() {
        val questions = WritingSuggestionQuestions.questionsFor(WritingSuggestionScope.WRITING)

        assertEquals(
            WritingSuggestionAction.whileWriting().map { it.name }.toSet(),
            questions.keys
        )
        assertTrue(questions.values.all { it.type == SystemOneQuestion.NOUL })
    }

    @Test
    fun `the document title is decided by the app and never asked`() {
        val asked = WritingSuggestionQuestions.questionsFor(WritingSuggestionScope.WRITING).keys +
            WritingSuggestionQuestions.questionsFor(WritingSuggestionScope.DOCUMENT_OPENED).keys

        assertFalse(asked.contains(WritingSuggestionAction.DOCUMENT_TITLE.name))
    }

    @Test
    fun `when a document opens, only the document actions are asked`() {
        val questions = WritingSuggestionQuestions.questionsFor(WritingSuggestionScope.DOCUMENT_OPENED)

        assertEquals(setOf("TLDR", "CONCLUSION"), questions.keys)
    }

    @Test
    fun `the state carries the paragraph and the document`() {
        val state = WritingSuggestionQuestions.stateFor(
            WritingSuggestionsRequest(
                scope = WritingSuggestionScope.WRITING,
                text = "Run the following command.",
                blockType = "message",
                documentTitle = "Setup",
                documentText = "Setup\nRun the following command.",
            )
        )

        assertEquals("Run the following command.", state["paragraph"]?.jsonPrimitive?.content)
        assertEquals("message", state["paragraph_type"]?.jsonPrimitive?.content)
        assertEquals("Setup", state["document_title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the document text is cut to the limit`() {
        val state = WritingSuggestionQuestions.stateFor(
            WritingSuggestionsRequest(
                scope = WritingSuggestionScope.DOCUMENT_OPENED,
                documentText = "a".repeat(WritingSuggestionQuestions.DOCUMENT_TEXT_LIMIT + 100),
            )
        )

        assertEquals(WritingSuggestionQuestions.DOCUMENT_TEXT_LIMIT, state["document"]?.jsonPrimitive?.content?.length)
        assertFalse(state.containsKey("paragraph"))
    }
}
