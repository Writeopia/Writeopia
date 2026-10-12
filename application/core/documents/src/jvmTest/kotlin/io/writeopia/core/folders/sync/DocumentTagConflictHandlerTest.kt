@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.core.folders.repository.tag.InMemoryDocumentTagRepository
import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentTagConflictHandlerTest {

    private val workspaceId = "workspace-1"

    private fun tag(id: String, name: String, updatedAt: Long, deleted: Boolean = false, syncedAt: Long? = null) =
        DocumentTag(
            id = id,
            workspaceId = workspaceId,
            name = name,
            description = "About $name",
            createdAt = Instant.fromEpochMilliseconds(0),
            lastUpdatedAt = Instant.fromEpochMilliseconds(updatedAt),
            lastSyncedAt = syncedAt?.let(Instant::fromEpochMilliseconds),
            deleted = deleted,
        )

    @Test
    fun `new backend tags are stored and local-only tags are sent`() = runBlocking {
        val repository = InMemoryDocumentTagRepository()
        val localOnly = tag("local", "Local", updatedAt = 5)
        repository.upsertTag(localOnly)
        val handler = DocumentTagConflictHandler(repository)

        val toSend = handler.handleConflict(
            localTags = listOf(localOnly),
            externalTags = listOf(tag("remote", "Remote", updatedAt = 3)),
        )

        assertEquals(listOf("local"), toSend.map { it.id })
        assertEquals("Remote", repository.getTagById("remote")?.name)
        assertEquals(Instant.fromEpochMilliseconds(3), repository.getTagById("remote")?.lastSyncedAt)
    }

    @Test
    fun `the copy updated last wins`() = runBlocking {
        val repository = InMemoryDocumentTagRepository()
        val staleLocal = tag("a", "Old name", updatedAt = 1)
        val freshLocal = tag("b", "Renamed locally", updatedAt = 9)
        repository.upsertTag(staleLocal)
        repository.upsertTag(freshLocal)
        val handler = DocumentTagConflictHandler(repository)

        val toSend = handler.handleConflict(
            localTags = listOf(staleLocal, freshLocal),
            externalTags = listOf(tag("a", "New name", updatedAt = 2), tag("b", "Renamed remotely", updatedAt = 2)),
        )

        assertEquals(listOf("b"), toSend.map { it.id })
        assertEquals("New name", repository.getTagById("a")?.name)
        assertEquals("Renamed locally", repository.getTagById("b")?.name)
    }

    @Test
    fun `a tag deleted locally is not brought back and its tombstone is sent`() = runBlocking {
        val repository = InMemoryDocumentTagRepository()
        val deletedLocally = tag("gone", "Gone", updatedAt = 10, deleted = true)
        repository.upsertTag(deletedLocally)
        val handler = DocumentTagConflictHandler(repository)

        val toSend = handler.handleConflict(
            localTags = listOf(deletedLocally),
            externalTags = listOf(tag("gone", "Gone", updatedAt = 4)),
        )

        assertEquals(listOf("gone"), toSend.map { it.id })
        assertTrue(repository.getTagById("gone")?.deleted == true)
        assertNull(repository.getTagsForWorkspace(workspaceId).firstOrNull { it.id == "gone" })
    }

    @Test
    fun `an up-to-date local tag is neither sent nor overwritten`() = runBlocking {
        val repository = InMemoryDocumentTagRepository()
        val synced = tag("a", "Same", updatedAt = 5, syncedAt = 5)
        repository.upsertTag(synced)
        val handler = DocumentTagConflictHandler(repository)

        val toSend = handler.handleConflict(
            localTags = emptyList(),
            externalTags = listOf(tag("a", "Same", updatedAt = 5)),
        )

        assertTrue(toSend.isEmpty())
        assertEquals(Instant.fromEpochMilliseconds(5), repository.getTagById("a")?.lastSyncedAt)
    }
}
