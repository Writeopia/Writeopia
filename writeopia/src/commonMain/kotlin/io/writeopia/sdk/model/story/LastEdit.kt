package io.writeopia.sdk.model.story

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep

/**
 * Last edition. This signs how the last edition in the [Document] was made and it allows the SDK
 * to react properly, for example saving just one line of change instead saving the whole document.
 */
sealed class LastEdit {
    /**
     * No edition was make
     */
    data object Nothing : LastEdit()

    /**
     * A whole edition was made like a line break (adding a new line) , a deletion
     * o a image upload between content.
     * In this case the whole document should be saved.
     * It is important to notice that when new content is added between content this will change all
     * the values of the positions, so it is necessary to save the whole document again.
     */
    data object Whole : LastEdit()

    /**
     * A edition in the line was made, but the positions were not affected. In this case it is
     * possible to update just one line.
     */
    data class LineEdition(val position: Int, val storyStep: StoryStep) : LastEdit()

    data class InfoEdition(val position: Int, val storyStep: StoryStep) : LastEdit()

    /**
     * Metadata was edited
     */
    data object Metadata : LastEdit()

    /**
     * A line break was made, creating a new line. This is more efficient than a Whole edit
     * because only the two affected story steps need to be saved.
     * @param originalStep The updated original line with its database position
     * @param newStep The newly created line with its intermediate database position
     */
    data class LineBreakEdition(
        val originalStep: Pair<Double, StoryStep>,
        val newStep: Pair<Double, StoryStep>
    ) : LastEdit()
}
