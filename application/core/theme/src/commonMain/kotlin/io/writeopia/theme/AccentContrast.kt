package io.writeopia.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Moves [accent] towards black (light background) or white (dark one) until it reaches
 * [minContrast] against [background]. The dynamic accent can be pale in light mode and dark in dark
 * mode, so anything drawn in the accent over a surface (icons, borders) goes through this.
 */
fun accentWithContrast(accent: Color, background: Color, minContrast: Float = 4.5F): Color {
    val target = if (background.luminance() > 0.5F) Color.Black else Color.White

    for (step in 0..10) {
        val candidate = lerp(accent, target, step / 10F)

        if (contrastRatio(candidate, background) >= minContrast) return candidate
    }

    return target
}

private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()

    return (maxOf(l1, l2) + 0.05F) / (minOf(l1, l2) + 0.05F)
}
