package io.writeopia.model

data class UiConfiguration(
    val userId: String,
    val colorThemeOption: ColorThemeOption,
    val accentColor: AccentColor = AccentColor.PURPLE,
    val sideMenuWidth: Float,
    val font: Font = Font.SYSTEM
)
