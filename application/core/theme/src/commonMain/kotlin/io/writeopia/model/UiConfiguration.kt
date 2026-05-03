package io.writeopia.model

data class UiConfiguration(
    val userId: String,
    val colorThemeOption: ColorThemeOption,
    val sideMenuWidth: Float,
    val font: Font = Font.SYSTEM,
    val persistenceMode: PersistenceMode = PersistenceMode.LOCAL_DATABASE
)
