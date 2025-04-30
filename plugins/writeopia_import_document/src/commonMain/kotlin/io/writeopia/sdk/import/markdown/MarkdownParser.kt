package io.writeopia.sdk.import.markdown

import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.serialization.data.StoryStepApi
import io.writeopia.sdk.serialization.data.StoryTypeApi
import io.writeopia.sdk.serialization.data.TagInfoApi

object MarkdownParser {

    fun parse(lines: List<String>): List<StoryStepApi> {
        var acc = -1
        return lines.map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, trimmed ->
                acc++
                when {
                    i == 0 && trimmed.startsWith("#") -> {
                        val type = StoryTypes.TITLE.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(1).trimStart(),
                            position = acc
                        )
                    }

                    i == 0 && !trimmed.startsWith("#") -> {
                        val type = StoryTypes.TITLE.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = "",
                            position = acc
                        )
                    }

                    trimmed.startsWith("####") -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(4).trimStart(),
                            tags = setOf(TagInfoApi(Tag.H4.name, 0)),
                            position = acc
                        )
                    }

                    trimmed.startsWith("###") -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(3).trimStart(),
                            tags = setOf(TagInfoApi(Tag.H3.name, 0)),
                            position = acc
                        )
                    }

                    trimmed.startsWith("##") -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(2).trimStart(),
                            tags = setOf(TagInfoApi(Tag.H2.name, 0)),
                            position = acc
                        )
                    }

                    trimmed.startsWith("#") -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(1).trimStart(),
                            tags = setOf(TagInfoApi(Tag.H1.name, 0)),
                            position = acc
                        )
                    }

//                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
//                    StoryType.OrderedListItem to trimmed.replace(Regex("^\\d+\\.\\s+"), "")
//                }

                    trimmed.startsWith("---") -> {
                        val type = StoryTypes.DIVIDER.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            position = acc
                        )
                    }

                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed.drop(2).trimStart(),
                            position = acc
                        )
                    }

                    else -> {
                        val type = StoryTypes.TEXT.type
                        StoryStepApi(
                            type = StoryTypeApi(type.name, type.number),
                            text = trimmed,
                            position = acc
                        )
                    }
                }
            }
    }
}
