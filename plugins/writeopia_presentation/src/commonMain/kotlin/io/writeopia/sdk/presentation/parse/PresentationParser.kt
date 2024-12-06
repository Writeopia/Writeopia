package io.writeopia.sdk.presentation.parse

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.presentation.model.SlidePage

object PresentationParser {

    fun parse(data: Iterable<StoryStep>): List<SlidePage> =
        data.fold(emptyList()) { acc, storyStep ->
            if (storyStep.isTitle()) {
                acc + SlidePage(title = storyStep.text ?: "")
            } else {
                val lastStep = acc.last()
                val content = lastStep.content
                val newLast = lastStep.copy(content = content + storyStep)

                val reducedAcc = if (acc.isNotEmpty()) acc.dropLast(1) else acc
                reducedAcc + newLast
            }
        }
}

private fun StoryStep.isTitle() =
    when (StoryTypes.fromNumber(this.type.number)) {
        StoryTypes.TEXT -> Tag.titleTags()
            .map { TagInfo(it) }
            .any { titleTag -> tags.contains(titleTag) }

        StoryTypes.TITLE -> true

        else -> false
    }
