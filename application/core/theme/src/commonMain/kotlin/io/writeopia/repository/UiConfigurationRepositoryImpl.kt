package io.writeopia.repository

import io.writeopia.model.ColorThemeOption
import io.writeopia.model.UiConfiguration
import io.writeopia.repository.extensions.toModel
import io.writeopia.repository.extensions.toRoomEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UiConfigurationRepositoryImpl(
    private val uiConfigurationDao: UiConfigurationDao
) : UiConfigurationRepository {

    override suspend fun insertUiConfiguration(uiConfiguration: UiConfiguration) {
        uiConfigurationDao.saveUiConfiguration(uiConfiguration.toRoomEntity())
    }

    override suspend fun getUiConfigurationEntity(userId: String): UiConfiguration? =
        uiConfigurationDao.getConfigurationByUserId(userId)?.toModel()

    override suspend fun updateConfiguration(
        userId: String,
        change: (UiConfiguration) -> UiConfiguration
    ) {
        val entity = getUiConfigurationEntity(userId)

        if (entity != null) {
            insertUiConfiguration(entity.let(change))
        } else {
            insertUiConfiguration(
                UiConfiguration(
                    userId = userId,
                    colorThemeOption = ColorThemeOption.SYSTEM,
                    sideMenuWidth = 500F
                ).let(change)
            )
        }
    }

    override fun listenForUiConfiguration(
        getUserId: String,
        coroutineScope: CoroutineScope
    ): Flow<UiConfiguration?> =
        uiConfigurationDao.listenForConfigurationByUserId("disconnected_user")
            .map { entity ->
                entity?.toModel()
            }
}
