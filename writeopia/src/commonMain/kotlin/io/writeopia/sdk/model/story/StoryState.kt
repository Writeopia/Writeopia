package io.writeopia.sdk.model.story

import io.writeopia.sdk.models.story.StoryStep

/**
 * The state of document of the TextEditor of Writeopia. This class has all the stories in their
 * updated state and which one has the current focus.
 */
data class StoryState(
    val stories: Map<Int, StoryStep>,
    val lastEdit: LastEdit = LastEdit.Nothing,
    val focus: Int? = null,
    val selection: Selection = Selection.start()
)
