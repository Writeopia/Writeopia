package io.writeopia.auth.core.manager

import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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

    @Test
    fun `cached workspaces keep the sync state and selection of the known ones`() = runTest {
        val repository = SqlDelightAuthRepository(DatabaseFactory.createDatabase(DriverFactory()))

        val selected = workspace("a", "Alpha").copy(
            lastSync = Instant.fromEpochMilliseconds(5_000),
            lastEventSync = 7,
            selected = true,
        )
        repository.saveWorkspace(selected)
        repository.saveWorkspace(workspace("gone", "Gone"))

        repository.cacheWorkspaces(
            "user",
            listOf(workspace("a", "Alpha renamed"), workspace("b", "beta"))
        )

        val current = repository.getWorkspace()!!
        assertEquals("a", current.id)
        assertEquals("Alpha renamed", current.name)
        assertEquals(7, current.lastEventSync)
        assertEquals(5_000, current.lastSync.toEpochMilliseconds())

        val cached = repository.getCachedWorkspaces("user")
        assertEquals(listOf("a", "b"), cached.map { it.id })
        assertTrue(cached.all { it.documentCount == Workspace.UNKNOWN_DOCUMENT_COUNT })
        assertTrue(repository.getCachedWorkspaces("other").isEmpty())
    }

    @Test
    fun `cached workspaces never include the offline workspace and the selected one is never dropped`() =
        runTest {
            val repository =
                SqlDelightAuthRepository(DatabaseFactory.createDatabase(DriverFactory()))

            repository.saveWorkspace(Workspace.disconnectedWorkspace().copy(userId = "user"))
            repository.saveWorkspace(workspace("sel", "Selected").copy(selected = true))

            repository.cacheWorkspaces("user", listOf(workspace("b", "B")))

            assertEquals(listOf("b", "sel"), repository.getCachedWorkspaces("user").map { it.id })
        }

    private fun workspace(id: String, name: String) = Workspace(
        id = id,
        userId = "user",
        name = name,
        lastSync = Instant.DISTANT_PAST,
        selected = false,
        role = "",
    )
}
