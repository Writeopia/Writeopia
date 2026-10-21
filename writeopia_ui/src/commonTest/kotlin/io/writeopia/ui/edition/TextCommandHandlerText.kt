package io.writeopia.ui.edition

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextCommandHandlerText {

    private val step = StoryStep(type = StoryTypes.TEXT.type)

    @Test
    fun `it should be possible to trigger a command`() {
        val handler = TextCommandHandler(
            commandsMap = mapOf(
                "## " to { _, _ -> },
                "command" to { _, _ -> },
                "/box" to { _, _ -> },
            )
        )

        assertTrue {
            handler.handleCommand("## ", step, 0L)
        }
        assertTrue {
            handler.handleCommand("## Heading", step, 0L)
        }
        assertTrue {
            handler.handleCommand("command ", step, 0L)
        }
        assertFalse {
            handler.handleCommand("notACommand ", step, 0L)
        }
    }

    @Test
    fun `a command ending with a space waits for the space, so longer commands can be typed`() {
        val triggered = mutableListOf<String>()
        val handler = TextCommandHandler(
            commandsMap = listOf("# ", "## ", "### ", "- ", "-[]", "---").associateWith { command ->
                { _: StoryStep, _: Long ->
                    triggered.add(command)
                    Unit
                }
            }
        )

        listOf("#", "##", "###", "-", "--", "-[", "#hashtag", "a - b").forEach { text ->
            assertFalse(handler.handleCommand(text, step, 0L), "'$text' is not a command")
            assertFalse(handler.isExactCommand(text, step), "'$text' is not a command")
        }
        assertTrue(triggered.isEmpty())

        assertTrue(handler.isExactCommand("## ", step))
        assertTrue(handler.handleCommand("## ", step, 0L))
        assertTrue(handler.handleCommand("### Heading", step, 0L))
        assertTrue(handler.handleCommand("- item", step, 0L))
        assertTrue(handler.handleCommand("-[]", step, 0L))
        assertTrue(handler.handleCommand("---", step, 0L))
        assertTrue(handler.handleCommand("--- ", step, 0L))

        assertEquals(listOf("## ", "### ", "- ", "-[]", "---", "---"), triggered)
    }

    @Test
    fun `the title never takes commands`() {
        val handler = TextCommandHandler(commandsMap = mapOf("## " to { _, _ -> }))
        val title = StoryStep(type = StoryTypes.TITLE.type)

        assertFalse(handler.handleCommand("## Title", title, 0L))
        assertFalse(handler.isExactCommand("## ", title))
    }
}
