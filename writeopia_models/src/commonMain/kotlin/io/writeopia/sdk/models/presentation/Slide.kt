package io.writeopia.sdk.models.presentation

import io.writeopia.sdk.models.story.StoryStep

/**
 * One slide of a presentation: a title and the steps shown below it, in order.
 */
data class Slide(
    val title: String,
    val content: List<StoryStep> = emptyList()
)
