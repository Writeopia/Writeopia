package io.writeopia.documents.graph

import io.writeopia.documents.graph.layout.ForceLayout
import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceLayoutTest {

    private fun node(id: String, hub: Boolean) =
        Node(id = id, label = id, initialX = 0F, initialY = 0F, isFolder = hub, selected = false)

    /**
     * Folders of different sizes, one of them nested, and documents at the root. Like the graph of
     * the app, the root is not a node: the top folders and documents are separate pieces.
     */
    private fun workspace(folderSizes: List<Int> = listOf(3, 8, 15, 25, 5), rootDocuments: Int = 6): ForceLayout {
        val nodes = mutableListOf<Node>()
        val links = mutableListOf<Link>()
        repeat(rootDocuments) { index -> nodes += node("root-doc$index", hub = false) }
        folderSizes.forEachIndexed { folder, size ->
            val hub = node("folder$folder", hub = true).also(nodes::add)
            repeat(size) { index ->
                links += Link(hub, node("folder$folder-doc$index", hub = false).also(nodes::add))
            }
        }
        val parent = nodes.first { it.id == "folder1" }
        val nested = node("nested", hub = true).also(nodes::add)
        links += Link(parent, nested)
        repeat(4) { index -> links += Link(nested, node("nested-doc$index", hub = false).also(nodes::add)) }

        return ForceLayout(nodes, links)
    }

    private fun distance(a: Node, b: Node): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun ForceLayout.settle(): Int {
        var ticks = 0
        while (!isSettled && ticks < 2000) {
            tick(CENTER, CENTER)
            ticks++
        }
        return ticks
    }

    private fun ForceLayout.leavesOf(hub: Node): List<Node> =
        links.filter { it.source == hub && !it.target.isFolder }.map { it.target }

    private fun ForceLayout.report(label: String) {
        val hubs = nodes.filter { it.isFolder }
        val radius = hubs.associateWith { hub -> leavesOf(hub).maxOfOrNull { distance(it, hub) } ?: 0F }
        val meanLeaf = hubs.flatMap { hub -> leavesOf(hub).map { distance(it, hub) } }.average()
        val separation = hubs.flatMapIndexed { i, a ->
            hubs.drop(i + 1).map { b -> distance(a, b) / (radius.getValue(a) + radius.getValue(b)).coerceAtLeast(1F) }
        }
        val extent = nodes.maxOf { node ->
            val dx = node.x - CENTER
            val dy = node.y - CENTER
            sqrt(dx * dx + dy * dy)
        }
        println(
            "$label: extent ${"%.0f".format(
                extent
            )}, mean leaf-hub ${"%.1f".format(meanLeaf)}, min hub separation ${"%.2f".format(separation.min())}"
        )
    }

    @Test
    fun `documents stay close to their hub and clusters stay apart`() {
        val layout = workspace()
        layout.placeRadially(CENTER, CENTER)
        layout.warmUp(CENTER, CENTER)
        layout.settle()
        layout.report("settled")

        val hubs = layout.nodes.filter { it.isFolder }
        hubs.forEach { hub ->
            layout.leavesOf(hub).forEach { leaf ->
                assertTrue(distance(leaf, hub) < 110F, "${leaf.id} is ${distance(leaf, hub)} from ${hub.id}")
            }
        }

        // Every document is nearer its own hub than any other hub.
        hubs.forEach { hub ->
            layout.leavesOf(hub).forEach { leaf ->
                val nearest = hubs.minBy { distance(it, leaf) }
                assertTrue(nearest == hub, "${leaf.id} is nearer ${nearest.id} than ${hub.id}")
            }
        }
    }

    @Test
    fun `the first frame is close to the last`() {
        val layout = workspace()
        layout.placeRadially(CENTER, CENTER)
        layout.report("placed")
        layout.warmUp(CENTER, CENTER)
        layout.report("first frame")

        val first = layout.nodes.associateWith { it.x to it.y }
        val ticks = layout.settle()
        layout.report("last frame after $ticks ticks")

        val moves = layout.nodes.map { node ->
            val (x, y) = first.getValue(node)
            val dx = node.x - x
            val dy = node.y - y
            sqrt(dx * dx + dy * dy)
        }
        println("move after the first frame: mean ${"%.1f".format(moves.average())}, max ${"%.1f".format(moves.max())}")

        assertTrue(moves.average() < 15, "mean move ${moves.average()}")
        assertTrue(moves.max() < 40, "max move ${moves.max()}")
    }

    @Test
    fun `a dragged node heats the layout again`() {
        val layout = workspace()
        layout.placeRadially(CENTER, CENTER)
        layout.warmUp(CENTER, CENTER)
        layout.settle()
        assertTrue(layout.isSettled)

        val dragged = layout.nodes.first { it.id == "folder2" }
        dragged.isDragged = true
        dragged.x += 200F
        layout.tick(CENTER, CENTER)
        assertTrue(!layout.isSettled)

        dragged.isDragged = false
        layout.settle()
        assertTrue(layout.isSettled)
    }

    @Test
    fun `nodes start apart from each other`() {
        val layout = workspace()
        layout.placeRadially(CENTER, CENTER)

        val closest = layout.nodes.indices.minOf { i ->
            (i + 1 until layout.nodes.size).minOfOrNull { j -> distance(layout.nodes[i], layout.nodes[j]) }
                ?: Float.MAX_VALUE
        }
        assertTrue(closest > 5F, "closest pair $closest")
    }

    @Test
    fun `a single node rests at the centre`() {
        val layout = ForceLayout(listOf(node("doc", hub = false)), emptyList())
        layout.placeRadially(CENTER, CENTER)
        layout.warmUp(CENTER, CENTER)

        assertEquals(CENTER, layout.nodes.single().x, 0.01F)
        assertEquals(CENTER, layout.nodes.single().y, 0.01F)
    }

    @Test
    fun `an empty layout does nothing`() {
        val layout = ForceLayout(emptyList(), emptyList())
        layout.placeRadially(CENTER, CENTER)
        layout.warmUp(CENTER, CENTER)
        layout.tick(CENTER, CENTER)
        assertTrue(layout.nodes.isEmpty())
    }

    private companion object {
        const val CENTER = 500F
    }
}
