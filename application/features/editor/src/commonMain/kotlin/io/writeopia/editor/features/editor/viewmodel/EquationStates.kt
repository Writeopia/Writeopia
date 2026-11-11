package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.story.StoryStep

/** The options dialog of a drawing ("..."), and the conversion to an equation running from it. */
data class DrawingOptionsState(
    val step: StoryStep,
    val position: String,
    val phase: Phase = Phase.IDLE,
    /** Why the conversion failed, shown with the retry. */
    val error: String? = null,
) {
    enum class Phase {
        IDLE,

        /** The cloud AI is reading the strokes. */
        CONVERTING,

        /** The cloud AI can't answer right now (no session, or an offline workspace). */
        UNAVAILABLE,

        FAILED,
    }
}

/** The dialog that edits the LaTeX of an equation step, and the rendering of what was typed. */
data class EquationEditionState(
    val position: String,
    val latex: String,
    val rendering: Boolean = false,
    val error: String? = null,
)
