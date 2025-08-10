package io.writeopia.ui.edition

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextCommandHandlerText {

    @Test
    fun `it should be possible to trigger a command`() {
        val handler = TextCommandHandler(
            commandsMap = mapOf(
                "##" to { _, _ -> },
                "command" to { _, _ -> },
                "/box" to { _, _ -> },
            )
        )

        assertTrue {
            handler.handleCommand("## ", StoryStep(type = StoryTypes.TEXT.type), 0)
        }
        assertTrue {
            handler.handleCommand("command ", StoryStep(type = StoryTypes.TEXT.type), 0)
        }
        assertFalse {
            handler.handleCommand("notACommand ", StoryStep(type = StoryTypes.TEXT.type), 0)
        }
    }
}
