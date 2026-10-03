package io.writeopia.model

data class UiConfiguration(
    val userId: String,
    val colorThemeOption: ColorThemeOption,
    val accentColor: AccentColor = AccentColor.PURPLE,
    val sideMenuWidth: Float,
    val font: Font = Font.SYSTEM,
    /** The AI the user picked; null until a choice is made, so each platform applies its default. */
    val aiProvider: AiProvider? = null,
)
