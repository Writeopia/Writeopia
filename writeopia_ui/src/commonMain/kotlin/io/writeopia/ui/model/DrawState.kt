package io.writeopia.ui.model

/**
 * The state of document of the TextEditor of Writeopia. This class has all the stories in their
 * updated state and which one has the current focus.
 */
data class DrawState(
    val stories: List<DrawStory> = emptyList(),
    val focus: Double? = null
)
