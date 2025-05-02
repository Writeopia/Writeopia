package io.writeopia.core.configuration.repository

import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.sdk.models.sorting.OrderBy
import kotlinx.coroutines.flow.Flow

interface ConfigurationRepository : WorkspaceConfigRepository {

    suspend fun saveDocumentArrangementPref(arrangement: NotesArrangement, userId: String)

    suspend fun saveDocumentSortingPref(orderBy: OrderBy, userId: String)

    suspend fun arrangementPref(userId: String): String

    suspend fun getOrderPreference(userId: String): String

    suspend fun listenForArrangementPref(userId: String): Flow<String>

    suspend fun listenOrderPreference(userId: String): Flow<String>

    override suspend fun saveWorkspacePath(path: String, userId: String)

    override suspend fun loadWorkspacePath(userId: String): String?

    suspend fun hasFirstConfiguration(userId: String): Boolean

    suspend fun setTutorialNotes(hasTutorials: Boolean, userId: String)

    // Self-hosted backend URL
    suspend fun saveSelfHostedBackendUrl(url: String, userId: String)

    suspend fun loadSelfHostedBackendUrl(userId: String): String?

    suspend fun listenForSelfHostedBackendUrl(userId: String): Flow<String?>

    suspend fun clearSelfHostedBackendUrl(userId: String)
}
