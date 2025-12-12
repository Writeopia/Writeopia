package io.writeopia.persistence

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.app.sql.OllamaEntityQueries
import io.writeopia.model.OllamaConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OllamaSqlDao(private val ollamaQueries: OllamaEntityQueries?) : OllamaDao {

    private val _ollamaConfigState = MutableStateFlow<OllamaConfig?>(null)

    override suspend fun getConfiguration(id: String): OllamaConfig? =
        ollamaQueries?.query(id)?.awaitAsOneOrNull()?.let { entity ->
            OllamaConfig(url = entity.url, selectedModel = entity.selected_model ?: "")
        }

    override suspend fun saveConfiguration(id: String, ollamaConfig: OllamaConfig) {
        ollamaQueries?.insert(
            id = id,
            url = ollamaConfig.url,
            selected_model = ollamaConfig.selectedModel
        )
    }

    override suspend fun updateConfiguration(
        id: String,
        ollamaConfigFn: OllamaConfig.() -> OllamaConfig
    ) {
        val config = getConfiguration(id) ?: OllamaConfig()

        saveConfiguration(id, ollamaConfigFn(config))
    }

    override fun listenForConfiguration(id: String): StateFlow<OllamaConfig?> {
        return _ollamaConfigState
    }

    override suspend fun refreshStateOfId(id: String) {
        _ollamaConfigState.value = getConfiguration(id)
    }
}
