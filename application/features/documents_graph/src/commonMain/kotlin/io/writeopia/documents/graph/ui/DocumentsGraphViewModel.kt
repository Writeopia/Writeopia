package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.documents.graph.ItemData
import io.writeopia.documents.graph.repository.GraphRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DocumentsGraphViewModel(
    private val graphRepository: GraphRepository
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow("root")

    private val _selectedNodes = MutableStateFlow(setOf<String>())

    private val graphState: StateFlow<Map<String, List<ItemData>>> by lazy {
        _selectedOrigin.map { origin ->
            graphRepository.loadAllDocumentsAsAdjacencyList("disconnected_user")
        }.map { map ->
            map.mapKeys { it.key.id }
                .addRoot()

        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }

    val graphSelectedState: StateFlow<Map<String, List<ItemData>>> by lazy {
        combine(graphState,_selectedNodes ) { map, selected ->
            map.mapValues { (_, menuItems) ->
                menuItems.map { item ->
                    item.copy(selected = selected.contains(item.id))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
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
}
