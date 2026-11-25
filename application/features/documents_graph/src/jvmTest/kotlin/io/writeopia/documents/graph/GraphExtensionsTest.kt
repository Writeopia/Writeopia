package io.writeopia.documents.graph

import io.writeopia.documents.graph.extensions.toGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphExtensionsTest {

    @Test
    fun `items under a hub are linked to it`() {
        val graph = mapOf(
            "root" to listOf(ItemData(id = "folder", title = "Folder", parentId = "root", isFolder = true)),
            "folder" to listOf(ItemData(id = "doc", title = "Doc", parentId = "folder", isFolder = false)),
        ).toGraph()

        assertEquals(listOf("folder", "doc"), graph.nodes.map { it.id })
        // The root is only a key, never a node: its links are dropped.
        assertEquals(listOf("folder->doc"), graph.links.map { "${it.source.id}->${it.target.id}" })
    }

    @Test
    fun `an empty graph has no nodes`() {
        assertTrue(emptyMap<String, List<ItemData>>().toGraph().nodes.isEmpty())
    }
}
