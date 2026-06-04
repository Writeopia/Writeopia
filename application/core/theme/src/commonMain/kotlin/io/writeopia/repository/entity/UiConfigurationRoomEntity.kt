package io.writeopia.repository.entity

class UiConfigurationEntity(
    val userId: String,
    val colorThemeOption: String,
    val accentColor: String = "purple",
    val font: String
)
