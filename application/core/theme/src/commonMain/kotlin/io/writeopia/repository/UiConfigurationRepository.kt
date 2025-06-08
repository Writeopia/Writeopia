package io.writeopia.repository

import io.writeopia.model.UiConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface UiConfigurationRepository {
    suspend fun insertUiConfiguration(uiConfiguration: UiConfiguration)

    suspend fun getUiConfigurationEntity(userId: String): UiConfiguration?

    suspend fun updateConfiguration(userId: String, change: (UiConfiguration) -> UiConfiguration)

    fun listenForUiConfiguration(
        getUserId: String,
        coroutineScope: CoroutineScope
    ): Flow<UiConfiguration?>
}
