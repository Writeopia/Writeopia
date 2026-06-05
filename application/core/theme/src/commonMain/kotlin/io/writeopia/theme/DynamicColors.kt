package io.writeopia.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Data class holding the dynamic accent colors from the system.
 */
data class DynamicAccentColors(
    val lightPrimary: Color,
    val lightSecondary: Color,
    val darkPrimary: Color,
    val darkSecondary: Color
)

/**
 * Returns the dynamic accent colors from the system if available.
 * On Android 12+, this returns colors derived from the user's wallpaper.
 * On other platforms, returns null.
 */
@Composable
expect fun getDynamicAccentColors(): DynamicAccentColors?
