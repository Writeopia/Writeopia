package io.writeopia.repository.entity

class UiConfigurationEntity(
    val userId: String,
    val colorThemeOption: String,
    val font: String,
    val persistenceMode: String = "LOCAL_DATABASE"
)
