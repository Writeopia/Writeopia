package io.writeopia.editor.features.editor.viewmodel

import kotlin.math.roundToInt

/**
 * The zoom of the editor's text (Cmd/Ctrl + and Cmd/Ctrl -). It lives only in the view model:
 * it is never saved and starts back at [DEFAULT] with each document.
 */
object EditorFontScale {
    const val DEFAULT = 1F
    const val MIN = 0.5F
    const val MAX = 2.5F
    private const val STEP = 0.1F

    fun larger(scale: Float): Float = (scale + STEP).rounded().coerceAtMost(MAX)

    fun smaller(scale: Float): Float = (scale - STEP).rounded().coerceAtLeast(MIN)

    // Steps of 0.1 drift in Float arithmetic; keeps the scale on whole tenths.
    private fun Float.rounded(): Float = (this * 10).roundToInt() / 10F
}
