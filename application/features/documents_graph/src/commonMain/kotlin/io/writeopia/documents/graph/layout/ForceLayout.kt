package io.writeopia.documents.graph.layout

import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The force layout of the documents graph, after d3-force: links pull to a distance, nodes push
 * each other away, the graph is kept centred, and a cooling [alpha] scales every force so the
 * layout comes to rest instead of shaking forever.
 *
 * The hubs (root, folders, tags) are what make it read as clusters: a hub's documents hang close
 * to it ([LEAF_DISTANCE]), while the link between two hubs is long enough for both clusters to fit
 * side by side ([clusterRadius]). The nodes start where the layout will roughly put them
 * ([placeRadially]) and most of the cooling runs before the first frame ([warmUp]), so the first
 * frame already looks like the last one.
 *
 * The distances are in dp, the nodes are in pixels: everything is scaled by [density].
 */
internal class ForceLayout(
    val nodes: List<Node>,
    val links: List<Link>,
    private val density: Float = 1F,
) {
    var alpha: Float = 1F
        private set

    private val indexOf: Map<Node, Int> = nodes.withIndex().associate { (index, node) -> node to index }
    private val sourceIndex = IntArray(links.size) { indexOf.getValue(links[it].source) }
    private val targetIndex = IntArray(links.size) { indexOf.getValue(links[it].target) }

    private val degree = IntArray(nodes.size).also { degree ->
        links.indices.forEach { i ->
            degree[sourceIndex[i]]++
            degree[targetIndex[i]]++
        }
    }

    /** How many documents (not hubs) hang directly from each node. */
    private val leafCount = IntArray(nodes.size).also { count ->
        links.indices.forEach { i -> if (!nodes[targetIndex[i]].isFolder) count[sourceIndex[i]]++ }
    }

    private val linkDistance = FloatArray(links.size) { i ->
        val source = sourceIndex[i]
        val target = targetIndex[i]
        if (nodes[source].isFolder && nodes[target].isFolder) {
            clusterRadius(source) + clusterRadius(target) + HUB_GAP
        } else {
            LEAF_DISTANCE
        }
    }

    // Like d3: a link pulls less on a node with many links, so a hub is not dragged around by
    // its documents, and the node with fewer links moves more.
    private val linkStrength = FloatArray(links.size) { i ->
        1F / min(degree[sourceIndex[i]], degree[targetIndex[i]]).coerceAtLeast(1)
    }
    private val linkBias = FloatArray(links.size) { i ->
        val source = degree[sourceIndex[i]].toFloat()
        source / (source + degree[targetIndex[i]])
    }

    /**
     * The nodes nothing links to: the top folders or tags and the documents at the root. The root
     * itself is never a node, so these are the separate pieces of the graph.
     */
    private val isTop = BooleanArray(nodes.size) { true }.also { top ->
        targetIndex.forEach { top[it] = false }
    }

    private val charge = FloatArray(nodes.size) { i ->
        if (nodes[i].isFolder) HUB_CHARGE else LEAF_CHARGE
    }

    private val alphaDecay = 1F - ALPHA_MIN.toDouble().pow(1.0 / COOLING_TICKS).toFloat()

    private var alphaTarget = 0F

    /** Whether the layout has come to rest; nothing moves until a node is dragged. */
    val isSettled: Boolean get() = alpha < ALPHA_MIN && alphaTarget == 0F

    /** The radius of the ring of documents around a node, in dp. */
    fun clusterRadius(index: Int): Float =
        if (leafCount[index] == 0) HUB_RADIUS else LEAF_DISTANCE + LEAF_SPREAD * sqrt(leafCount[index].toFloat())

    /**
     * Runs the first ticks at once, before anything is drawn, so the animation that follows is
     * only the end of the cooling. Bounded by [budget] pair visits, which keeps it short on
     * large graphs.
     */
    fun warmUp(centerX: Float, centerY: Float, budget: Long = WARM_UP_BUDGET) {
        val pairs = nodes.size.toLong() * nodes.size
        val ticks = if (pairs == 0L) 0 else min(WARM_UP_TICKS.toLong(), budget / pairs).toInt()
        repeat(ticks) { tick(centerX, centerY) }
    }

    /** One step of the simulation. A dragged node heats it again, like d3's alphaTarget. */
    fun tick(centerX: Float, centerY: Float) {
        alphaTarget = if (nodes.any { it.isDragged }) DRAG_ALPHA else 0F
        if (isSettled) return

        alpha += (alphaTarget - alpha) * alphaDecay

        applyLinks()
        applyCharge()
        applyCollision()
        applyGravity(centerX, centerY)

        for (node in nodes) {
            if (node.isDragged) {
                node.vx = 0F
                node.vy = 0F
                continue
            }
            node.vx *= 1F - VELOCITY_DECAY
            node.vy *= 1F - VELOCITY_DECAY
            node.x += node.vx
            node.y += node.vy
        }

        applyCentre(centerX, centerY)
    }

    private fun applyLinks() {
        for (i in links.indices) {
            val source = nodes[sourceIndex[i]]
            val target = nodes[targetIndex[i]]
            var dx = target.x + target.vx - source.x - source.vx
            var dy = target.y + target.vy - source.y - source.vy
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(MIN_DISTANCE)
            val pull = (distance - linkDistance[i] * density) / distance * alpha * linkStrength[i]
            dx *= pull
            dy *= pull

            val bias = linkBias[i]
            target.vx -= dx * bias
            target.vy -= dy * bias
            source.vx += dx * (1 - bias)
            source.vy += dy * (1 - bias)
        }
    }

    /** Falls with the distance, not its square: it reaches far enough to keep clusters apart. */
    private fun applyCharge() {
        for (i in nodes.indices) {
            val a = nodes[i]
            for (j in i + 1 until nodes.size) {
                val b = nodes[j]
                var dx = b.x - a.x
                var dy = b.y - a.y
                if (dx == 0F && dy == 0F) {
                    dx = jiggle(i, j)
                    dy = jiggle(j, i)
                }
                val distanceSq = max(dx * dx + dy * dy, MIN_DISTANCE * MIN_DISTANCE)
                val scale = alpha * density / distanceSq

                // Each node is pushed by the charge of the other.
                a.vx += dx * charge[j] * scale
                a.vy += dy * charge[j] * scale
                b.vx -= dx * charge[i] * scale
                b.vy -= dy * charge[i] * scale
            }
        }
    }

    /** Keeps the nodes, and the names drawn under them, from sitting on top of each other. */
    private fun applyCollision() {
        val minDistance = COLLISION_DISTANCE * density

        for (i in nodes.indices) {
            val a = nodes[i]
            for (j in i + 1 until nodes.size) {
                val b = nodes[j]
                val dx = b.x + b.vx - a.x - a.vx
                val dy = b.y + b.vy - a.y - a.vy
                val distanceSq = dx * dx + dy * dy
                if (distanceSq >= minDistance * minDistance) continue

                val distance = sqrt(distanceSq).coerceAtLeast(MIN_DISTANCE)
                val push = (minDistance - distance) / distance * COLLISION_STRENGTH / 2
                a.vx -= dx * push
                a.vy -= dy * push
                b.vx += dx * push
                b.vy += dy * push
            }
        }
    }

    /**
     * Pulls only the top nodes to the centre: the pieces of the graph stay together, while the
     * documents follow their hub through its links instead of being squeezed in themselves.
     */
    private fun applyGravity(centerX: Float, centerY: Float) {
        for (i in nodes.indices) {
            if (!isTop[i]) continue
            val node = nodes[i]
            node.vx += (centerX - node.x) * GRAVITY * alpha
            node.vy += (centerY - node.y) * GRAVITY * alpha
        }
    }

    /** Moves the whole graph so its mean sits at the centre; unlike a pull, it squeezes nothing. */
    private fun applyCentre(centerX: Float, centerY: Float) {
        if (nodes.isEmpty() || nodes.any { it.isDragged }) return

        val shiftX = centerX - nodes.sumOf { it.x.toDouble() }.toFloat() / nodes.size
        val shiftY = centerY - nodes.sumOf { it.y.toDouble() }.toFloat() / nodes.size
        for (node in nodes) {
            node.x += shiftX
            node.y += shiftY
        }
    }

    /**
     * Puts the nodes near where the forces will settle them: the documents at the root in the
     * centre, the top hubs on a ring around them, each hub's own hubs out from it within a wedge
     * sized by how many nodes hang under them, and the documents of a hub on a sunflower around
     * it. Nodes only reached through a cycle go on an outer ring.
     */
    fun placeRadially(centerX: Float, centerY: Float) {
        if (nodes.isEmpty()) return

        val children = Array(nodes.size) { mutableListOf<Int>() }
        links.indices.forEach { i -> children[sourceIndex[i]].add(targetIndex[i]) }

        val placed = BooleanArray(nodes.size)
        val weight = IntArray(nodes.size)

        fun weigh(index: Int, visiting: BooleanArray): Int {
            if (visiting[index]) return 0
            visiting[index] = true
            weight[index] = 1 + children[index].sumOf { weigh(it, visiting) }
            return weight[index]
        }

        fun place(index: Int, x: Float, y: Float, wedgeStart: Float, wedgeSize: Float) {
            placed[index] = true
            nodes[index].x = x
            nodes[index].y = y

            val leaves = children[index].filter { !nodes[it].isFolder && !placed[it] }
            leaves.forEachIndexed { k, leaf ->
                val radius = (LEAF_DISTANCE + LEAF_SPREAD * sqrt(k + 0.5F)) * density
                val angle = k * GOLDEN_ANGLE
                placed[leaf] = true
                nodes[leaf].x = x + cos(angle) * radius
                nodes[leaf].y = y + sin(angle) * radius
            }

            val hubs = children[index].filter { nodes[it].isFolder && !placed[it] }
            val total = hubs.sumOf { weight[it] }.coerceAtLeast(1)
            var start = wedgeStart
            hubs.forEach { hub ->
                val size = wedgeSize * weight[hub] / total
                val angle = start + size / 2
                val distance = (clusterRadius(index) + clusterRadius(hub) + HUB_GAP) * density
                // A hub's own hubs fan out away from its parent, within at most half a turn.
                val childWedge = min(size, PI.toFloat())
                place(
                    hub,
                    x + cos(angle) * distance,
                    y + sin(angle) * distance,
                    angle - childWedge / 2,
                    childWedge,
                )
                start += size
            }
        }

        val visiting = BooleanArray(nodes.size)
        val tops = nodes.indices.filter { isTop[it] }
        tops.forEach { weigh(it, visiting) }

        // The documents at the root sit in the middle, where the root would be.
        val loose = tops.filter { children[it].isEmpty() && !nodes[it].isFolder }
        loose.forEachIndexed { k, index ->
            val radius = LEAF_SPREAD * 2 * sqrt(k + 0.5F) * density
            val angle = k * GOLDEN_ANGLE
            placed[index] = true
            nodes[index].x = centerX + cos(angle) * radius
            nodes[index].y = centerY + sin(angle) * radius
        }
        val looseRadius = if (loose.isEmpty()) 0F else LEAF_SPREAD * 2 * sqrt(loose.size.toFloat())

        // The other pieces on a ring around them, each with an arc as wide as its cluster.
        val clusters = tops.filter { !placed[it] }.sortedByDescending { weight[it] }
        if (clusters.size == 1 && loose.isEmpty()) {
            place(clusters.first(), centerX, centerY, 0F, 2 * PI.toFloat())
        } else if (clusters.isNotEmpty()) {
            val arcs = clusters.map { 2 * clusterRadius(it) + HUB_GAP }
            val ringRadius = max(
                looseRadius + clusterRadius(clusters.first()) + HUB_GAP,
                arcs.sum() / (2 * PI.toFloat()),
            )
            val total = arcs.sum()
            var start = 0F
            clusters.forEachIndexed { k, index ->
                val size = 2 * PI.toFloat() * arcs[k] / total
                val angle = start + size / 2
                val childWedge = min(size, PI.toFloat())
                place(
                    index,
                    centerX + cos(angle) * ringRadius * density,
                    centerY + sin(angle) * ringRadius * density,
                    angle - childWedge / 2,
                    childWedge,
                )
                start += size
            }
        }

        val outerRadius = nodes.indices.filter { placed[it] }.maxOf { index ->
            val dx = nodes[index].x - centerX
            val dy = nodes[index].y - centerY
            sqrt(dx * dx + dy * dy)
        } + LEAF_DISTANCE * density
        val unreached = nodes.indices.filter { !placed[it] }
        unreached.forEachIndexed { k, index ->
            val angle = 2 * PI.toFloat() * k / unreached.size
            nodes[index].x = centerX + cos(angle) * outerRadius
            nodes[index].y = centerY + sin(angle) * outerRadius
        }

        nodes.forEach { node ->
            node.vx = 0F
            node.vy = 0F
        }
    }

    private fun jiggle(i: Int, j: Int): Float = ((i * 31 + j * 17) % 7 - 3) * 1e-3F + 1e-4F

    companion object {
        /** From a hub to each of its documents. */
        const val LEAF_DISTANCE = 44F

        /** How much the ring of documents grows with their number (with its square root). */
        const val LEAF_SPREAD = 9F

        /** The radius of a hub without documents. */
        const val HUB_RADIUS = 20F

        /** The space left between the rings of two linked hubs. */
        const val HUB_GAP = 70F

        const val LEAF_CHARGE = -40F
        const val HUB_CHARGE = -160F

        const val GRAVITY = 0.05F

        const val COLLISION_DISTANCE = 34F
        const val COLLISION_STRENGTH = 0.7F

        const val VELOCITY_DECAY = 0.4F
        const val ALPHA_MIN = 0.001F

        /** About five seconds at 60 frames per second, as in d3. */
        const val COOLING_TICKS = 300

        /** Heat while a node is dragged, so its neighbours follow. */
        const val DRAG_ALPHA = 0.3F

        const val WARM_UP_TICKS = 200
        const val WARM_UP_BUDGET = 40_000_000L

        private const val MIN_DISTANCE = 1F

        /** π(3 − √5): the angle between neighbours on a sunflower spiral. */
        private const val GOLDEN_ANGLE = 2.3999632F
    }
}
