package io.writeopia.api.typesafe.service

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionSelectorTest {

    @Test
    fun `it keeps only the actions at or above the threshold`() {
        val selected = SuggestionSelector.select(
            mapOf(
                WritingSuggestionAction.LIST to 0.95,
                WritingSuggestionAction.CODE_BLOCK to 0.8,
                WritingSuggestionAction.IMAGE to 0.79,
                WritingSuggestionAction.TITLE to 0.1,
            )
        )

        assertEquals(
            listOf(WritingSuggestionAction.LIST, WritingSuggestionAction.CODE_BLOCK),
            selected.map { it.action }
        )
    }

    @Test
    fun `it answers the three best, best first`() {
        val selected = SuggestionSelector.select(
            mapOf(
                WritingSuggestionAction.LIST to 0.85,
                WritingSuggestionAction.CODE_BLOCK to 0.99,
                WritingSuggestionAction.IMAGE to 0.9,
                WritingSuggestionAction.TITLE to 0.81,
                WritingSuggestionAction.CALLOUT to 0.95,
            )
        )

        assertEquals(
            listOf(
                WritingSuggestionAction.CODE_BLOCK,
                WritingSuggestionAction.CALLOUT,
                WritingSuggestionAction.IMAGE,
            ),
            selected.map { it.action }
        )
    }

    @Test
    fun `the threshold comes from the environment, within 0 and 1`() {
        assertEquals(0.65, SuggestionSelector.thresholdFromEnv("0.65"))
        assertEquals(0.9, SuggestionSelector.thresholdFromEnv(" 0.9 "))
        assertEquals(SuggestionSelector.DEFAULT_THRESHOLD, SuggestionSelector.thresholdFromEnv(null))
        assertEquals(SuggestionSelector.DEFAULT_THRESHOLD, SuggestionSelector.thresholdFromEnv("eighty"))
        assertEquals(SuggestionSelector.DEFAULT_THRESHOLD, SuggestionSelector.thresholdFromEnv("80"))
    }

    @Test
    fun `the limit comes from the environment, above zero`() {
        assertEquals(5, SuggestionSelector.limitFromEnv("5"))
        assertEquals(SuggestionSelector.DEFAULT_LIMIT, SuggestionSelector.limitFromEnv(null))
        assertEquals(SuggestionSelector.DEFAULT_LIMIT, SuggestionSelector.limitFromEnv("0"))
        assertEquals(SuggestionSelector.DEFAULT_LIMIT, SuggestionSelector.limitFromEnv("many"))
    }

    @Test
    fun `it answers nothing when nothing is confident enough`() {
        val selected = SuggestionSelector.select(
            mapOf(
                WritingSuggestionAction.LIST to 0.5,
                WritingSuggestionAction.CODE_BLOCK to 0.3,
            )
        )

        assertTrue(selected.isEmpty())
    }
}
