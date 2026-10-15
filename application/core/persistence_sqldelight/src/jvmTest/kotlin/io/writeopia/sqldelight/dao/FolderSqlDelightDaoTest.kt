@file:OptIn(ExperimentalTime::class)

package io.writeopia.sqldelight.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sqldelight.database.DatabaseFactory.createDatabase
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.app.sql.FolderEntity
import io.writeopia.sqldelight.extensions.toModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

class FolderSqlDelightDaoTest {

    private val workspaceId = "ws"

    @Test
    fun `only folders changed since their last sync count as outdated`() = runTest {
        val dao = FolderSqlDelightDao(createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY))
        val now = Clock.System.now()

        val neverSynced = Folder.fromName("never synced", workspaceId).copy(id = "never", lastSyncedAt = null)
        val changedAfterSync = Folder.fromName("changed", workspaceId).copy(
            id = "changed",
            lastUpdatedAt = now,
            lastSyncedAt = now - 1.hours
        )
        val upToDate = Folder.fromName("up to date", workspaceId).copy(
            id = "upToDate",
            lastUpdatedAt = now - 1.hours,
            lastSyncedAt = now
        )
        val deletedLocally = Folder.fromName("deleted", workspaceId).copy(id = "deleted", deleted = true)
        val otherWorkspace = Folder.fromName("other", "otherWs").copy(id = "other", lastSyncedAt = null)

        listOf(neverSynced, changedAfterSync, upToDate, deletedLocally, otherWorkspace).forEach { folder ->
            dao.createFolder(folder.toEntity())
        }

        val outdated = dao.selectOutdatedByWorkspaceId(workspaceId).map { it.id }.sorted()

        assertEquals(listOf("changed", "never"), outdated)
    }

    @Test
    fun `the time a folder's contents were synced is stored apart from its own sync time`() = runTest {
        val dao = FolderSqlDelightDao(createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY))
        val now = Clock.System.now()
        val folder = Folder.fromName("opened", workspaceId).copy(
            id = "opened",
            lastSyncedAt = now - 1.hours,
            contentSyncedAt = null
        )

        dao.createFolder(folder.toEntity())
        assertNull(dao.getFolderById("opened")?.toModel(0)?.contentSyncedAt)

        dao.updateFolder(folder.copy(contentSyncedAt = now).toEntity())
        val stored = dao.getFolderById("opened")?.toModel(0)

        assertEquals(now.toEpochMilliseconds(), stored?.contentSyncedAt?.toEpochMilliseconds())
        assertEquals((now - 1.hours).toEpochMilliseconds(), stored?.lastSyncedAt?.toEpochMilliseconds())
    }

    private fun Folder.toEntity() = FolderEntity(
        id = id,
        parent_id = parentId,
        workspace_id = workspaceId,
        title = title,
        created_at = createdAt.toEpochMilliseconds(),
        last_updated_at = lastUpdatedAt.toEpochMilliseconds(),
        last_synced_at = lastSyncedAt?.toEpochMilliseconds(),
        favorite = 0L,
        icon = null,
        icon_tint = null,
        deleted = if (deleted) 1L else 0L,
        content_synced_at = contentSyncedAt?.toEpochMilliseconds()
    )
}
