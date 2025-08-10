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
    private val commandsMap: Map<String, (StoryStep, Int) -> Unit>,
    private val excludeTypes: Set<Int> = setOf(StoryTypes.TITLE.type.number),
    private val trie: Trie = Trie()
) {

    init {
        commandsMap.keys.forEach { trie.insert(it) }
    }

    fun handleCommand(text: String, step: StoryStep, position: Int): Boolean {
        if (excludeTypes.contains(step.type.number) || text.lastOrNull() != ' ') return false

        val textArray = text.split(" ")
        if (textArray.isEmpty()) return false

        val command = textArray[0]
        val hasCommand = trie.search(command)

        return if (hasCommand) {
            commandsMap[command]!!.invoke(step.copy(text = text), position)
            true
        } else {
            false
        }
    }

    companion object {
        fun noCommands(): TextCommandHandler = TextCommandHandler(emptyMap())

        fun defaultCommands(manager: WriteopiaStateManager): TextCommandHandler {
            return TextCommandHandler(
                mapOf(
                    CommandFactory.checkItem().commandText to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.CHECK_ITEM.type),
                            CommandInfo(
                                CommandFactory.checkItem(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.checkItem2().commandText to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.CHECK_ITEM.type),
                            CommandInfo(
                                CommandFactory.checkItem2(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.box().commandText to { _, position ->
                        manager.toggleTagForPosition(
                            position,
                            TagInfo(Tag.HIGH_LIGHT_BLOCK),
                            CommandInfo(CommandFactory.box(), CommandTrigger.WRITTEN)
                        )
                    },
                    CommandFactory.unOrderedList().commandText to { _, position ->
                        manager.changeStoryType(
                            position,
                            TypeInfo(StoryTypes.UNORDERED_LIST_ITEM.type),
                            CommandInfo(
                                CommandFactory.unOrderedList(),
                                CommandTrigger.WRITTEN
                            )
                        )
                    },
                    CommandFactory.h1().commandText to { _, position ->
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
                    CommandFactory.h2().commandText to { _, position ->
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
                    CommandFactory.h3().commandText to { _, position ->
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
                    CommandFactory.h4().commandText to { _, position ->
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
                    CommandFactory.codeBlock().commandText to { _, position ->
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
                    CommandFactory.divider().commandText to { _, position ->
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

class Trie {
    private class TrieNode {
        val children: MutableMap<Char, TrieNode> = mutableMapOf()
        var isEndOfWord: Boolean = false
    }

    private val root = TrieNode()

    fun insert(word: String) {
        var node = root
        for (char in word) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        node.isEndOfWord = true
    }

    fun search(word: String): Boolean {
        val node = findNode(word)
        return node?.isEndOfWord == true
    }

    fun startsWith(prefix: String): Boolean {
        return findNode(prefix) != null
    }

    private fun findNode(prefix: String): TrieNode? {
        var node = root
        for (char in prefix) {
            node = node.children[char] ?: return null
        }
        return node
    }
}
