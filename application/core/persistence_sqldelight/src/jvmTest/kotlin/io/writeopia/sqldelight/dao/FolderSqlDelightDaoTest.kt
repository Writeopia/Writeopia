@file:OptIn(ExperimentalTime::class)

package io.writeopia.sqldelight.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sqldelight.database.DatabaseFactory.createDatabase
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.app.sql.FolderEntity
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
    fun `each folder keeps its own contents watermark per workspace, the root included`() = runTest {
        val dao = FolderSqlDelightDao(createDatabase(DriverFactory(), JdbcSqliteDriver.IN_MEMORY))

        assertNull(dao.getContentSyncedAt("root", workspaceId))

        dao.setContentSyncedAt("root", workspaceId, 100L)
        dao.setContentSyncedAt("folder", workspaceId, 200L)
        dao.setContentSyncedAt("root", "otherWs", 300L)
        dao.setContentSyncedAt("root", workspaceId, 400L)

        assertEquals(400L, dao.getContentSyncedAt("root", workspaceId))
        assertEquals(200L, dao.getContentSyncedAt("folder", workspaceId))
        assertEquals(300L, dao.getContentSyncedAt("root", "otherWs"))
        assertNull(dao.getContentSyncedAt("folder", "otherWs"))
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
        deleted = if (deleted) 1L else 0L
    )
}
