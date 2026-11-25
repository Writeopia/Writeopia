package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.manager.InTextMarkdownHandler
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.data.StoryTypeApi
import io.writeopia.sdk.serialization.data.TagInfoApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel

object MarkdownParser {

    fun parse(lines: List<String>): List<StoryStepApi> {
        var acc = -1
        return joinEquationBlocks(lines.map { it.trim() })
            .mapIndexed { i, trimmed ->
                acc++
                val stepApi = parseLine(acc, i, trimmed)

                // The LaTeX of an equation is kept as written: `*`, `_` and `**` mean nothing
                // to Markdown there.
                if (stepApi.type.number == StoryTypes.EQUATION.type.number) {
                    stepApi
                } else {
                    InTextMarkdownHandler.handleMarkdown(stepApi.toModel()).toApi(Rank.at(acc))
                }
            }
    }

    /**
     * An equation written over several lines, from a line starting with `$$` to the line ending
     * with `$$`, becomes one `$$...$$` line. A block that never closes stays as its lines.
     */
    private fun joinEquationBlocks(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val opensBlock = line.startsWith(EQUATION_DELIMITER) &&
                !(line.length > EQUATION_DELIMITER.length * 2 && line.endsWith(EQUATION_DELIMITER))
            val closing = if (opensBlock) {
                (index + 1 until lines.size).firstOrNull { lines[it].endsWith(EQUATION_DELIMITER) }
            } else {
                null
            }

            if (closing != null) {
                val latex = buildList {
                    add(line.removePrefix(EQUATION_DELIMITER))
                    addAll(lines.subList(index + 1, closing))
                    add(lines[closing].removeSuffix(EQUATION_DELIMITER))
                }.joinToString(" ") { part -> part.trim() }.trim()
                result.add("$EQUATION_DELIMITER $latex $EQUATION_DELIMITER")
                index = closing + 1
            } else {
                result.add(line)
                index++
            }
        }
        return result
    }

    private const val EQUATION_DELIMITER = "$$"

    private fun parseLine(acc: Int, i: Int, trimmed: String) =
        // Take care when moving the code, order matters!
        when {
            i == 0 && trimmed.startsWith("#") -> {
                val type = StoryTypes.TITLE.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(1).trimStart(),
                    position = Rank.at(acc)
                )
            }

            i == 0 && !trimmed.startsWith("#") -> {
                val type = StoryTypes.TITLE.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = "",
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("####") -> {
                val type = StoryTypes.TEXT.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(4).trimStart(),
                    tags = setOf(TagInfoApi(Tag.H4.name, 0)),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("###") -> {
                val type = StoryTypes.TEXT.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(3).trimStart(),
                    tags = setOf(TagInfoApi(Tag.H3.name, 0)),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("##") -> {
                val type = StoryTypes.TEXT.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(2).trimStart(),
                    tags = setOf(TagInfoApi(Tag.H2.name, 0)),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("#") -> {
                val type = StoryTypes.TEXT.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(1).trimStart(),
                    tags = setOf(TagInfoApi(Tag.H1.name, 0)),
                    position = Rank.at(acc)
                )
            }

//                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
//                    StoryType.OrderedListItem to trimmed.replace(Regex("^\\d+\\.\\s+"), "")
//                }

            trimmed.length > EQUATION_DELIMITER.length * 2 &&
                trimmed.startsWith(EQUATION_DELIMITER) &&
                trimmed.endsWith(EQUATION_DELIMITER) -> {
                val type = StoryTypes.EQUATION.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.removePrefix(EQUATION_DELIMITER).removeSuffix(EQUATION_DELIMITER).trim(),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("---") -> {
                val type = StoryTypes.DIVIDER.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("[] ") || trimmed.startsWith("-[] ") -> {
                val type = StoryTypes.CHECK_ITEM.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(3).trimStart(),
                    position = Rank.at(acc)
                )
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val type = StoryTypes.UNORDERED_LIST_ITEM.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed.drop(2).trimStart(),
                    position = Rank.at(acc)
                )
            }

            else -> {
                val type = StoryTypes.TEXT.type
                StoryStepApi(
                    type = StoryTypeApi(type.name, type.number),
                    text = trimmed,
                    position = Rank.at(acc)
                )
            }
        }
}
