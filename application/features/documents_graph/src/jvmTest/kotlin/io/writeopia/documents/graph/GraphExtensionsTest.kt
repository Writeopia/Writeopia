package io.writeopia.documents.graph

import io.writeopia.documents.graph.extensions.toGraph
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun documents(count: Int) = mapOf(
    "root" to (0 until count).map { index ->
        ItemData(id = "doc$index", title = "Doc $index", parentId = "root", isFolder = false)
    }
)

class GraphExtensionsTest {

    @Test
    fun `many nodes start spread over the viewport`() {
        val graph = documents(60).toGraph(1000f, 1000f)

        assertEquals(60, graph.nodes.size)
        graph.nodes.forEach { node ->
            assertTrue(node.x in 50f..950f, "x=${node.x} outside the viewport")
            assertTrue(node.y in 50f..950f, "y=${node.y} outside the viewport")
        }

        val xs = graph.nodes.map { it.x }
        val ys = graph.nodes.map { it.y }
        assertTrue(xs.maxOrNull()!! - xs.minOrNull()!! > 800f)
        assertTrue(ys.maxOrNull()!! - ys.minOrNull()!! > 800f)

        val closest = graph.nodes.indices.flatMap { i ->
            (i + 1 until graph.nodes.size).map { j ->
                val dx = graph.nodes[i].x - graph.nodes[j].x
                val dy = graph.nodes[i].y - graph.nodes[j].y
                sqrt(dx * dx + dy * dy)
            }
        }.minOrNull()!!
        assertTrue(closest > 70f, "closest pair $closest")
    }

    @Test
    fun `a single node starts at the centre`() {
        val node = documents(1).toGraph(1000f, 600f).nodes.single()

        assertEquals(500f, node.x, 0.01f)
        assertEquals(300f, node.y, 0.01f)
    }

    @Test
    fun `a wide viewport still holds the spread`() {
        val graph = documents(40).toGraph(1920f, 600f)

        graph.nodes.forEach { node ->
            assertTrue(node.x in 96f..1824f)
            assertTrue(node.y in 30f..570f)
        }
        assertTrue(graph.nodes.maxOf { it.y } - graph.nodes.minOf { it.y } > 480f)
    }

    @Test
    fun `an empty graph has no nodes`() {
        assertTrue(emptyMap<String, List<ItemData>>().toGraph(1000f, 1000f).nodes.isEmpty())
    }
}
