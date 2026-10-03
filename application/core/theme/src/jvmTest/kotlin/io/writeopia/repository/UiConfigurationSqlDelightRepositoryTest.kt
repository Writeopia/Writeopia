package io.writeopia.repository

import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.theme.UiConfigurationSqlDelightDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UiConfigurationSqlDelightRepositoryTest {

    @Test
    fun shouldBePossibleToSaveAndGetColorOptionTheme() = runTest {
        UiConfigurationRepositoryCommonTest.shouldBePossibleToSaveAndGetColorTheme(getRepository())
    }

    @Test
    fun shouldBePossibleToKeepChoiceToMoveSideMenu() = runTest {
        UiConfigurationRepositoryCommonTest.shouldBePossibleToKeepChoiceToMoveSideMenu(
            getRepository()
        )
    }

    @Test
    fun shouldBePossibleToKeepTheAiProvider() = runTest {
        UiConfigurationRepositoryCommonTest.shouldBePossibleToKeepTheAiProvider(getRepository())
    }

    private suspend fun getRepository(): UiConfigurationRepository {
        val database = DatabaseFactory.createDatabase(DriverFactory())
        return UiConfigurationSqlDelightDao(database)
            .let(::UiConfigurationSqlDelightRepository)
    }
}
