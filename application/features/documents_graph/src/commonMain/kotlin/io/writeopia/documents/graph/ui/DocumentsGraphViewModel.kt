package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.documents.graph.ItemData
import io.writeopia.documents.graph.extensions.toGraph
import io.writeopia.documents.graph.repository.GraphRepository
import io.writeopia.forcegraph.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DocumentsGraphViewModel(
    private val graphRepository: GraphRepository
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow("root")

    val graphState: StateFlow<Graph> by lazy {
        _selectedOrigin.map { origin ->
            graphRepository.loadAllDocumentsAsAdjacencyList("disconnected_user")
        }.map { map ->
            map.mapKeys { it.key.id }
                .addRoot()
                .toGraph()
        }.stateIn(viewModelScope, SharingStarted.Lazily, Graph())
    }

    private fun Map<String, List<ItemData>>.addRoot(): Map<String, List<ItemData>> {
        val root = "root" to this.values.flatten().filter { it.parentId == "root" }

        return this + root
    }
}
