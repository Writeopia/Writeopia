package io.writeopia.documents.graph.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.documents.graph.extensions.toGraph
import io.writeopia.forcegraph.Graph
import io.writeopia.models.interfaces.LoadDocuments
import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DocumentsGraphViewModel(
    private val loadDocuments: LoadDocuments,
) : ViewModel() {

    private val _selectedOrigin = MutableStateFlow("root")

    @OptIn(ExperimentalCoroutinesApi::class)
    val graphState: StateFlow<Graph<MenuItem>> by lazy {
        _selectedOrigin.flatMapLatest { origin ->
            loadDocuments.listenForMenuItemsByParentId(
                parentId = origin,
                userId = "disconnected_user"
            )
        }.map { map ->
            map.toGraph()
        }.stateIn(viewModelScope, SharingStarted.Lazily, Graph())
    }
}
