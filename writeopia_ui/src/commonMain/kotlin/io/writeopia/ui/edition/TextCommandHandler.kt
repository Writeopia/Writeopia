package io.writeopia.ui.edition

import io.writeopia.sdk.models.command.CommandFactory
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.manager.WriteopiaStateManager

class TextCommandHandler(
    private val commandsMap: Map<String, (StoryStep, String) -> Unit>,
    private val excludeTypes: Set<Int> = setOf(StoryTypes.TITLE.type.number),
    private val trie: Trie = Trie()
) {

    init {
        commandsMap.keys.forEach { trie.insert(it) }
    }

    /**
     * Whether [text] is, as a whole, a command (e.g. "[]", "---" or "## ").
     */
    fun isExactCommand(text: String, step: StoryStep): Boolean =
        !excludeTypes.contains(step.type.number) && trie.search(text)

    fun handleCommand(text: String, step: StoryStep, position: String): Boolean {
        if (excludeTypes.contains(step.type.number)) return false

        val command = findCommand(text) ?: return false
        commandsMap[command]!!.invoke(step.copy(text = text), position)
        return true
    }

    /**
     * The command [text] starts with: the whole text ("---"), the text up to and including its
     * first space ("## " of "## Heading"), or the first word ("---" of "--- "). A command whose
     * text ends with a space is only found once that space is typed, so "#" and "##" stay plain
     * text while "###" is being written.
     */
    private fun findCommand(text: String): String? {
        if (trie.search(text)) return text

        val space = text.indexOf(' ')
        if (space < 0) return null

        return listOf(text.substring(0, space + 1), text.substring(0, space))
            .firstOrNull(trie::search)
    }

    companion object {
        fun noCommands(): TextCommandHandler = TextCommandHandler(emptyMap())

        fun defaultCommands(manager: WriteopiaStateManager): TextCommandHandler = TextCommandHandler(
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
                CommandFactory.card().commandText to { _, position ->
                    manager.toggleTagForPosition(
                        position,
                        TagInfo(Tag.CARD_BLOCK),
                        CommandInfo(CommandFactory.card(), CommandTrigger.WRITTEN)
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

class Trie {

    private val root = TrieNode()

    fun insert(word: String) {
        var current = root

        word.forEach { ch ->
            if (current.children.contains(ch)) {
                current = current.children[ch]!!
            } else {
                current.children[ch] = TrieNode()
                current = current.children[ch]!!
            }
        }

        current.isEnd = true
    }

    fun search(word: String): Boolean {
        val node = findNode(word)
        return node?.isEnd == true
    }

    private fun findNode(prefix: String): TrieNode? {
        var current: TrieNode? = root

        prefix.asSequence()
            .takeWhile { current != null }
            .forEach { ch ->
                current = current!!.children[ch]
            }

        return current
    }

    private class TrieNode(
        val children: MutableMap<Char, TrieNode> = mutableMapOf(),
        var isEnd: Boolean = false,
    )
}
