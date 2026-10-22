package io.writeopia.manager

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.manager.ContentHandler
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LinkCardContentHandlerTest {

    private val handler = ContentHandler(
        focusableTypes = setOf(StoryTypes.TEXT.type.number),
        stepsNormalizer = normalizer()
    )

    private fun normalizer(): UnitsNormalizationMap =
        StepsMapNormalizationBuilder.reduceNormalizations { defaultNormalizers() }

    @Test
    fun `the last empty line becomes a card and a text line is added below it`() {
        val stories = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title", nextPosition = Rank.at(1)),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "", previousPosition = Rank.at(0))
        )

        val state = handler.createLinkCard(stories, Rank.at(1), "https://writeopia.io")!!

        val card = state.stories[Rank.at(1)]!!
        assertEquals(StoryTypes.LINK_CARD.type, card.type)
        assertEquals("https://writeopia.io", card.url)
        assertNull(card.text)

        assertEquals(3, state.stories.size)
        val below = state.stories.entries.first { (position, _) -> position > Rank.at(1) }
        assertEquals(StoryTypes.TEXT.type, below.value.type)
        assertEquals("", below.value.text)
        assertEquals(below.key, state.focus)

        val edit = assertIs<LastEdit.LineBreakEdition>(state.lastEdit)
        assertEquals(card, edit.originalStep.second)
        assertEquals(below.value, edit.newStep.second)
    }

    @Test
    fun `an existing text line below keeps its place and takes the cursor`() {
        val stories = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title", nextPosition = Rank.at(1)),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "", previousPosition = Rank.at(0), nextPosition = Rank.at(2)),
            Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "below", previousPosition = Rank.at(1))
        )

        val state = handler.createLinkCard(stories, Rank.at(1), "https://writeopia.io")!!

        assertEquals(StoryTypes.LINK_CARD.type, state.stories[Rank.at(1)]!!.type)
        assertEquals(3, state.stories.size)
        assertEquals("below", state.stories[Rank.at(2)]!!.text)
        assertEquals(Rank.at(2), state.focus)
        assertIs<LastEdit.LineEdition>(state.lastEdit)
    }

    @Test
    fun `nothing happens for a position without a step`() {
        assertNull(handler.createLinkCard(emptyMap(), Rank.at(1), "https://writeopia.io"))
    }
}
