@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

class FolderConflictHandlerTest {

    private val workspaceId = "testWorkspace"

    @Test
    fun `a folder received from the backend is stored as already synced`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        coEvery { folderRepository.getFolderById(any()) } returns null
        val handler = FolderConflictHandler(folderRepository)

        val external = Folder.fromName("From backend", workspaceId).copy(id = "external")

        val toSend = handler.handleConflict(localFolders = emptyList(), externalFolders = listOf(external))

        val created = slot<Folder>()
        coVerify { folderRepository.createFolder(capture(created)) }
        assertEquals("external", created.captured.id)
        val lastSyncedAt = assertNotNull(created.captured.lastSyncedAt)
        assertTrue(lastSyncedAt >= created.captured.lastUpdatedAt)
        assertTrue(toSend.isEmpty())
    }

    @Test
    fun `when the backend copy wins it is stored as already synced`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val now = Clock.System.now()
        val local = Folder.fromName("Old title", workspaceId).copy(
            id = "shared",
            lastUpdatedAt = now - 2.hours,
            lastSyncedAt = null
        )
        val external = local.copy(title = "New title", lastUpdatedAt = now - 1.hours, lastSyncedAt = null)
        coEvery { folderRepository.getFolderById("shared") } returns local
        val handler = FolderConflictHandler(folderRepository)

        val toSend = handler.handleConflict(localFolders = listOf(local), externalFolders = listOf(external))

        val updated = slot<Folder>()
        coVerify { folderRepository.updateFolder(capture(updated)) }
        assertEquals("New title", updated.captured.title)
        assertNotNull(updated.captured.lastSyncedAt)
        assertTrue(toSend.isEmpty())
    }

    @Test
    fun `when the local copy wins it is sent and kept untouched`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val now = Clock.System.now()
        val local = Folder.fromName("Local title", workspaceId).copy(
            id = "shared",
            lastUpdatedAt = now,
            lastSyncedAt = null
        )
        val external = local.copy(title = "Stale title", lastUpdatedAt = now - 1.hours)
        coEvery { folderRepository.getFolderById("shared") } returns local
        val handler = FolderConflictHandler(folderRepository)

        val toSend = handler.handleConflict(localFolders = listOf(local), externalFolders = listOf(external))

        assertEquals(listOf(local), toSend)
        coVerify { folderRepository.updateFolder(local) }
    }

    @Test
    fun `a folder soft deleted locally is not recreated from backend data`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val external = Folder.fromName("Deleted here", workspaceId).copy(id = "gone")
        coEvery { folderRepository.getFolderById("gone") } returns external.copy(deleted = true)
        val handler = FolderConflictHandler(folderRepository)

        handler.handleConflict(localFolders = emptyList(), externalFolders = listOf(external))

        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
        coVerify(exactly = 0) { folderRepository.updateFolder(any()) }
    }

    @Test
    fun `local only folders are sent to the backend`() = runBlocking {
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val localOnly = Folder.fromName("Only here", workspaceId).copy(id = "localOnly")
        val handler = FolderConflictHandler(folderRepository)

        val toSend = handler.handleConflict(localFolders = listOf(localOnly), externalFolders = emptyList())

        assertEquals(listOf(localOnly), toSend)
    }
}
