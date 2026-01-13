package io.writeopia.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode

private val DarkColorPalette = darkColorScheme(
    primary = LIGHT_PURPLE,
    secondary = DARK_PURPLE,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    surface = LIGHT_PURPLE,
    inverseSurface = DARK_PURPLE
)

private val LightColorPalette = lightColorScheme(
    primary = LIGHT_PURPLE,
    secondary = DARK_PURPLE,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF363636),
    surface = LIGHT_PURPLE,
    inverseSurface = DARK_PURPLE
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
    val cardPlaceHolderBackground: Color,
    val searchBackground: Color,
    val linkColor: Color,
    val dividerColor: Color,
    val defaultButton: Color
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
        cardPlaceHolderBackground = Color.Unspecified,
        searchBackground = Color.Unspecified,
        linkColor = Color.Unspecified,
        dividerColor = Color.Unspecified,
        defaultButton = Color.Unspecified
    )
}

@Composable
fun WriteopiaTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    content: @Composable () -> Unit
) {
    val globalBackground = if (darkTheme) Color(0xFF252525) else Color(0xFFF8F0F9)

    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }.copy(surfaceVariant = globalBackground)

    val optionsSelector = if (darkTheme) Color(0x22FFFFFF) else Color(0x22000000)
    val textLight = if (darkTheme) Color(0xFFDFDFDF) else Color(0xFF444444)
    val textLighter = if (darkTheme) Color(0xFFAAAAAA) else Color(0xFF666666)
    val tintLight = if (darkTheme) Color(0xFFDFDFDF) else Color(0xFF444444)
    val highlight = if (darkTheme) Color(0xFF616161) else Color(0xFFE0E0E0)
    val lightBackground = if (darkTheme) Color(0xFF222222) else Color(0xFFF1F1F1)
    val cardBackground = if (darkTheme) colors.surfaceContainer else colors.surfaceContainer
    val cardShadow = if (darkTheme) colors.background else Color.Gray
    val cardPlaceHolderBackground = if (darkTheme) colors.background else colors.surfaceVariant
    val searchBackground = if (darkTheme) colors.surfaceVariant else colors.background
    val linkColor = if (darkTheme) Color(0xFF42A5F5) else Color.Blue
    val dividerColor = if (darkTheme) Color(0xFF333333) else Color(0xFFDFDFDF)
    val defaultButton = if (darkTheme) colors.background else colors.background

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
        cardPlaceHolderBackground = cardPlaceHolderBackground,
        searchBackground = searchBackground,
        linkColor = linkColor,
        dividerColor = dividerColor,
        defaultButton = defaultButton,
    )

    CompositionLocalProvider(LocalWriteopiaColors provides writeopiaColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
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
