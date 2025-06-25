package io.writeopia.persistence

import io.writeopia.model.OllamaConfig
import kotlinx.coroutines.flow.StateFlow

interface OllamaDao {

    suspend fun getConfiguration(id: String): OllamaConfig?

    suspend fun saveConfiguration(id: String, ollamaConfig: OllamaConfig)

    fun listenForConfiguration(id: String): StateFlow<OllamaConfig?>

    suspend fun refreshStateOfId(id: String)

    suspend fun updateConfiguration(id: String, ollamaConfigFn: OllamaConfig.() -> OllamaConfig)
}
