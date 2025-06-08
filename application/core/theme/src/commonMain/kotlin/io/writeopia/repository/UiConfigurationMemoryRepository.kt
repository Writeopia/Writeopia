package io.writeopia.repository

import io.writeopia.model.ColorThemeOption
import io.writeopia.model.UiConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class UiConfigurationMemoryRepository : UiConfigurationRepository {
    private var uiConfiguration = MutableStateFlow(
        UiConfiguration(
            userId = "disconnected_user",
            colorThemeOption = ColorThemeOption.SYSTEM,
            sideMenuWidth = 280F
        )
    )

    override suspend fun insertUiConfiguration(uiConfiguration: UiConfiguration) {
        this.uiConfiguration.value = uiConfiguration
    }

    override suspend fun getUiConfigurationEntity(userId: String): UiConfiguration =
        uiConfiguration.value

    override suspend fun updateConfiguration(
        userId: String,
        change: (UiConfiguration) -> UiConfiguration
    ) {
        uiConfiguration.value = uiConfiguration.value.let(change)
    }

    override fun listenForUiConfiguration(
        getUserId: String,
        coroutineScope: CoroutineScope
    ): Flow<UiConfiguration?> = uiConfiguration
}
