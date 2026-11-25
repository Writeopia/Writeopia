package io.writeopia.sdk.models.story

/**
 * How the pictures of `EQUATION` steps are drawn. The backend renders the LaTeX with black glyphs
 * on a transparent background at [SCALE] pixels per point, and the apps show the picture at its
 * pixel size divided by [SCALE] (in dp or points), tinted with the text color, so it is crisp on
 * dense screens and readable in both themes.
 */
object EquationImage {
    const val SCALE = 3

    /** The font size of the rendered formula, in points, before [SCALE]. */
    const val FONT_SIZE_POINTS = 20f
}
