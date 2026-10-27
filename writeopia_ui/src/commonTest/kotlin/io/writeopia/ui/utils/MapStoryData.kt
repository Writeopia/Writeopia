package io.writeopia.ui.utils

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes

object MapStoryData {

    fun singleCheckItem(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            id = GenerateId.generate(),
            localId = GenerateId.generate(),
            type = StoryTypes.CHECK_ITEM.type,
            text = "something \n"
        )
    )

    fun imageGroup(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.GROUP_IMAGE.type,
            steps = listOf(
                StoryStep(
                    localId = "1",
                    type = StoryTypes.IMAGE.type,
                    parentId = "0"
                ),
                StoryStep(
                    localId = "2",
                    type = StoryTypes.IMAGE.type,
                    parentId = "0"
                ),
                StoryStep(
                    localId = "3",
                    type = StoryTypes.IMAGE.type,
                    parentId = "0"
                )
            )
        ),
        Rank.at(1) to StoryStep(
            localId = "4",
            type = StoryTypes.IMAGE.type,
        )
    )

    fun imageStepsList(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "1",
            type = StoryTypes.IMAGE.type,
        ),
        Rank.at(1) to StoryStep(
            localId = "2",
            type = StoryTypes.IMAGE.type,
        ),
        Rank.at(2) to StoryStep(
            localId = "3",
            type = StoryTypes.IMAGE.type,
        ),
        Rank.at(3) to StoryStep(
            localId = "4",
            type = StoryTypes.IMAGE.type,
        ),
        Rank.at(4) to StoryStep(
            localId = "5",
            type = StoryTypes.IMAGE.type,
        ),
    )

    fun imageSimpleGroup(): Map<String, List<StoryStep>> = mapOf(
        Rank.at(0) to listOf(
            StoryStep(
                localId = "1",
                type = StoryTypes.IMAGE.type,
            ),
            StoryStep(
                localId = "2",
                type = StoryTypes.IMAGE.type,
            ),
            StoryStep(
                localId = "3",
                type = StoryTypes.IMAGE.type,
            ),
        )
    )

    fun twoGroupsImageList(): Map<String, List<StoryStep>> {
        val parent1Id = GenerateId.generate()
        val parent2Id = GenerateId.generate()

        return mapOf(
            Rank.at(0) to listOf(
                StoryStep(
                    localId = parent1Id,
                    type = StoryTypes.GROUP_IMAGE.type,
                    steps = listOf(
                        StoryStep(
                            localId = "1",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent1Id
                        ),
                        StoryStep(
                            localId = "2",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent1Id
                        ),
                        StoryStep(
                            localId = "3",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent1Id
                        )
                    )
                ),
                StoryStep(
                    localId = parent2Id,
                    type = StoryTypes.GROUP_IMAGE.type,
                    steps = listOf(
                        StoryStep(
                            localId = "12",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent2Id
                        ),
                        StoryStep(
                            localId = "12",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent2Id
                        ),
                        StoryStep(
                            localId = "13",
                            type = StoryTypes.IMAGE.type,
                            parentId = parent2Id
                        ),
                    )
                )
            ),
        )
    }

    fun stepsList(): Map<String, List<StoryStep>> = mapOf(
        Rank.at(0) to listOf(
            StoryStep(
                localId = "1",
                type = StoryTypes.IMAGE.type,
            ),
            StoryStep(
                localId = "2",
                type = StoryTypes.IMAGE.type,
            ),
            StoryStep(
                localId = "3",
                type = StoryTypes.IMAGE.type,
            )
        ),
        Rank.at(1) to listOf(
            StoryStep(
                localId = "4",
                type = StoryTypes.TEXT.type,
            )
        ),
        Rank.at(2) to listOf(
            StoryStep(
                localId = "5",
                type = StoryTypes.TEXT.type,
            )
        )
    )

    fun singleMessage(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.TEXT.type,
            text = "hi!",
        )
    )

    fun singleMessageLineBreak(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.TEXT.type,
            text = "hi!\n",
        )
    )

    fun messagesInLine(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.TEXT.type,
            text = "hi!",
        ),
        Rank.at(1) to StoryStep(
            localId = "2",
            type = StoryTypes.TEXT.type,
            text = "Hey!",
        ),
        Rank.at(2) to StoryStep(
            localId = "4",
            type = StoryTypes.TEXT.type,
            text = "And it was super awesome!! Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis " +
                "suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur?",
        ),
        Rank.at(3) to StoryStep(
            localId = "6",
            type = StoryTypes.TEXT.type,
            text = "I couldn't believe how sunny it was. Santiago is a really beautiful city. There's a lot to " +
                "do and enjoyed the day. We went to many cozy Cafes and we enjoyed the city by foot. " +
                "We had to buy some Syn Cards to be able to communicate in the new country. ",
        ),
        Rank.at(4) to StoryStep(
            localId = "7",
            type = StoryTypes.TEXT.type,
            text = "I couldn't believe how sunny it was. Santiago is a really beautiful city. There's a lot to " +
                "do and enjoyed the day. We went to many cozy Cafes and we enjoyed the city by foot. " +
                "We had to buy some Syn Cards to be able to communicate in the new country. ",
        ),
        Rank.at(5) to StoryStep(
            localId = "8",
            type = StoryTypes.TEXT.type,
            text = "I couldn't believe how sunny it was. Santiago is a really beautiful city. There's a lot to " +
                "do and enjoyed the day. We went to many cozy Cafes and we enjoyed the city by foot. " +
                "We had to buy some Syn Cards to be able to communicate in the new country. ",
        )
    )

    fun simpleMessages(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.TEXT.type,
            text = "message1",
        ),
        Rank.at(1) to StoryStep(
            localId = "2",
            type = StoryTypes.TEXT.type,
            text = "message2",
        ),
        Rank.at(2) to StoryStep(
            localId = "4",
            type = StoryTypes.TEXT.type,
            text = "message3",
        ),
        Rank.at(3) to StoryStep(
            localId = "6",
            type = StoryTypes.TEXT.type,
            text = "message4",
        ),
        Rank.at(4) to StoryStep(
            localId = "7",
            type = StoryTypes.TEXT.type,
            text = "message5",
        ),
        Rank.at(5) to StoryStep(
            localId = "8",
            type = StoryTypes.TEXT.type,
            text = "message6",
        )
    )

    fun complexList(): Map<String, List<StoryStep>> = mapOf(
        Rank.at(0) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
            ),
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
            ),
        ),
        Rank.at(1) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
            )
        ),
        Rank.at(2) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
            ),
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
            ),
            StoryStep(
                localId = "2",
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/984/400/400.jpg?hmac=CaqZ-rcUAbmidwURZcBynO7aIAC-FaktVN7X8lIvlmE",
            )
        ),
        Rank.at(3) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.TEXT.type,
                text = "We arrived in Santiago!! \n\nLorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
                    "eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud " +
                    "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
            )
        ),
        Rank.at(4) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                url = "https://fastly.picsum.photos/id/514/1200/600.jpg?hmac=gh5_PZFkQI74GShPTCJ_XP_EgN-X1O0OUP8tDlT7WkY",
                text = "The hotel entrance"
            )
        ),
        Rank.at(5) to listOf(
            StoryStep(
                localId = GenerateId.generate(),
                type = StoryTypes.IMAGE.type,
                text = "And it was super awesome!! \n\nUt enim ad minima veniam, quis nostrum exercitationem ullam " +
                    "corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur?",
            )
        ),
        Rank.at(6) to listOf(
            StoryStep(
                localId = "6",
                type = StoryTypes.TEXT.type,
                text = "I couldn't believe how sunny it was. Santiago is a really beautiful city. There's a lot to " +
                    "do and enjoyed the day. We went to many cozy Cafes and we enjoyed the city by foot. " +
                    "We had to buy some Syn Cards to be able to communicate in the new country. ",
            )
        )
    )

    fun aiAnswerWithH3(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "### This is a heading\nSome content here",
        )
    )

    fun aiAnswerWithMultipleMarkdown(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "### Heading\n- List item 1\n- List item 2\n[] Task item\nRegular text",
        )
    )

    fun aiAnswerWithListItems(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "- First item\n- Second item\n- Third item",
        )
    )

    fun aiAnswerWithCheckItems(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "[] Task one\n[] Task two",
        )
    )

    fun aiAnswerWithDivider(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "Content above\n---\nContent below",
        )
    )

    fun aiAnswerWithAllHeadings(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "# Heading 1\n## Heading 2\n### Heading 3\n#### Heading 4",
        )
    )

    fun aiAnswerWithInlineMarkdown(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            localId = "0",
            type = StoryTypes.AI_ANSWER.type,
            text = "**For The Cake**\nThis is *italic* text\nVisit https://example.com",
        )
    )

    fun syncHistory(): Map<String, StoryStep> =
        mapOf(
            Rank.at(0) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.IMAGE.type,
                    url = "https://fastly.picsum.photos/id/15/400/400.jpg?hmac=xv-6mggpYPLIQ9eNAHrl1qKPHjyUCYlBoNBvdsqF4cY",
                ),
            Rank.at(1) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.IMAGE.type,
                    url = "https://fastly.picsum.photos/id/1018/400/400.jpg?hmac=MwHJoMaVXsBbqg-LFoDVL6P8TCDkSEikExptCkkHESQ",
                ),
            Rank.at(2) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.IMAGE.type,
                    url = "https://fastly.picsum.photos/id/984/400/400.jpg?hmac=CaqZ-rcUAbmidwURZcBynO7aIAC-FaktVN7X8lIvlmE",
                ),
            Rank.at(3) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.TEXT.type,
                    text = "We arrived in Santiago!!",
                ),
            Rank.at(4) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.CHECK_ITEM.type,
                    text = "We need to go to the Cafe!",
                ),
            Rank.at(5) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.CHECK_ITEM.type,
                    text = "We need to have lots of fun!",
                ),
            Rank.at(6) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.CHECK_ITEM.type,
                    text = "We need to lear some Spanish!",
                ),
            Rank.at(7) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.IMAGE.type,
                    url = "https://fastly.picsum.photos/id/514/1200/600.jpg?hmac=gh5_PZFkQI74GShPTCJ_XP_EgN-X1O0OUP8tDlT7WkY",
                    text = "The hotel entrance"
                ),
            Rank.at(8) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.TEXT_BOX.type,
                    text = "And it was super awesome!! \n\nUt enim ad minima veniam, " +
                        "quis nostrum exercitationem ullam corporis suscipit laboriosam, " +
                        "nisi ut aliquid ex ea commodi consequatur?",
                ),
            Rank.at(9) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.TEXT_BOX.type,
                    text = "I hope to to it again some day..."
                ),
            Rank.at(10) to
                StoryStep(
                    localId = GenerateId.generate(),
                    type = StoryTypes.TEXT_BOX.type,
                    text = "I couldn't believe how sunny it was. Santiago is a really beautiful city. There's a lot to " +
                        "do and enjoyed the day. We went to many cozy Cafes and we enjoyed the city by foot. " +
                        "We had to buy some Syn Cards to be able to communicate in the new country. ",
                ),
        )

    /**
     * Document with title at position 0 and text at position 1.
     * Used for testing title protection.
     */
    fun documentWithTitle(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            id = GenerateId.generate(),
            localId = "title",
            type = StoryTypes.TITLE.type,
            text = "My Document Title"
        ),
        Rank.at(1) to StoryStep(
            id = GenerateId.generate(),
            localId = "text1",
            type = StoryTypes.TEXT.type,
            text = "Some content"
        )
    )

    /**
     * Document with only a title at position 0.
     * Used for testing title protection on empty documents.
     */
    fun documentWithOnlyTitle(): Map<String, StoryStep> = mapOf(
        Rank.at(0) to StoryStep(
            id = GenerateId.generate(),
            localId = "title",
            type = StoryTypes.TITLE.type,
            text = "Empty Document Title"
        )
    )
}
