@file:OptIn(ExperimentalTime::class)

package io.writeopia.account.viewmodel

import io.writeopia.core.folders.repository.tag.InMemoryDocumentTagRepository
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class WorkspaceTagsControllerTest {

    private val repository = InMemoryDocumentTagRepository()
    private val sent = mutableListOf<DocumentTag>()
    private var online = true

    private fun TestScope.controller() = WorkspaceTagsController(
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        tagRepository = repository,
        workspaceId = { "ws" },
        sendTags = { tags, _ ->
            if (online) {
                sent.addAll(tags)
                ResultData.Complete(Unit)
            } else {
                ResultData.Error()
            }
        },
    )

    private fun tag(id: String, name: String, synced: Boolean = true) = DocumentTag(
        id = id,
        workspaceId = "ws",
        name = name,
        description = "about $name",
        createdAt = Instant.fromEpochMilliseconds(1_000),
        lastUpdatedAt = Instant.fromEpochMilliseconds(1_000),
        lastSyncedAt = if (synced) Instant.fromEpochMilliseconds(1_000) else null,
    )

    @Test
    fun `a new tag is stored, sent and marked synced`() = runTest {
        val controller = controller()

        controller.createTag("  Research ", " Papers to read ")

        val tags = controller.tags.first()
        assertEquals(listOf("Research"), tags.map { it.name })
        assertEquals("Papers to read", tags.single().description)
        assertEquals(listOf("Research"), sent.map { it.name })
        assertFalse(tags.single().isOutdated)
    }

    @Test
    fun `an edited tag keeps its id and is sent again`() = runTest {
        repository.upsertTag(tag("t1", "Ideas"))
        val controller = controller()

        controller.editTag("t1", "Big ideas", "The ones worth a document")

        val edited = assertNotNull(repository.getTagById("t1"))
        assertEquals("Big ideas", edited.name)
        assertEquals("The ones worth a document", edited.description)
        assertEquals(listOf("t1"), sent.map { it.id })
        assertFalse(edited.isOutdated)
    }

    @Test
    fun `an edit that changes nothing is not sent`() = runTest {
        repository.upsertTag(tag("t1", "Ideas"))
        val controller = controller()

        controller.editTag("t1", "Ideas", "about Ideas")

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a deleted tag becomes a tombstone that syncs`() = runTest {
        repository.upsertTag(tag("t1", "Ideas"))
        repository.upsertTag(tag("t2", "Work"))
        val controller = controller()

        controller.deleteTag("t1")

        assertEquals(listOf("Work"), controller.tags.first().map { it.name })
        val tombstone = assertNotNull(repository.getTagById("t1"))
        assertTrue(tombstone.deleted)
        assertEquals(listOf("t1"), sent.map { it.id })
        assertTrue(sent.single().deleted)
        assertFalse(tombstone.isOutdated)
    }

    @Test
    fun `offline changes stay outdated for the next sync`() = runTest {
        online = false
        repository.upsertTag(tag("t1", "Ideas"))
        val controller = controller()

        controller.editTag("t1", "Thoughts", "")
        controller.deleteTag("t1")

        val outdated = repository.localOutdatedTags("ws")
        assertEquals(listOf("t1"), outdated.map { it.id })
        assertTrue(outdated.single().deleted)
    }

    @Test
    fun `a blank name creates nothing`() = runTest {
        val controller = controller()

        controller.createTag("   ", "nothing")

        assertTrue(controller.tags.first().isEmpty())
        assertNull(sent.firstOrNull())
    }

    @Test
    fun `names are taken ignoring case, except by the tag being edited`() {
        val tags = listOf(tag("t1", "Ideas"), tag("t2", "Work"))

        assertTrue(WorkspaceTagsController.isNameTaken(tags, " ideas "))
        assertFalse(WorkspaceTagsController.isNameTaken(tags, "Ideas", exceptId = "t1"))
        assertFalse(WorkspaceTagsController.isNameTaken(tags, "Plans"))
        assertFalse(WorkspaceTagsController.isNameTaken(tags, "  "))
    }

    @Test
    fun `a creation queued behind another with the same name creates nothing`() = runTest {
        val controller = controller()

        controller.createTag("Ideas", "")
        controller.createTag(" ideas ", "")

        assertEquals(listOf("Ideas"), controller.tags.first().map { it.name })
        assertEquals(1, sent.size)
    }

    @Test
    fun `an edit to the name of another tag is refused`() = runTest {
        repository.upsertTag(tag("t1", "Ideas"))
        repository.upsertTag(tag("t2", "Work"))
        val controller = controller()

        controller.editTag("t2", "IDEAS", "")

        assertEquals("Work", repository.getTagById("t2")?.name)
        assertTrue(sent.isEmpty())
    }
}
