package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightAuthRepositoryTest {

    @Test
    fun `it should be possible to save a user`() = runTest {
        val database = DatabaseFactory.createDatabase(DriverFactory())

        val repository = SqlDelightAuthRepository(database)

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
