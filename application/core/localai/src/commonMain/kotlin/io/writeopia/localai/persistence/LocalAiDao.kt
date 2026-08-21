package io.writeopia.localai.persistence

import io.writeopia.localai.model.LocalAiConfig
import kotlinx.coroutines.flow.StateFlow

interface LocalAiDao {
    suspend fun getConfiguration(id: String): LocalAiConfig?
    suspend fun saveConfiguration(id: String, config: LocalAiConfig)
    fun listenForConfiguration(id: String): StateFlow<LocalAiConfig?>
}
