package io.writeopia.sdk.utils.extensions

import io.writeopia.sdk.model.story.Section
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.utils.iterables.mapValuesSorted

fun Map<String, StoryStep>.toEditState(): MutableMap<String, List<StoryStep>> =
    mapValuesSorted { (_, story) -> listOf(story) }

/** The ranks of a document written in order, from [Rank.FIRST], for the elements of a new document. */
fun <T> Iterable<T>.associateWithPosition(): Map<String, T> {
    val result = sortedMutableMapOf<String, T>()
    forEachIndexed { index, element -> result[Rank.at(index)] = element }
    return result
}

fun Map<String, StoryStep>.noContent(): Boolean =
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

fun Map<String, StoryStep>.toSections(): List<Section> = this.values.toSections()
