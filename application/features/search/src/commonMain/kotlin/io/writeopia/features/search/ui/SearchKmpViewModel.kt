package io.writeopia.features.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.features.search.repository.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SearchKmpViewModel(
    private val searchRepository: SearchRepository
) : SearchViewModel, ViewModel() {

    private val _searchState = MutableStateFlow("")
    override val searchState: StateFlow<String> = _searchState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override val queryResults by lazy {
        val localFlow =
            searchState.flatMapLatest(searchRepository::searchNotesAndFoldersLocally)

        val remoteFlow = searchState
            .debounce(500)
            .flatMapLatest(searchRepository::searchNotesAndFoldersRemotely)

        combine(localFlow, remoteFlow) { local, remote -> local + remote }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    override fun init() {}

    override fun onSearchType(query: String) {
        _searchState.value = query
    }
}
