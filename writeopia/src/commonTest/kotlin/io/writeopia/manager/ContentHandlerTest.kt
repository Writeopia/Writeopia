package io.writeopia.manager

import io.writeopia.sdk.manager.ContentHandler
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.command.CommandFactory
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.utils.MapStoryData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentHandlerTest {

    @Test
    fun `should be possible to add content correctly`() {
        val input = MapStoryData.imageStepsList()

        val contentHandler =
            ContentHandler(
                focusableTypes = setOf(StoryTypes.TEXT.type.number),
                stepsNormalizer = normalizer()
            )

        val storyStep = StoryStep(type = StoryTypes.TEXT.type)
        val newStory = contentHandler.addNewContent(input, storyStep, 1)

        val expected = mapOf(
            0 to StoryStep(type = StoryTypes.IMAGE.type),
            1 to storyStep,
            2 to StoryStep(type = StoryTypes.IMAGE.type),
            3 to StoryStep(type = StoryTypes.IMAGE.type),
        ).mapValues { (_, storyStep) ->
            storyStep.type
        }

        assertEquals(expected, newStory.mapValues { (_, storyStep) -> storyStep.type })
    }

    @Test
    fun `when a line break happens, the text should be divided correctly`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "line1\nline2"
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(0 to storyStep),
            Action.LineBreak(storyStep, 0)
        )

        assertEquals("line1", newState.stories[0]!!.text)
        assertEquals("line2", newState.stories[1]!!.text)
    }

    @Test
    fun `when a line break happens, the text should be divided correctly for multiple line`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "line1\nline2\nline3\nline4"
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(0 to storyStep),
            Action.LineBreak(storyStep, 0)
        )

        assertEquals("line1", newState.stories[0]!!.text)
        assertEquals("line2", newState.stories[1]!!.text)
        assertEquals("line3", newState.stories[2]!!.text)
        assertEquals("line4", newState.stories[3]!!.text)
    }

    @Test
    fun `when check item command is WRITTEN, the command should be removed for the story text`() {
        val input = MapStoryData.messagesInLine()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val text = "Lalala"

        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "-[]$text"
        )

        val position = 1
        val mutable = input.toMutableMap()
        mutable[position] = storyStep

        val newState = contentHandler.changeStoryType(
            currentStory = mutable,
            typeInfo = TypeInfo(StoryTypes.CHECK_ITEM.type),
            position = position,
            CommandInfo(CommandFactory.checkItem(), CommandTrigger.WRITTEN)
        )

        val checkItemStory = newState.stories[position]

        assertEquals(StoryTypes.CHECK_ITEM.type, checkItemStory?.type)
        assertEquals(text, checkItemStory?.text)
    }

    @Test
    fun `when deleting stories, the focus should move correctly`() {
        val input = MapStoryData.messagesInLine()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val text = "Lalala"

        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "#$text"
        )

        val position = 1
        val mutable = input.toMutableMap()
        mutable[position] = storyStep

        val newState = contentHandler.changeStoryType(
            currentStory = mutable,
            typeInfo = TypeInfo(StoryTypes.TEXT.type),
            position = position,
            CommandInfo(CommandFactory.h1(), CommandTrigger.WRITTEN)
        )

        val textStory = newState.stories[position]

        assertEquals(StoryTypes.TEXT.type, textStory?.type)
        assertEquals(text, textStory?.text)

        val deletePosition = 2

        val newState2 = contentHandler.deleteStory(
            Action.DeleteStory(newState.stories[deletePosition]!!, deletePosition),
            newState.stories
        )

        assertEquals(1, newState2?.focus)
    }

    @Test
    fun `when deleting stories, empty spaces should NOT be allowed`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val newState = contentHandler.deleteStory(Action.DeleteStory(input[1]!!, 1), input)

        assertEquals(setOf(0, 1, 2, 3, 4), newState!!.stories.keys)
    }

    @Test
    fun `when erasing stories, is should move text correctly`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val lastStory = input.values.last()
        val lastIndex = input.values.size - 1
        val secondLastStory = input[lastIndex - 1]

        val newState = contentHandler.eraseStory(Action.EraseStory(lastStory, lastIndex), input)

        assertEquals(secondLastStory!!.text + lastStory.text, newState.stories.values.last().text)
    }

    @Test
    fun `when erasing stories, empty spaces should NOT be allowed`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val newState = contentHandler.eraseStory(Action.EraseStory(input[1]!!, 1), input)

        assertEquals(setOf(0, 1, 2, 3, 4), newState.stories.keys)
    }

    @Test
    fun `when a header is collapsed, all text bellow it should be hidden`() {
        val input = MapStoryData.messagesWithHeader()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val state = contentHandler.collapseItem(input, 0)

        assertTrue { state.stories[0]!!.tags.any { it.tag == Tag.COLLAPSED } }
        state.stories.values.drop(0).all { it.tags.any { it.tag.isHidden() } }
    }

    @Test
    fun `when changing the type of a story, it should not change the position of the box highlight`() {
        val input = MapStoryData.messagesWithHighlight()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val position = 1

        val newState = contentHandler.changeStoryType(
            currentStory = input,
            typeInfo = TypeInfo(StoryTypes.TEXT.type),
            position = 1,
            CommandInfo(CommandFactory.h1(), CommandTrigger.WRITTEN)
        )

        val story = newState.stories[position]

        assertEquals(
            story?.tags?.first { it.tag == Tag.HIGH_LIGHT_BLOCK }?.position,
            0
        )
    }
}

private fun normalizer(): UnitsNormalizationMap =
    StepsMapNormalizationBuilder.reduceNormalizations {
        defaultNormalizers()
    }
