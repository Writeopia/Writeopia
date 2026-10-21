package io.writeopia.utils

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.NextPositionCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextPositionCalculatorTest {

    @Test
    fun `calculate should return empty map for empty input`() {
        val result = NextPositionCalculator.calculate(emptyMap())
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `calculate should set nextPosition to null for single element`() {
        val stories = mapOf(
            0L to StoryStep(type = StoryTypes.TEXT.type, text = "Hello")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertNull(result[0L]?.nextPosition)
        assertNull(result[0L]?.previousPosition)
    }

    @Test
    fun `calculate should set correct nextPosition for two elements`() {
        val stories = mapOf(
            0L to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1L to StoryStep(type = StoryTypes.TEXT.type, text = "Content")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(1L, result[0L]?.nextPosition)
        assertNull(result[0L]?.previousPosition)
        assertNull(result[1L]?.nextPosition)
        assertEquals(0L, result[1L]?.previousPosition)
    }

    @Test
    fun `calculate should handle non-sequential positions`() {
        val stories = mapOf(
            0L to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            15L to StoryStep(type = StoryTypes.TEXT.type, text = "Middle"),
            30L to StoryStep(type = StoryTypes.TEXT.type, text = "End")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(15L, result[0L]?.nextPosition)
        assertNull(result[0L]?.previousPosition)

        assertEquals(30L, result[15L]?.nextPosition)
        assertEquals(0L, result[15L]?.previousPosition)

        assertNull(result[30L]?.nextPosition)
        assertEquals(15L, result[30L]?.previousPosition)
    }

    @Test
    fun `calculate should handle multiple elements in sequence`() {
        val stories = mapOf(
            0L to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1L to StoryStep(type = StoryTypes.TEXT.type, text = "First"),
            2L to StoryStep(type = StoryTypes.TEXT.type, text = "Second"),
            3L to StoryStep(type = StoryTypes.TEXT.type, text = "Third")
        )

        val result = NextPositionCalculator.calculate(stories)

        // First element
        assertEquals(1L, result[0L]?.nextPosition)
        assertNull(result[0L]?.previousPosition)

        // Middle elements
        assertEquals(2L, result[1L]?.nextPosition)
        assertEquals(0L, result[1L]?.previousPosition)

        assertEquals(3L, result[2L]?.nextPosition)
        assertEquals(1L, result[2L]?.previousPosition)

        // Last element
        assertNull(result[3L]?.nextPosition)
        assertEquals(2L, result[3L]?.previousPosition)
    }
}
