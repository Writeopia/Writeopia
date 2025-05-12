package io.writeopia.sqldelight.dao

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import io.writeopia.app.sql.NotesConfiguration
import io.writeopia.app.sql.NotesConfigurationEntityQueries
import io.writeopia.app.sql.OnboardingEntityQueries
import io.writeopia.app.sql.SelfHostedConfiguration
import io.writeopia.app.sql.SelfHostedConfigurationEntityQueries
import io.writeopia.app.sql.WorkspaceConfiguration
import io.writeopia.app.sql.WorkspaceConfigurationEntityQueries
import io.writeopia.common.utils.extensions.toBoolean
import io.writeopia.sql.WriteopiaDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class ConfigurationSqlDelightDao(database: WriteopiaDb?) {

    private val notesConfigurationQueries: NotesConfigurationEntityQueries? =
        database?.notesConfigurationEntityQueries

    private val workspaceConfigurationQueries: WorkspaceConfigurationEntityQueries? =
        database?.workspaceConfigurationEntityQueries

    private val selfHostedConfigurationQueries: SelfHostedConfigurationEntityQueries? =
        database?.selfHostedConfigurationEntityQueries

    private val onboardingQueries: OnboardingEntityQueries? =
        database?.onboardingEntityQueries

    private val selfHostedConfigurationState = MutableStateFlow<SelfHostedConfiguration?>(null)

    suspend fun saveNotesConfiguration(notesConfiguration: NotesConfiguration) {
        notesConfiguration.run {
            notesConfigurationQueries?.insert(user_id, arrangement_type, order_by)
        }
    }

    suspend fun saveWorkspaceConfiguration(workspaceConfiguration: WorkspaceConfiguration) {
        workspaceConfiguration.run {
            workspaceConfigurationQueries?.insert(
                user_id = user_id,
                path = path,
                has_first_configuration = has_first_configuration,
            )
        }
    }

    suspend fun saveSelfHostedConfiguration(selfHostedConfiguration: SelfHostedConfiguration) {
        selfHostedConfiguration.run {
            selfHostedConfigurationQueries?.insert(user_id, url)
        }
        selfHostedConfigurationState.value = selfHostedConfiguration
    }

    suspend fun getConfigurationByUserId(userId: String): NotesConfiguration? =
        notesConfigurationQueries?.selectConfigurationByUserId(userId)?.awaitAsOneOrNull()

    suspend fun getWorkspaceByUserId(userId: String): WorkspaceConfiguration? =
        workspaceConfigurationQueries?.selectWorkspaceConfigurationByUserId(userId)
            ?.awaitAsOneOrNull()

    suspend fun getSelfHostedByUserId(userId: String): SelfHostedConfiguration? =
        selfHostedConfigurationQueries?.selectSelfHostedByUserId(userId)?.awaitAsOneOrNull()?.also {
            selfHostedConfigurationState.value = it
        }

    suspend fun listenForSelfHostedConfigurationByUserId(userId: String): Flow<SelfHostedConfiguration?> {
        val config = getSelfHostedByUserId(userId)
        selfHostedConfigurationState.value = config
        return selfHostedConfigurationState
    }

    suspend fun deleteSelfHostedConfiguration(userId: String) {
        selfHostedConfigurationQueries?.delete(userId)
        selfHostedConfigurationState.value = null
    }

    suspend fun isOnboarded(): Boolean =
        onboardingQueries?.selectIsOnboarded()?.executeAsOneOrNull()?.is_onboarded?.toBoolean()
            ?: false

    suspend fun setOnboarded() {
        onboardingQueries?.insert(is_onboarded = 1L)
    }
}
