package io.writeopia.ui.edition

import io.writeopia.sdk.models.command.Command
import io.writeopia.sdk.models.command.CommandFactory
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.command.WhereToFind
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.manager.WriteopiaStateManager

class TextCommandHandler(
    private val commandsMap: Map<Command, (StoryStep, Int) -> Unit>,
    private val excludeTypes: Set<Int> = setOf(StoryTypes.TITLE.type.number)
) {

    fun handleCommand(text: String, step: StoryStep, position: Int): Boolean {
        if (excludeTypes.contains(step.type.number)) return false

        // Todo(Leandro): Using a reverse index would improve the speed a lot.
        val command: Command = commandsMap.keys
            .firstOrNull { command ->
                when (command.whereToFind) {
                    WhereToFind.START -> text.startsWith(command.commandText)
                    WhereToFind.ANYWHERE -> text.contains(command.commandText)
                }
            } ?: return false

        commandsMap[command]!!.invoke(step.copy(text = text), position)

        return true
    }

    companion object {
        fun noCommands(): TextCommandHandler = TextCommandHandler(emptyMap())

        fun defaultCommands(manager: WriteopiaStateManager): TextCommandHandler {
            return TextCommandHandler(
                mapOf(
                    CommandFactory.checkItem() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.CHECK_ITEM.type),
                            CommandInfo(
                                CommandFactory.checkItem(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.checkItem2() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.CHECK_ITEM.type),
                            CommandInfo(
                                CommandFactory.checkItem2(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.box() to { _, position ->
                        manager.toggleTagForPosition(
                            position,
                            TagInfo(Tag.HIGH_LIGHT_BLOCK),
                            CommandInfo(CommandFactory.box(), CommandTrigger.WRITTEN)
                        )
                    },
                    CommandFactory.unOrderedList() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.UNORDERED_LIST_ITEM.type),
                            CommandInfo(
                                CommandFactory.unOrderedList(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.h1() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.TEXT.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.h1(),
                                CommandTrigger.WRITTEN,
                                tags = setOf(TagInfo(Tag.H1))
                            )
                        )
                    },
                    CommandFactory.h2() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.TEXT.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.h2(),
                                CommandTrigger.WRITTEN,
                                tags = setOf(TagInfo(Tag.H2))
                            )
                        )
                    },
                    CommandFactory.h3() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.TEXT.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.h3(),
                                CommandTrigger.WRITTEN,
                                tags = setOf(TagInfo(Tag.H3))
                            )
                        )
                    },
                    CommandFactory.h4() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.TEXT.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.h4(),
                                CommandTrigger.WRITTEN,
                                tags = setOf(TagInfo(Tag.H4))
                            )
                        )
                    },
                    CommandFactory.codeBlock() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.CODE_BLOCK.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.codeBlock(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.divider() to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(
                                StoryTypes.DIVIDER.type,
                                Decoration()
                            ),
                            CommandInfo(
                                CommandFactory.divider(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    }
                )
            )
        }
    }
}
