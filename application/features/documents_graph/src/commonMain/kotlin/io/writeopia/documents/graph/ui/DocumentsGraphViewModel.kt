package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.documents.graph.GraphGrouping
import io.writeopia.documents.graph.GraphItemDetails
import io.writeopia.documents.graph.ItemData
import io.writeopia.documents.graph.extensions.toGraph
import io.writeopia.documents.graph.repository.GraphRepository
import io.writeopia.forcegraph.model.Graph
import io.writeopia.forcegraph.Link
import io.writeopia.forcegraph.Node
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class DocumentsGraphViewModel(
    private val graphRepository: GraphRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow("root")
    private val _selectedNodes = MutableStateFlow(setOf<String>())

    private val _grouping = MutableStateFlow(GraphGrouping.FOLDERS)

    /** Whether the hubs are the folders or the tags of the workspace. */
    val grouping: StateFlow<GraphGrouping> = _grouping.asStateFlow()

    fun setGrouping(grouping: GraphGrouping) {
        _grouping.value = grouping
        _selectedItem.value = null
    }

    private val _selectedItem = MutableStateFlow<GraphItemDetails?>(null)

    /** The node the user clicked last, for the card at the corner of the graph; null when closed. */
    val selectedItem: StateFlow<GraphItemDetails?> = _selectedItem.asStateFlow()

    fun closeDetails() {
        _selectedItem.value = null
    }

    private var maxWidth: Float = 1000F
    private var maxHeight: Float = 1000F

    // The forces are tuned in dp, like the points of the Swift app; the graph is laid out in pixels.
    private var density: Float = 1F

    fun initSize(maxWidth: Float, maxHeight: Float, density: Float = 1F) {
        this.maxWidth = maxWidth
        this.maxHeight = maxHeight
        this.density = density
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val graphState: StateFlow<Graph> by lazy {
        combine(_selectedOrigin, _grouping) { _, grouping -> grouping }.map { grouping ->
            val workspaceId = authRepository.getWorkspace()?.id

            val result: Map<String, List<ItemData>> = when {
                workspaceId == null -> emptyMap()

                grouping == GraphGrouping.TAGS ->
                    graphRepository.loadDocumentsByTagsAsAdjacencyList(workspaceId).mapKeys { it.key.id }

                else ->
                    graphRepository.loadAllDocumentsAsAdjacencyList(workspaceId).mapKeys { it.key.id }.addRoot()
            }

            val nodes = result.values.flatten().distinctBy { it.id }
            val isSmall = nodes.size <= 12

            if (isSmall) {
                _selectedNodes.value = nodes.map { it.id }.toSet()
            }

            result
        }.map { map ->
            map.toGraph(maxWidth, maxHeight)
        }.flatMapLatest { graph ->
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(graph.tick(0.016f))
                    delay(16)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, Graph())
    }

    val graphSelectedState: StateFlow<Graph> by lazy {
        combine(graphState, _selectedNodes) { graph, selected ->
            val newNodes = graph.nodes.map { node ->
                node.showName = selected.contains(node.id) || node.isFolder
                node
            }

            graph.copy(nodes = newNodes)
        }.stateIn(viewModelScope, SharingStarted.Lazily, Graph())
    }

    fun selectNode(id: String) {
        if (_selectedNodes.value.contains(id)) {
            _selectedNodes.value -= id
        } else {
            _selectedNodes.value += id
        }

        viewModelScope.launch {
            val workspaceId = authRepository.getWorkspace()?.id ?: return@launch
            _selectedItem.value = graphRepository.loadDetails(id, workspaceId)
        }
    }

    private fun Map<String, List<ItemData>>.addRoot(): Map<String, List<ItemData>> {
        val root = "root" to this.values.flatten().filter { it.parentId == "root" }

        return this + root
    }

    private fun Graph.tick(dt: Float): Graph {
        applyLinkForce(links)
        applyChargeForce(nodes)
        applyCenteringForce(nodes)
        updatePositions(nodes, dt)
        applyCollision(nodes)

        return this
    }

    private fun applyLinkForce(links: List<Link>) {
        val linkDistance = LINK_DISTANCE * density
        val strength = 0.15f

        for (link in links) {
            val dx = link.target.x - link.source.x
            val dy = link.target.y - link.source.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val force = (distance - linkDistance) * strength
            val fx = force * dx / distance
            val fy = force * dy / distance

            link.source.vx += fx
            link.source.vy += fy
            link.target.vx -= fx
            link.target.vy -= fy
        }
    }

    private fun applyChargeForce(nodes: List<Node>) {
        // Forces grow with distance (links, centre) but charge falls with its square: in pixels
        // it scales with the cube of the density to keep the same balance.
        val chargeStrength = CHARGE_STRENGTH * density * density * density

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val nodeA = nodes[i]
                val nodeB = nodes[j]
                val dx = nodeB.x - nodeA.x
                val dy = nodeB.y - nodeA.y
                val distanceSq = (dx * dx + dy * dy).coerceAtLeast(0.01f)
                val force = chargeStrength / distanceSq
                val fx = force * dx / sqrt(distanceSq)
                val fy = force * dy / sqrt(distanceSq)

                nodeA.vx -= fx
                nodeA.vy -= fy
                nodeB.vx += fx
                nodeB.vy += fy
            }
        }
    }

    private fun applyCenteringForce(nodes: List<Node>) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        val strength = 0.002f

        for (node in nodes) {
            node.vx += (centerX - node.x) * strength
            node.vy += (centerY - node.y) * strength
        }
    }

    private fun updatePositions(nodes: List<Node>, dt: Float) {
        val damping = 0.95f

        for (node in nodes) {
            node.vx *= damping
            node.vy *= damping
            node.x += node.vx * dt
            node.y += node.vy * dt
        }
    }

    /**
     * Pushes apart the nodes closer than [COLLISION_DISTANCE], so the dots and their names don't
     * sit on top of each other. It moves the positions, after the velocities, like a d3 collide.
     */
    private fun applyCollision(nodes: List<Node>) {
        val minDistance = COLLISION_DISTANCE * density

        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val nodeA = nodes[i]
                val nodeB = nodes[j]
                val dx = nodeB.x - nodeA.x
                val dy = nodeB.y - nodeA.y
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                if (distance >= minDistance) continue

                val push = (minDistance - distance) / 4
                val px = push * dx / distance
                val py = push * dy / distance

                if (!nodeA.isDragged) {
                    nodeA.x -= px
                    nodeA.y -= py
                }
                if (!nodeB.isDragged) {
                    nodeB.x += px
                    nodeB.y += py
                }
            }
        }
    }

    private companion object {
        const val LINK_DISTANCE = 100F
        const val CHARGE_STRENGTH = 12000F
        const val COLLISION_DISTANCE = 64F
    }
}
