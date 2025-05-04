package io.writeopia.core.configuration.repository

import io.writeopia.app.sql.NotesConfiguration
import io.writeopia.app.sql.WorkspaceConfiguration
import io.writeopia.common.utils.extensions.toBoolean
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sqldelight.dao.ConfigurationSqlDelightDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ConfigurationSqlDelightRepository(
    private val configurationSqlDelightDao: ConfigurationSqlDelightDao
) : ConfigurationRepository {

    private val _arrangementPref: MutableStateFlow<String> =
        MutableStateFlow(NotesArrangement.GRID.type)
    private val _orderPreference: MutableStateFlow<String> = MutableStateFlow(OrderBy.UPDATE.type)

    override suspend fun saveDocumentArrangementPref(
        arrangement: NotesArrangement,
        userId: String
    ) {
        configurationSqlDelightDao.saveNotesConfiguration(
            NotesConfiguration(
                userId,
                arrangement.type,
                getOrderPreference(userId)
            )
        )

        refreshArrangementPref(userId)
    }

    override suspend fun saveDocumentSortingPref(orderBy: OrderBy, userId: String) {
        configurationSqlDelightDao.saveNotesConfiguration(
            NotesConfiguration(
                userId,
                arrangementPref(userId),
                orderBy.type
            )
        )

        refreshOrderPref(userId)
    }

    override suspend fun arrangementPref(userId: String): String =
        configurationSqlDelightDao.getConfigurationByUserId(userId)?.arrangement_type
            ?: NotesArrangement.STAGGERED_GRID.type

    override suspend fun getOrderPreference(userId: String): String =
        configurationSqlDelightDao.getConfigurationByUserId(userId)?.order_by
            ?: OrderBy.CREATE.type

    override suspend fun listenForArrangementPref(userId: String): Flow<String> {
        refreshArrangementPref(userId)

        return _arrangementPref
    }

    override suspend fun listenOrderPreference(userId: String): Flow<String> {
        refreshOrderPref(userId)

        return _orderPreference
    }

    override suspend fun saveWorkspacePath(path: String, userId: String) {
        configurationSqlDelightDao.saveWorkspaceConfiguration(
            WorkspaceConfiguration(path = path, user_id = userId, has_first_configuration = 1L)
        )
    }

    override suspend fun loadWorkspacePath(userId: String): String? =
        configurationSqlDelightDao.getWorkspaceByUserId(userId)?.path

    override suspend fun hasFirstConfiguration(userId: String): Boolean =
        configurationSqlDelightDao.getWorkspaceByUserId(userId)
            ?.has_first_configuration
            ?.toBoolean()
            ?: false

    override suspend fun setTutorialNotes(hasTutorials: Boolean, userId: String) {
        val currentConfiguration = configurationSqlDelightDao.getWorkspaceByUserId(userId)

        configurationSqlDelightDao.saveWorkspaceConfiguration(
            WorkspaceConfiguration(
                path = currentConfiguration?.path ?: "",
                user_id = userId,
                has_first_configuration = hasTutorials.toLong()
            )
        )
    }

    override suspend fun isOnboarded(): Boolean = configurationSqlDelightDao.isOnboarded()

    override suspend fun setOnboarded() {
        configurationSqlDelightDao.setOnboarded()
    }

    private suspend fun refreshArrangementPref(userId: String) {
        _arrangementPref.value = arrangementPref(userId)
    }

    private suspend fun refreshOrderPref(userId: String) {
        _orderPreference.value = getOrderPreference(userId)
    }
}
