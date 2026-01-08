package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

class DocumentsGraphViewModel(
    private val graphRepository: GraphRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow("root")
    private val _selectedNodes = MutableStateFlow(setOf<String>())

    private var maxWidth: Float = 1000F
    private var maxHeight: Float = 1000F

    fun initSize(maxWidth: Float, maxHeight: Float) {
        this.maxWidth = maxWidth
        this.maxHeight = maxHeight
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val graphState: StateFlow<Graph> by lazy {
        _selectedOrigin.map { origin ->
            val result =
                authRepository.getWorkspace()
                    ?.id
                    ?.let { graphRepository.loadAllDocumentsAsAdjacencyList(it) }
                    ?: emptyMap()

            val nodes = result.values.flatten()
            val isSmall = nodes.size <= 12

            if (isSmall) {
                _selectedNodes.value = nodes.map { it.id }.toSet()
            }

            result
        }.map { map ->
            map.mapKeys { it.key.id }.addRoot().toGraph(maxWidth, maxHeight)
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

        return this
    }

    private fun applyLinkForce(links: List<Link>) {
        val linkDistance = 40F
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
        val chargeStrength = if (nodes.size < 10) 100f else 2000f

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
        val strength = 0.003f

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
}
