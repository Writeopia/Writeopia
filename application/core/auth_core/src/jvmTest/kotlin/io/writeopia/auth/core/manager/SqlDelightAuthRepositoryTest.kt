package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private const val APP_DIRECTORY = ".writeopia"
private const val DB_VERSION = 1

class SqlDelightAuthRepositoryTest {

    @Test
    fun `it should be possible to save a user`() = runTest {
        val homeDirectory: String = System.getProperty("user.home")
        val appDirectory = File(homeDirectory, APP_DIRECTORY)

        val dbName = "writeopia_$DB_VERSION.db"
        val dbPath = "$appDirectory${File.separator}$dbName"
        val url = "jdbc:sqlite:$dbPath"

        if (!appDirectory.exists()) {
            appDirectory.mkdirs()
        }

        val databaseStateFlow = DatabaseFactory.createDatabase(
            DriverFactory(),
            url = url,
        )

        val repository = SqlDelightAuthRepository(databaseStateFlow)

        val user = WriteopiaUser(
            id = "someId",
            name = "someName",
            email = "someEmail",
        )

        repository.unselectAllUsers()
        repository.saveUser(user, selected = true)
        val userFromDb = repository.getUser()

        assertEquals(user.id, userFromDb.id)
    }
}
