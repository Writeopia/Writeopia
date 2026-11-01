@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.databse.HikariCp
import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FolderCreationSyncEventTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-folder-events"
    private val userId = "user-folder-events"
    private val now = 1_792_000_000_000L

    private fun folder(id: String, deleted: Boolean = false) = Folder(
        id = id,
        parentId = "root",
        title = "Folder",
        createdAt = Instant.fromEpochMilliseconds(now),
        lastUpdatedAt = Instant.fromEpochMilliseconds(now),
        workspaceId = workspaceId,
        itemCount = 0,
        deleted = deleted
    )

    private fun events() =
        db.syncEventEntityQueries.selectByWorkspaceAfterTime(workspaceId, 0).executeAsList()

    @AfterTest
    fun tearDown() {
        val documentIds = db.documentEntityQueries.selectAllIdsByWorkspaceId(workspaceId).executeAsList()
        if (documentIds.isNotEmpty()) db.storyStepEntityQueries.deleteByDocumentIds(documentIds)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
        db.folderEntityQueries.deleteByWorkspace(workspaceId)
        // The sync_event table has no delete query: the events of a test must not leak into the next.
        HikariCp.driver()
            .execute(null, "DELETE FROM sync_event WHERE workspace_id = '$workspaceId'", 0, null)
            .value
    }

    @Test
    fun `creating a folder writes one creation event`() = runBlocking {
        val folder = DocumentsService.createFolder("root", "Folder", workspaceId, userId, writeopiaDb = db)

        val event = events().single()
        assertEquals("CREATE_FOLDER", event.event_type)
        assertEquals(folder.id, event.entity_id)
        assertEquals(userId, event.user_id)
    }

    @Test
    fun `an uploaded folder writes a creation event`() = runBlocking {
        DocumentsService.receiveFolders(listOf(folder("folder-1")), userId, db)

        val event = events().single()
        assertEquals("CREATE_FOLDER", event.event_type)
        assertEquals("folder-1", event.entity_id)
    }

    @Test
    fun `a second upload of the same folder writes no creation event`() = runBlocking {
        DocumentsService.receiveFolders(listOf(folder("folder-1")), userId, db)
        DocumentsService.receiveFolders(listOf(folder("folder-1")), userId, db)

        assertEquals(1, events().size)
    }

    @Test
    fun `a deleted folder sent by a client writes no creation event`() = runBlocking {
        DocumentsService.receiveFolders(listOf(folder("folder-2", deleted = true)), userId, db)

        assertTrue(events().none { event -> event.entity_id == "folder-2" })
    }
}
