package io.writeopia.notemenu.viewmodel

import io.writeopia.sdk.models.document.Folder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FolderDestinationTest {

    private fun folder(id: String, parentId: String, deleted: Boolean = false): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = id,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace",
            itemCount = 0,
            deleted = deleted,
        )
    }

    @Test
    fun `the folder and everything inside it are not destinations`() {
        val moving = folder("b", parentId = "a")
        val folders = listOf(
            folder("a", Folder.ROOT_PATH),
            moving,
            folder("b1", parentId = "b"),
            folder("b11", parentId = "b1"),
            folder("c", Folder.ROOT_PATH),
            folder("gone", Folder.ROOT_PATH, deleted = true),
        )

        val destinations = moveDestinations(moving, folders)

        assertEquals(
            listOf(
                FolderDestination(Folder.ROOT_PATH, null, 0),
                FolderDestination("a", "a", 1, isCurrentParent = true),
                FolderDestination("c", "c", 1),
            ),
            destinations
        )
    }

    @Test
    fun `the tree is ordered depth first with the root marked as current parent`() {
        val moving = folder("z", Folder.ROOT_PATH)
        val folders = listOf(
            folder("b", Folder.ROOT_PATH),
            folder("a1", parentId = "a"),
            folder("a", Folder.ROOT_PATH),
            moving,
        )

        assertEquals(
            listOf(
                FolderDestination(Folder.ROOT_PATH, null, 0, isCurrentParent = true),
                FolderDestination("a", "a", 1),
                FolderDestination("a1", "a1", 2),
                FolderDestination("b", "b", 1),
            ),
            moveDestinations(moving, folders)
        )
    }
}
