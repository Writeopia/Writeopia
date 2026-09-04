package io.writeopia.persistence

import io.writeopia.model.LocalAiConfig
import kotlinx.coroutines.flow.StateFlow

interface LocalAiDao {

    suspend fun getConfiguration(id: String): LocalAiConfig?

    suspend fun saveConfiguration(id: String, localAiConfig: LocalAiConfig)

    fun listenForConfiguration(id: String): StateFlow<LocalAiConfig?>

    suspend fun refreshStateOfId(id: String)

    suspend fun updateConfiguration(id: String, localAiConfigFn: LocalAiConfig.() -> LocalAiConfig)
}
