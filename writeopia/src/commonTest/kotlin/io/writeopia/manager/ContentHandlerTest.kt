package io.writeopia.manager

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.manager.ContentHandler
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.command.CommandFactory
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.extensions.associateWithPosition
import io.writeopia.utils.MapStoryData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentHandlerTest {

    @Test
    fun `should be possible to add content correctly`() {
        Rank.jitter = false
        val input = MapStoryData.imageStepsList().values.associateWithPosition()

        val contentHandler =
            ContentHandler(
                focusableTypes = setOf(StoryTypes.TEXT.type.number),
                stepsNormalizer = normalizer()
            )

        val storyStep = StoryStep(type = StoryTypes.TEXT.type)
        val newStory = contentHandler.addNewContent(input, storyStep, Rank.at(1)).map

        // The new step goes right before the one at Rank.at(1), between it and the previous one
        val expected = mapOf<String, StoryStep>(
            Rank.at(0) to StoryStep(type = StoryTypes.IMAGE.type),
            Rank.between(Rank.at(0), Rank.at(1)) to storyStep,
            Rank.at(1) to StoryStep(type = StoryTypes.IMAGE.type),
            Rank.at(2) to StoryStep(type = StoryTypes.IMAGE.type),
        ).mapValues { (_, storyStep) ->
            storyStep.type
        }

        assertEquals(expected, newStory.mapValues { (_, storyStep) -> storyStep.type })
        Rank.jitter = true
    }

    @Test
    fun `when a line break happens the text should be divided correctly`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "line1\nline2"
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        // With no next position, new line goes at position + 1
        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(2, sortedStories.size)
        assertEquals("line1", sortedStories[0].value.text)
        assertEquals("line2", sortedStories[1].value.text)
    }

    @Test
    fun `when a line break happens the text should be divided correctly for multiple lines`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "line1\nline2\nline3\nline4"
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        // With intermediate positions, the new lines are at calculated positions
        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(4, sortedStories.size)
        assertEquals("line1", sortedStories[0].value.text)
        assertEquals("line2", sortedStories[1].value.text)
        assertEquals("line3", sortedStories[2].value.text)
        assertEquals("line4", sortedStories[3].value.text)
    }

    @Test
    fun `line break should preserve regular formatting spans`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "bold1\nbold2",
            spans = setOf(
                SpanInfo.create(0, 11, Span.BOLD)
            )
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.BOLD)),
            sortedStories[0].value.spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.BOLD)),
            sortedStories[1].value.spans,
        )
    }

    @Test
    fun `line break should preserve link extra on both lines`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val url = "https://writeopia.io"
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "link1\nlink2",
            spans = setOf(
                SpanInfo.create(0, 11, Span.LINK, url)
            )
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.LINK, url)),
            sortedStories[0].value.spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.LINK, url)),
            sortedStories[1].value.spans,
        )
    }

    @Test
    fun `line break should split comment span and preserve conversation id`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val conversationId = "conversation-1"
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "line1\nline2",
            spans = setOf(
                SpanInfo.create(0, 11, Span.COMMENT, conversationId)
            )
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.COMMENT, conversationId)),
            sortedStories[0].value.spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 5, Span.COMMENT, conversationId)),
            sortedStories[1].value.spans,
        )
    }

    @Test
    fun `line break should split a partially covered comment range`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val conversationId = "conversation-1"
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "hello\nworld",
            spans = setOf(
                SpanInfo.create(2, 9, Span.COMMENT, conversationId)
            )
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        val sortedStories = newState.stories.entries.sortedBy { it.key }
        assertEquals(
            setOf(SpanInfo.create(2, 5, Span.COMMENT, conversationId)),
            sortedStories[0].value.spans,
        )
        assertEquals(
            setOf(SpanInfo.create(0, 3, Span.COMMENT, conversationId)),
            sortedStories[1].value.spans,
        )
    }

    @Test
    fun `multiple line breaks should keep comment identity on every covered line`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val conversationId = "conversation-1"
        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "one\ntwo\nthree",
            spans = setOf(
                SpanInfo.create(0, 13, Span.COMMENT, conversationId)
            )
        )

        val (_, newState) = contentHandler.onLineBreak(
            mapOf(Rank.at(0) to storyStep),
            Action.LineBreak(storyStep, Rank.at(0))
        )

        val spans = newState.stories.entries
            .sortedBy { it.key }
            .map { it.value.spans.single() }

        assertEquals(
            listOf(
                SpanInfo.create(0, 3, Span.COMMENT, conversationId),
                SpanInfo.create(0, 3, Span.COMMENT, conversationId),
                SpanInfo.create(0, 5, Span.COMMENT, conversationId),
            ),
            spans,
        )
    }

    @Test
    fun `erasing a line should preserve adjacent parts of the same comment`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val conversationId = "conversation-1"
        val first = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "hello",
            spans = setOf(
                SpanInfo.create(0, 5, Span.COMMENT, conversationId)
            ),
            nextPosition = Rank.at(1),
        )
        val second = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "world",
            spans = setOf(
                SpanInfo.create(0, 5, Span.COMMENT, conversationId)
            ),
            previousPosition = Rank.at(0),
        )

        val newState = contentHandler.eraseStory(
            Action.EraseStory(second, Rank.at(1)),
            mapOf(Rank.at(0) to first, Rank.at(1) to second),
        )

        assertEquals(
            setOf(
                SpanInfo.create(0, 5, Span.COMMENT, conversationId),
                SpanInfo.create(5, 10, Span.COMMENT, conversationId),
            ),
            newState.stories.getValue(Rank.at(0)).spans,
        )
    }

    @Test
    fun `erasing a line should move its comment spans into the merged text`() {
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val first = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "hello",
            spans = setOf(
                SpanInfo.create(0, 5, Span.COMMENT, "conversation-1")
            ),
            nextPosition = Rank.at(1),
        )
        val second = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "world",
            spans = setOf(
                SpanInfo.create(0, 5, Span.COMMENT, "conversation-2")
            ),
            previousPosition = Rank.at(0),
        )

        val newState = contentHandler.eraseStory(
            Action.EraseStory(second, Rank.at(1)),
            mapOf(Rank.at(0) to first, Rank.at(1) to second),
        )

        val merged = newState.stories.getValue(Rank.at(0))
        assertEquals("helloworld", merged.text)
        assertEquals(
            setOf(
                SpanInfo.create(0, 5, Span.COMMENT, "conversation-1"),
                SpanInfo.create(5, 10, Span.COMMENT, "conversation-2"),
            ),
            merged.spans,
        )
    }

    @Test
    fun `when check item command is WRITTEN the command should be removed from the story text`() {
        val input = MapStoryData.messagesInLine()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val text = "Lalala"

        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "-[]$text"
        )

        val position = Rank.at(1)
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
    fun `when deleting stories the focus should move correctly`() {
        val input = MapStoryData.messagesInLine()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val text = "Lalala"

        val storyStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "#$text"
        )

        val position = Rank.at(1)
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

        val deletePosition = Rank.at(2)

        val newState2 = contentHandler.deleteStory(
            Action.DeleteStory(newState.stories[deletePosition]!!, deletePosition),
            newState.stories,
            "test-document-id"
        )

        assertEquals(Rank.at(1), newState2?.focus)
    }

    @Test
    fun `when deleting stories empty spaces should NOT be allowed`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val newState = contentHandler.deleteStory(Action.DeleteStory(input[Rank.at(1)]!!, Rank.at(1)), input, "test-document-id")

        // After deletion, the story at position 1L is removed, leaving 5 stories
        assertEquals(5, newState!!.stories.size)
        // Position 1L should no longer exist
        assertEquals(null, newState.stories[Rank.at(1)])
    }

    @Test
    fun `when erasing stories it should move text correctly`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val lastStory = input.values.last()
        val lastIndex = Rank.at(input.values.size - 1)
        val secondLastStory = input[Rank.at(input.values.size - 2)]

        val newState = contentHandler.eraseStory(Action.EraseStory(lastStory, lastIndex), input)

        assertEquals(secondLastStory!!.text + lastStory.text, newState.stories.values.last().text)
    }

    @Test
    fun `when erasing stories empty spaces should NOT be allowed`() {
        val input = MapStoryData.simpleMessages()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val newState = contentHandler.eraseStory(Action.EraseStory(input[Rank.at(1)]!!, Rank.at(1)), input)

        // After erasing, the story at position 1L is removed and merged with previous, leaving 5 stories
        assertEquals(5, newState.stories.size)
        // Position 1L should no longer exist
        assertEquals(null, newState.stories[Rank.at(1)])
    }

    @Test
    fun `when a header is collapsed all text below it should be hidden`() {
        val input = MapStoryData.messagesWithHeader()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())

        val state = contentHandler.collapseItem(input, Rank.at(0))

        assertTrue { state.stories[Rank.at(0)]!!.tags.any { it.tag == Tag.COLLAPSED } }
        state.stories.values.drop(0).all { it.tags.any { it.tag.isHidden() } }
    }

    @Test
    fun `when changing the type of a story it should not change the position of the box highlight`() {
        val input = MapStoryData.messagesWithHighlight()
        val contentHandler = ContentHandler(stepsNormalizer = normalizer())
        val position = Rank.at(1)

        val newState = contentHandler.changeStoryType(
            currentStory = input,
            typeInfo = TypeInfo(StoryTypes.TEXT.type),
            position = Rank.at(1),
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
