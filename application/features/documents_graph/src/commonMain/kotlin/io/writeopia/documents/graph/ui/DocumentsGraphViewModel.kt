package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.documents.graph.GraphGrouping
import io.writeopia.documents.graph.GraphItemDetails
import io.writeopia.documents.graph.ItemData
import io.writeopia.documents.graph.extensions.toGraph
import io.writeopia.documents.graph.layout.ForceLayout
import io.writeopia.documents.graph.repository.GraphRepository
import io.writeopia.forcegraph.model.Graph
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

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

    // The forces are tuned in dp; the graph is laid out in pixels.
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
            val graph = map.toGraph()
            val layout = ForceLayout(graph.nodes, graph.links, density)
            // Placed near where the forces will settle it and mostly cooled before it is shown,
            // so the first frame already looks like the last one.
            withContext(Dispatchers.Default) {
                layout.placeRadially(maxWidth / 2, maxHeight / 2)
                layout.warmUp(maxWidth / 2, maxHeight / 2)
            }
            layout
        }.flatMapLatest { layout ->
            flow {
                emit(Graph(layout.nodes, layout.links))
                // The nodes are Compose state: each tick redraws the graph without a new emission.
                // Once settled nothing moves until a node is dragged.
                while (currentCoroutineContext().isActive) {
                    layout.tick(maxWidth / 2, maxHeight / 2)
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
}
