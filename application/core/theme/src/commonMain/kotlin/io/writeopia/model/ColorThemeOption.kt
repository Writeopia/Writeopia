package io.writeopia.model

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode

enum class ColorThemeOption(val theme: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromText(theme: String?): ColorThemeOption? = entries.find { option ->
            option.theme == theme
        }
    }
}

@Composable
fun ColorThemeOption?.isDarkTheme(): Boolean =
    when (this) {
        ColorThemeOption.LIGHT -> false
        ColorThemeOption.DARK -> true
        ColorThemeOption.SYSTEM -> isSystemInDarkMode()
        else -> isSystemInDarkMode()
    }
