package io.writeopia.core.configuration.repository

import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.sdk.models.sorting.OrderBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryConfigurationRepository private constructor() : ConfigurationRepository {

    private val arrangementPrefs = mutableMapOf<String, String>()
    private val sortPrefs = mutableMapOf<String, String>()
    private val workSpacePrefs = mutableMapOf<String, String>()
    private val selfHostedBackendPrefs = mutableMapOf<String, String>()

    private val arrangementPrefsState = MutableStateFlow(NotesArrangement.STAGGERED_GRID.type)
    private val sortPrefsState = MutableStateFlow(OrderBy.UPDATE.type)
    private val selfHostedBackendState = MutableStateFlow<String?>(null)

    override suspend fun saveDocumentArrangementPref(
        arrangement: NotesArrangement,
        userId: String
    ) {
        arrangementPrefs[userId] = arrangement.type
        arrangementPrefsState.value = arrangement.type
    }

    override suspend fun saveDocumentSortingPref(orderBy: OrderBy, userId: String) {
        sortPrefs[userId] = orderBy.type
        sortPrefsState.value = orderBy.type
    }

    override suspend fun arrangementPref(userId: String): String =
        arrangementPrefs[userId] ?: NotesArrangement.STAGGERED_GRID.type

    override suspend fun getOrderPreference(userId: String): String =
        sortPrefs[userId] ?: OrderBy.NAME.type

    override suspend fun saveWorkspacePath(path: String, userId: String) {
        workSpacePrefs[userId] = path
    }

    override suspend fun loadWorkspacePath(userId: String): String? =
        workSpacePrefs[userId]

    override suspend fun listenForArrangementPref(userId: String): Flow<String> =
        arrangementPrefsState.asStateFlow()

    override suspend fun listenOrderPreference(userId: String): Flow<String> =
        sortPrefsState.asStateFlow()

    override suspend fun saveSelfHostedBackendUrl(url: String, userId: String) {
        selfHostedBackendPrefs[userId] = url
        selfHostedBackendState.value = url
    }

    override suspend fun loadSelfHostedBackendUrl(userId: String): String? =
        selfHostedBackendPrefs[userId]

    override suspend fun listenForSelfHostedBackendUrl(userId: String): Flow<String?> =
        selfHostedBackendState.asStateFlow()

    override suspend fun clearSelfHostedBackendUrl(userId: String) {
        selfHostedBackendPrefs.remove(userId)
        selfHostedBackendState.value = null
    }

    override suspend fun hasFirstConfiguration(userId: String): Boolean = false

    override suspend fun setTutorialNotes(hasTutorials: Boolean, userId: String) {}

    override suspend fun isOnboarded(): Boolean = true

    override suspend fun setOnboarded() {}

    companion object {
        private var instance: InMemoryConfigurationRepository? = null

        fun singleton(): InMemoryConfigurationRepository =
            instance ?: InMemoryConfigurationRepository().also {
                instance = it
            }
    }
}
