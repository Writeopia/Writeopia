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
    fun `a short document goes whole around the target`() {
        val context = AiCommandContext.build(document(paragraphs = 6), targetPositions = listOf(3.0, 4.0))

        assertEquals("p3\np4", context.targetText)
        assertEquals(listOf(3.0, 4.0), context.targetPositions)
        assertEquals("p1\np2", context.textBefore)
        assertEquals("p5\np6", context.textAfter)
    }

    @Test
    fun `a long document is cut to the paragraphs around the target`() {
        val context = AiCommandContext.build(document(paragraphs = 50), targetPositions = listOf(25.0))

        assertEquals("p25", context.targetText)
        assertEquals((15..24).joinToString("\n") { "p$it" }, context.textBefore)
        assertEquals((26..35).joinToString("\n") { "p$it" }, context.textAfter)
    }

    @Test
    fun `without a target the whole document is the context`() {
        val context = AiCommandContext.build(document(paragraphs = 40), targetPositions = emptyList())

        assertEquals("", context.targetText)
        assertEquals(emptyList(), context.targetPositions)
        assertEquals((1..40).joinToString("\n") { "p$it" }, context.textBefore)
        assertEquals("", context.textAfter)
    }

    @Test
    fun `blank and ephemeral stories are left out`() {
        val stories = document(paragraphs = 3) +
            mapOf(
                1.5 to StoryStep(type = StoryTypes.TEXT.type, text = "   "),
                2.5 to StoryStep(type = StoryTypes.LOADING.type, text = "spinner", ephemeral = true),
            )

        val context = AiCommandContext.build(stories, targetPositions = listOf(1.5, 2.0))

        assertEquals("p2", context.targetText)
        assertEquals(listOf(2.0), context.targetPositions)
        assertEquals("p1", context.textBefore)
        assertEquals("p3", context.textAfter)
    }

    @Test
    fun `the title is not part of the context`() {
        val context = AiCommandContext.build(document(paragraphs = 2), targetPositions = listOf(1.0))

        assertEquals("", context.textBefore)
        assertEquals("p2", context.textAfter)
    }
}
