package io.writeopia.sdk.utils.extensions

import io.writeopia.sdk.model.story.Section
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.iterables.POSITION_GAP
import io.writeopia.sdk.utils.iterables.mapValuesSorted

fun Map<Long, StoryStep>.toEditState(): MutableMap<Long, List<StoryStep>> =
    mapValuesSorted { (_, story) -> listOf(story) }

/** Positions [POSITION_GAP] apart, from 0, for the elements of a new document. */
fun <T> Iterable<T>.associateWithPosition(): Map<Long, T> {
    val result = sortedMutableMapOf<Long, T>()
    forEachIndexed { index, element -> result[index * POSITION_GAP] = element }
    return result
}

fun Map<Long, StoryStep>.noContent(): Boolean =
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

fun Map<Long, StoryStep>.toSections(): List<Section> = this.values.toSections()
