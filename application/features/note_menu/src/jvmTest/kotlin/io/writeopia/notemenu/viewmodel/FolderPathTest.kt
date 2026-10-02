package io.writeopia.notemenu.viewmodel

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FolderPathTest {

    private fun folder(id: String, parentId: String): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = "Title $id",
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace",
            itemCount = 0,
        )
    }

    private fun document(id: String, parentId: String): Document {
        val now = Clock.System.now()
        return Document(
            id = id,
            title = id,
            content = emptyMap(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            parentId = parentId,
            workspaceId = "workspace",
        )
    }

    @Test
    fun `the path goes from the first folder under the root down to the one shown`() {
        val items: Map<String, List<MenuItem>> = mapOf(
            Folder.ROOT_PATH to listOf(folder("a", Folder.ROOT_PATH), folder("c", Folder.ROOT_PATH)),
            "a" to listOf(folder("b", "a"), document("doc", "a")),
            "b" to listOf(folder("b1", "b")),
        )

        assertEquals(listOf("a", "b", "b1"), items.pathTo("b1").map { it.id })
        assertEquals(listOf("a"), items.pathTo("a").map { it.id })
    }

    @Test
    fun `an unknown folder has no path`() {
        val items: Map<String, List<MenuItem>> = mapOf(
            Folder.ROOT_PATH to listOf(folder("a", Folder.ROOT_PATH)),
        )

        assertEquals(emptyList(), items.pathTo("missing"))
        assertEquals(emptyList(), items.pathTo(Folder.ROOT_PATH))
    }

    @Test
    fun `the path is rebuilt by looking the parents up one by one`() = runTest {
        val byId = listOf(folder("a", Folder.ROOT_PATH), folder("b", "a"), folder("c", "b"))
            .associateBy { it.id }

        val path = pathTo(byId.getValue("c")) { id -> byId[id] }

        assertEquals(listOf("a", "b", "c"), path.map { it.id })
    }

    @Test
    fun `a parent that can't be found or a cycle ends the path`() = runTest {
        val orphan = folder("b", "missing")
        assertEquals(listOf("b"), pathTo(orphan) { null }.map { it.id })

        val loop = listOf(folder("x", "y"), folder("y", "x")).associateBy { it.id }
        assertEquals(listOf("y", "x"), pathTo(loop.getValue("x")) { id -> loop[id] }.map { it.id })
    }
}
