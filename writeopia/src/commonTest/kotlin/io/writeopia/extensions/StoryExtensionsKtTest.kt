package io.writeopia.extensions

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.utils.extensions.noContent
import io.writeopia.sdk.utils.extensions.toSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryExtensionsKtTest {

    @Test
    fun `it should be able to recognize an empty document`() {
        val storyStepMap = buildList {
            repeat(5) {
                add(it to StoryStep(type = StoryTypes.TEXT.type))
            }
        }.toMap()

        assertTrue(storyStepMap.noContent())
    }

    @Test
    fun `it should be able to recognize a not empty document`() {
        val storyStepMap = buildList {
            repeat(5) { index ->
                add(
                    index to StoryStep(
                        type = StoryTypes.TEXT.type
                    )
                )
            }
        }.toMap().toMutableMap()

        storyStepMap[5] = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "some text"
        )

        assertFalse(storyStepMap.noContent())
    }

    @Test
    fun `it should be possible to create sections from a list of story steps`() {
        val storyStepMap = buildList {
            add(
                0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Title",
                    tags = setOf(TagInfo(Tag.H1))
                )
            )

            repeat(5) { i ->
                val count = i + 1
                add(count to StoryStep(type = StoryTypes.TEXT.type, text = "$count"))
            }
        }.toMap()

        val expectedText = buildString {
            appendLine("Title")
            appendLine("1")
            appendLine("2")
            appendLine("3")
            appendLine("4")
            appendLine("5")
        }

        val sections = storyStepMap.toSections()

        assertEquals(sections.size, 1)
        assertEquals(expectedText, sections.joinToString(separator = "\n") { it.asText() })
    }
}
