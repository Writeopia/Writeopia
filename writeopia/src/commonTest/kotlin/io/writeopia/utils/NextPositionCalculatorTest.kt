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
            0.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Hello")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertNull(result[0.0]?.nextPosition)
        assertNull(result[0.0]?.previousPosition)
    }

    @Test
    fun `calculate should set correct nextPosition for two elements`() {
        val stories = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Content")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(1.0, result[0.0]?.nextPosition)
        assertNull(result[0.0]?.previousPosition)
        assertNull(result[1.0]?.nextPosition)
        assertEquals(0.0, result[1.0]?.previousPosition)
    }

    @Test
    fun `calculate should handle non-sequential positions`() {
        val stories = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1.5 to StoryStep(type = StoryTypes.TEXT.type, text = "Middle"),
            3.0 to StoryStep(type = StoryTypes.TEXT.type, text = "End")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(1.5, result[0.0]?.nextPosition)
        assertNull(result[0.0]?.previousPosition)

        assertEquals(3.0, result[1.5]?.nextPosition)
        assertEquals(0.0, result[1.5]?.previousPosition)

        assertNull(result[3.0]?.nextPosition)
        assertEquals(1.5, result[3.0]?.previousPosition)
    }

    @Test
    fun `calculate should handle multiple elements in sequence`() {
        val stories = mapOf(
            0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "First"),
            2.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Second"),
            3.0 to StoryStep(type = StoryTypes.TEXT.type, text = "Third")
        )

        val result = NextPositionCalculator.calculate(stories)

        // First element
        assertEquals(1.0, result[0.0]?.nextPosition)
        assertNull(result[0.0]?.previousPosition)

        // Middle elements
        assertEquals(2.0, result[1.0]?.nextPosition)
        assertEquals(0.0, result[1.0]?.previousPosition)

        assertEquals(3.0, result[2.0]?.nextPosition)
        assertEquals(1.0, result[2.0]?.previousPosition)

        // Last element
        assertNull(result[3.0]?.nextPosition)
        assertEquals(2.0, result[3.0]?.previousPosition)
    }
}
