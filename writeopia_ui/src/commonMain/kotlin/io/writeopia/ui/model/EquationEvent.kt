package io.writeopia.ui.model

import io.writeopia.sdk.models.story.StoryStep

/**
 * What an equation step asks of the app, which has the backend that renders LaTeX to pictures.
 * The manager emits these on [io.writeopia.ui.manager.WriteopiaStateManager.equationEvents].
 */
sealed class EquationEvent {
    abstract val step: StoryStep
    abstract val position: String

    /** The user wants to edit the LaTeX of the step (the pencil of the equation). */
    data class Edit(override val step: StoryStep, override val position: String) : EquationEvent()

    /** The step became an equation and has no picture yet: its LaTeX must be rendered. */
    data class Render(override val step: StoryStep, override val position: String) : EquationEvent()
}
