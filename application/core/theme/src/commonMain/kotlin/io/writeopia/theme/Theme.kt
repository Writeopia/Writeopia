package io.writeopia.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.writeopia.model.AccentColor

private fun darkColorPalette(primary: Color, secondary: Color) = darkColorScheme(
    primary = primary,
    secondary = secondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    surface = primary,
    inverseSurface = secondary
)

private fun lightColorPalette(primary: Color, secondary: Color) = lightColorScheme(
    primary = primary,
    secondary = secondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF363636),
    surface = primary,
    inverseSurface = secondary
)

@Immutable
data class WriteopiaColors(
    val globalBackground: Color,
    val optionsSelector: Color,
    val lightBackground: Color,
    val textLight: Color,
    val textLighter: Color,
    val tintLight: Color,
    val highlight: Color,
    val selectedBg: Color,
    val cardBg: Color,
    val cardShadow: Color,
    val cardBorder: Color,
    val cardBorderSelected: Color,
    /** The accent made readable over the cards and surfaces (icons, selection borders). */
    val accentForeground: Color,
    val cardPlaceHolderBackground: Color,
    val searchBackground: Color,
    val linkColor: Color,
    val dividerColor: Color,
    val defaultButton: Color,
    val optionButton: Color
)

val LocalWriteopiaColors = staticCompositionLocalOf {
    WriteopiaColors(
        globalBackground = Color.Unspecified,
        optionsSelector = Color.Unspecified,
        lightBackground = Color.Unspecified,
        textLight = Color.Unspecified,
        textLighter = Color.Unspecified,
        tintLight = Color.Unspecified,
        highlight = Color.Unspecified,
        selectedBg = Color.Unspecified,
        cardBg = Color.Unspecified,
        cardShadow = Color.Unspecified,
        cardBorder = Color.Unspecified,
        cardBorderSelected = Color.Unspecified,
        accentForeground = Color.Unspecified,
        cardPlaceHolderBackground = Color.Unspecified,
        searchBackground = Color.Unspecified,
        linkColor = Color.Unspecified,
        dividerColor = Color.Unspecified,
        defaultButton = Color.Unspecified,
        optionButton = Color.Unspecified
    )
}

@Composable
fun WriteopiaTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    accentColor: AccentColor = AccentColor.DYNAMIC,
    content: @Composable () -> Unit
) {
    // Get dynamic colors if DYNAMIC is selected and available on this platform
    val dynamicColors = if (accentColor == AccentColor.DYNAMIC) {
        getDynamicAccentColors()
    } else {
        null
    }

    // Material's dynamic dark scheme has a light primary and the light one a dark primary. The
    // accent is the opposite here: dark in dark mode, light in light mode.
    val (primary, secondary) = when {
        dynamicColors != null -> {
            if (darkTheme) {
                dynamicColors.lightPrimary to dynamicColors.lightSecondary
            } else {
                dynamicColors.darkPrimary to dynamicColors.darkSecondary
            }
        }
        else -> {
            accentColor.lightColor to accentColor.darkColor
        }
    }

    val dynamicScheme = dynamicColors?.let { if (darkTheme) it.darkScheme else it.lightScheme }

    // With dynamic colors the background and the surfaces are tinted by the wallpaper (like
    // Google Keep); otherwise the fixed app background is used.
    val globalBackground = dynamicScheme?.surface
        ?: if (darkTheme) Color(0xFF252525) else Color(0xFFF8F0F9)

    val baseColors = if (darkTheme) {
        darkColorPalette(primary, secondary)
    } else {
        lightColorPalette(primary, secondary)
    }

    val colors = if (dynamicScheme != null) {
        // The text on the accent comes from the scheme the accent was taken from.
        val onAccent = (if (darkTheme) dynamicColors.lightScheme else dynamicColors.darkScheme)?.onPrimary

        dynamicScheme.copy(
            primary = primary,
            secondary = secondary,
            onPrimary = onAccent ?: baseColors.onPrimary,
            onSecondary = onAccent ?: baseColors.onSecondary,
            surfaceVariant = globalBackground,
            // The dialogs are Cards, which default to this level, the same one the buttons use
            // (defaultButton). A lower one makes the buttons stand out inside the dialogs.
            surfaceContainerHighest = dynamicScheme.surfaceContainerLow
        )
    } else {
        baseColors.copy(surfaceVariant = globalBackground)
    }

    val optionsSelector = if (darkTheme) Color(0x22FFFFFF) else Color(0x22000000)
    val textLight = if (darkTheme) Color(0xFFDFDFDF) else Color(0xFF444444)
    val textLighter = if (darkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)
    val tintLight = if (darkTheme) Color(0xFFDFDFDF) else Color(0xFF444444)
    // With dynamic colors the highlight is the button color pushed towards the text color, so it
    // stands out from the buttons (the gray was hard to see on the tinted surfaces).
    val highlight = dynamicScheme?.let {
        lerp(it.surfaceContainerHighest, it.onSurface, if (darkTheme) 0.25F else 0.22F)
    } ?: if (darkTheme) Color(0xFF616161) else Color(0xFFE0E0E0)
    val lightBackground = if (darkTheme) Color(0xFF222222) else Color(0xFFF1F1F1)
    val cardBackground = dynamicScheme?.surfaceContainerHigh ?: colors.surfaceContainer
    val cardShadow = if (darkTheme) colors.background else Color.Gray
    val cardBorder = dynamicScheme?.outlineVariant
        ?: if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFD9D2DA)
    val accentForeground = accentWithContrast(primary, cardBackground, minContrast = 3F)
    val cardBorderSelected = accentForeground
    val cardPlaceHolderBackground = if (darkTheme) colors.background else colors.surfaceVariant
    val searchBackground = if (darkTheme) colors.surfaceVariant else colors.background
    val linkColor = if (darkTheme) Color(0xFF42A5F5) else Color.Blue
    val dividerColor = if (darkTheme) Color(0xFF333333) else Color(0xFFDFDFDF)
    // With dynamic colors the sheets and menus are drawn on the (tinted) surface, so the buttons
    // need a higher surface to stand out from them.
    val defaultButton = dynamicScheme?.surfaceContainerHighest ?: colors.background
    // The option buttons of the settings: the same dynamic button color, or the translucent
    // overlay otherwise, which shows on the dialog whatever its color.
    val optionButton = dynamicScheme?.surfaceContainerHighest ?: optionsSelector

    val writeopiaColors = WriteopiaColors(
        globalBackground = globalBackground,
        lightBackground = lightBackground,
        optionsSelector = optionsSelector,
        textLight = textLight,
        textLighter = textLighter,
        tintLight = tintLight,
        highlight = highlight,
        selectedBg = MaterialTheme.colorScheme.primary.copy(0.15F),
        cardBg = cardBackground,
        cardShadow = cardShadow,
        cardBorder = cardBorder,
        cardBorderSelected = cardBorderSelected,
        accentForeground = accentForeground,
        cardPlaceHolderBackground = cardPlaceHolderBackground,
        searchBackground = searchBackground,
        linkColor = linkColor,
        dividerColor = dividerColor,
        defaultButton = defaultButton,
        optionButton = optionButton,
    )

    CompositionLocalProvider(LocalWriteopiaColors provides writeopiaColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = robotoTypography(),
            shapes = Shapes,
            content = content
        )
    }
}

object WriteopiaTheme {
    val colorScheme: WriteopiaColors
        @Composable
        get() = LocalWriteopiaColors.current
}
