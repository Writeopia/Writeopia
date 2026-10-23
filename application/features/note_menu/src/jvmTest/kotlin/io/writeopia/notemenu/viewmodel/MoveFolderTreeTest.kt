package io.writeopia.notemenu.viewmodel

import io.writeopia.commonui.folders.FolderSearchResult
import io.writeopia.commonui.folders.MoveFolderTree
import io.writeopia.sdk.models.document.Folder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MoveFolderTreeTest {

    private fun folder(id: String, parentId: String, title: String = id, deleted: Boolean = false): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace",
            itemCount = 0,
            deleted = deleted,
        )
    }

    private val folders = listOf(
        folder("work", Folder.ROOT_PATH, title = "Work"),
        folder("reports", parentId = "work", title = "Reports"),
        folder("old", parentId = "reports", title = "Old reports"),
        folder("art", Folder.ROOT_PATH, title = "art"),
        folder("gone", Folder.ROOT_PATH, title = "Gone reports", deleted = true),
    )

    @Test
    fun `children are sorted by title ignoring case`() {
        val tree = MoveFolderTree(folders, excludedIds = emptySet(), rootTitle = "Home")

        assertEquals(listOf("art", "work"), tree.childrenOf(Folder.ROOT_PATH).map { it.id })
        assertEquals(listOf("reports"), tree.childrenOf("work").map { it.id })
    }

    @Test
    fun `search matches the title anywhere and tells where the folder is`() {
        val tree = MoveFolderTree(folders, excludedIds = emptySet(), rootTitle = "Home")

        assertEquals(
            listOf(
                FolderSearchResult(folders[1], "Home / Work"),
                FolderSearchResult(folders[2], "Home / Work / Reports"),
            ),
            tree.search("REPORT")
        )
    }

    @Test
    fun `a blank search lists every folder`() {
        val tree = MoveFolderTree(folders, excludedIds = emptySet(), rootTitle = "Home")

        assertEquals(listOf("art", "work", "reports", "old"), tree.search("  ").map { it.folder.id })
    }

    @Test
    fun `the selected folders and what is inside them can't be picked`() {
        val tree = MoveFolderTree(folders, excludedIds = setOf("reports"), rootTitle = "Home")

        assertEquals(emptyList(), tree.childrenOf("work"))
        assertEquals(emptyList(), tree.search("old"))
        assertEquals(listOf("art", "work"), tree.search("").map { it.folder.id })
    }
}
