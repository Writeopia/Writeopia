package io.writeopia.features.search.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchItemsMergeTest {

    @Test
    fun `the items come in order, each id once`() {
        val first = listOf(SearchItem.FolderInfo("f", "Folder"), SearchItem.DocumentInfo("a", "A"))
        val second = listOf(SearchItem.DocumentInfo("a", "A"), SearchItem.DocumentInfo("b", "B"))

        assertEquals(
            listOf(SearchItem.FolderInfo("f", "Folder"), SearchItem.DocumentInfo("a", "A"), SearchItem.DocumentInfo("b", "B")),
            mergeSearchItems(first, second)
        )
    }

    @Test
    fun `the copy with a snippet wins, whichever list it came from`() {
        val local = listOf(SearchItem.DocumentInfo("a", "A"), SearchItem.DocumentInfo("b", "B", snippet = "local b"))
        val remote = listOf(SearchItem.DocumentInfo("a", "A", snippet = "remote a"), SearchItem.DocumentInfo("b", "B"))

        assertEquals(
            listOf(SearchItem.DocumentInfo("a", "A", snippet = "remote a"), SearchItem.DocumentInfo("b", "B", snippet = "local b")),
            mergeSearchItems(local, remote)
        )
    }

    @Test
    fun `a presentation keeps its place and is never replaced`() {
        val presentation = SearchItem.PresentationInfo("p", "Slides", documentId = "a")
        val local = listOf(presentation, SearchItem.DocumentInfo("a", "A"))

        assertEquals(local, mergeSearchItems(local, listOf(presentation)))
    }
}
