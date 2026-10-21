package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class AiCommandContextTest {

    private fun document(paragraphs: Int): Map<Double, StoryStep> = buildMap {
        put(0.0, StoryStep(type = StoryTypes.TITLE.type, text = "Title"))
        repeat(paragraphs) { index ->
            put((index + 1).toDouble(), StoryStep(type = StoryTypes.TEXT.type, text = "p${index + 1}"))
        }
    }

    @Test
    fun `the selected lines and the whole document go together`() {
        val context = AiCommandContext.build(document(paragraphs = 6), selectedPositions = listOf(4.0, 3.0))

        assertEquals("p3\np4", context.selectedText)
        assertEquals(listOf(3.0, 4.0), context.selectedPositions)
        assertEquals((1..6).joinToString("\n") { "p$it" }, context.documentText)
    }

    @Test
    fun `without a selection only the document goes`() {
        val context = AiCommandContext.build(document(paragraphs = 3), selectedPositions = emptyList())

        assertEquals("", context.selectedText)
        assertEquals(emptyList(), context.selectedPositions)
        assertEquals("p1\np2\np3", context.documentText)
    }

    @Test
    fun `blank and ephemeral stories are left out`() {
        val stories = document(paragraphs = 3) +
            mapOf(
                1.5 to StoryStep(type = StoryTypes.TEXT.type, text = "   "),
                2.5 to StoryStep(type = StoryTypes.LOADING.type, text = "spinner", ephemeral = true),
            )

        val context = AiCommandContext.build(stories, selectedPositions = listOf(1.5, 2.0))

        assertEquals("p2", context.selectedText)
        assertEquals(listOf(2.0), context.selectedPositions)
        assertEquals("p1\np2\np3", context.documentText)
    }

    @Test
    fun `the title is not part of the document text`() {
        val context = AiCommandContext.build(document(paragraphs = 1), selectedPositions = emptyList())

        assertEquals("p1", context.documentText)
    }
}
