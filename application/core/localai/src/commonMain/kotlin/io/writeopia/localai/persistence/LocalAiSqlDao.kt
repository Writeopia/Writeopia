package io.writeopia.localai.persistence

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.app.sql.LocalAiEntityQueries
import io.writeopia.localai.model.LocalAiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalAiSqlDao(private val queries: LocalAiEntityQueries?) : LocalAiDao {
    private val _configState = MutableStateFlow<LocalAiConfig?>(null)

    override suspend fun getConfiguration(id: String): LocalAiConfig? {
        return queries?.query(id)?.awaitAsOneOrNull()?.let {
            LocalAiConfig(
                modelPath = it.model_path,
                temperature = it.temperature.toFloat(),
                maxTokens = it.max_tokens.toInt(),
                contextLength = it.context_length.toInt()
            )
        }
    }

    override suspend fun saveConfiguration(id: String, config: LocalAiConfig) {
        queries?.insert(
            id = id,
            model_path = config.modelPath,
            temperature = config.temperature.toDouble(),
            max_tokens = config.maxTokens.toLong(),
            context_length = config.contextLength.toLong()
        )
        _configState.value = config
    }

    override fun listenForConfiguration(id: String): StateFlow<LocalAiConfig?> = _configState
}
