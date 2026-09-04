package io.writeopia.persistence

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.app.sql.LocalAiEntityQueries
import io.writeopia.model.LocalAiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalAiSqlDao(private val localAiQueries: LocalAiEntityQueries?) : LocalAiDao {

    private val _localAiConfigState = MutableStateFlow<LocalAiConfig?>(null)

    override suspend fun getConfiguration(id: String): LocalAiConfig? =
        localAiQueries?.query(id)?.awaitAsOneOrNull()?.let { entity ->
            LocalAiConfig(url = entity.url, selectedModel = entity.selected_model ?: "")
        }

    override suspend fun saveConfiguration(id: String, localAiConfig: LocalAiConfig) {
        localAiQueries?.insert(
            id = id,
            url = localAiConfig.url,
            selected_model = localAiConfig.selectedModel
        )
    }

    override suspend fun updateConfiguration(
        id: String,
        localAiConfigFn: LocalAiConfig.() -> LocalAiConfig
    ) {
        val config = getConfiguration(id) ?: LocalAiConfig()

        saveConfiguration(id, localAiConfigFn(config))
    }

    override fun listenForConfiguration(id: String): StateFlow<LocalAiConfig?> = _localAiConfigState

    override suspend fun refreshStateOfId(id: String) {
        _localAiConfigState.value = getConfiguration(id)
    }
}
