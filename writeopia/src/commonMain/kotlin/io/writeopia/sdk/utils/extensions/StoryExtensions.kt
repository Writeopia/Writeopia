package io.writeopia.sdk.utils.extensions

import io.writeopia.sdk.model.story.Section
import io.writeopia.sdk.models.story.StoryStep

fun Map<Double, StoryStep>.toEditState(): MutableMap<Double, List<StoryStep>> =
    mapValues { (_, story) -> listOf(story) }.toMutableMap()

fun <T> Iterable<T>.associateWithPosition(): Map<Double, T> {
    var acc = -1.0

    return associateBy { ++acc }
}

fun Map<Double, StoryStep>.noContent(): Boolean =
    this.values.all { storyStep ->
        storyStep.run {
            url.isNullOrBlank() &&
                path.isNullOrBlank() &&
                text.isNullOrBlank() &&
                steps.isEmpty()
        }
    }

fun Iterable<StoryStep>.toSections(): List<Section> {
    if (this.toList().isEmpty()) return emptyList()

    return this
        .filter { story -> !story.text.isNullOrBlank() }
        .fold(emptyList()) { acc, story ->
            when {
                acc.isEmpty() && !story.isTitle() -> {
                    listOf(Section(title = story.text ?: "", content = listOf(story.text ?: "")))
                }

                story.isTitle() -> {
                    acc + Section(title = story.text ?: "", content = emptyList())
                }

                else -> {
                    val lastSection = acc.last()
                    val newAcc = acc.dropLast(1)

                    newAcc + lastSection.copy(content = lastSection.content + (story.text ?: ""))
                }
            }
        }
}

fun Map<Double, StoryStep>.toSections(): List<Section> = this.values.toSections()
