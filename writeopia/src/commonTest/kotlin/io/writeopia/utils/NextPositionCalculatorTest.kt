package io.writeopia.utils

import io.writeopia.sdk.utils.iterables.Rank
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
            Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "Hello")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertNull(result[Rank.at(0)]?.nextPosition)
        assertNull(result[Rank.at(0)]?.previousPosition)
    }

    @Test
    fun `calculate should set correct nextPosition for two elements`() {
        val stories = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "Content")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(Rank.at(1), result[Rank.at(0)]?.nextPosition)
        assertNull(result[Rank.at(0)]?.previousPosition)
        assertNull(result[Rank.at(1)]?.nextPosition)
        assertEquals(Rank.at(0), result[Rank.at(1)]?.previousPosition)
    }

    @Test
    fun `calculate should handle non-sequential positions`() {
        val stories = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            Rank.at(15) to StoryStep(type = StoryTypes.TEXT.type, text = "Middle"),
            Rank.at(30) to StoryStep(type = StoryTypes.TEXT.type, text = "End")
        )

        val result = NextPositionCalculator.calculate(stories)

        assertEquals(Rank.at(15), result[Rank.at(0)]?.nextPosition)
        assertNull(result[Rank.at(0)]?.previousPosition)

        assertEquals(Rank.at(30), result[Rank.at(15)]?.nextPosition)
        assertEquals(Rank.at(0), result[Rank.at(15)]?.previousPosition)

        assertNull(result[Rank.at(30)]?.nextPosition)
        assertEquals(Rank.at(15), result[Rank.at(30)]?.previousPosition)
    }

    @Test
    fun `calculate should handle multiple elements in sequence`() {
        val stories = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "First"),
            Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "Second"),
            Rank.at(3) to StoryStep(type = StoryTypes.TEXT.type, text = "Third")
        )

        val result = NextPositionCalculator.calculate(stories)

        // First element
        assertEquals(Rank.at(1), result[Rank.at(0)]?.nextPosition)
        assertNull(result[Rank.at(0)]?.previousPosition)

        // Middle elements
        assertEquals(Rank.at(2), result[Rank.at(1)]?.nextPosition)
        assertEquals(Rank.at(0), result[Rank.at(1)]?.previousPosition)

        assertEquals(Rank.at(3), result[Rank.at(2)]?.nextPosition)
        assertEquals(Rank.at(1), result[Rank.at(2)]?.previousPosition)

        // Last element
        assertNull(result[Rank.at(3)]?.nextPosition)
        assertEquals(Rank.at(2), result[Rank.at(3)]?.previousPosition)
    }
}
