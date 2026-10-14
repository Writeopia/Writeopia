package io.writeopia.ui.model

import io.writeopia.sdk.models.story.StoryStep

/**
 * A link card that was just created from a pasted address. The app listens to these to ask the
 * backend for a description of the page.
 *
 * @param storyStep The new card. Its url is the pasted address and its text is still empty.
 * @param position Where the card is in the document.
 */
data class LinkCardCreation(val storyStep: StoryStep, val position: Double)
