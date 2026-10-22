@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.embeddings

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentPlainTextTest {

    private fun document(title: String, vararg steps: Pair<StoryTypes, String>): Document {
        val now = Clock.System.now()
        return Document(
            id = "doc",
            title = title,
            content = steps.withIndex().associate { (index, step) ->
                Rank.at(index) to StoryStep(type = step.first.type, text = step.second)
            },
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "ws",
            parentId = "root",
        )
    }

    @Test
    fun `the title is apart from the body and only text steps count`() {
        val text = DocumentPlainText.of(
            document(
                "Pasta",
                StoryTypes.TITLE to "Pasta",
                StoryTypes.TEXT to "Boil water.",
                StoryTypes.IMAGE to "ignored.png",
                StoryTypes.CHECK_ITEM to "Buy salt",
                StoryTypes.TEXT to "   ",
            )
        )

        assertEquals("Pasta", text.title)
        assertEquals("Boil water.\nBuy salt", text.body)
    }

    @Test
    fun `the title step fills a missing title`() {
        val text = DocumentPlainText.of(document("", StoryTypes.TITLE to "From the step", StoryTypes.TEXT to "Body"))

        assertEquals("From the step", text.title)
    }

    @Test
    fun `the embedding input keeps the title under the cap`() {
        val input = DocumentPlainText.embeddingInput("Title", "x".repeat(DocumentPlainText.EMBEDDING_INPUT_LIMIT * 2))

        assertEquals(DocumentPlainText.EMBEDDING_INPUT_LIMIT, input.length)
        assertTrue(input.startsWith("Title\n\n"))
        assertEquals("Only a title", DocumentPlainText.embeddingInput("Only a title", ""))
        assertEquals("", DocumentPlainText.embeddingInput(" ", ""))
    }

    @Test
    fun `the prompt text heads each document with its title and separates them`() {
        val prompt = DocumentPlainText.promptText(
            listOf(
                document("First", StoryTypes.TITLE to "First", StoryTypes.TEXT to "One"),
                document("Second"),
            )
        )

        assertEquals("# First\nFirst\nOne\n\n---\n\n# Second", prompt)
    }
}
