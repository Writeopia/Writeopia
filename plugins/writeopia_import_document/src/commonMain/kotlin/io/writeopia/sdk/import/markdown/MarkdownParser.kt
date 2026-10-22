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
        return lines.map { it.trim() }
            .mapIndexed { i, trimmed ->
                acc++
                val stepApi = parseLine(acc, i, trimmed)

                InTextMarkdownHandler.handleMarkdown(stepApi.toModel()).toApi(Rank.at(acc))
            }
    }

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
