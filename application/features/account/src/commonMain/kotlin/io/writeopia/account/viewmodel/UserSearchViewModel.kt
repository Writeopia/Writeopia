package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.app.dto.SearchUserApi
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class UserSearchViewModel(
    private val workspaceId: String,
    private val workspaceName: String,
    private val workspaceApi: WorkspaceApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<ResultData<List<SearchUserApi>>>(ResultData.Idle())
    val searchResults: StateFlow<ResultData<List<SearchUserApi>>> = _searchResults.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMorePages = MutableStateFlow(false)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private var currentPage = 1
    private val pageSize = 20
    private val loadedUsers = mutableListOf<SearchUserApi>()

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query ->
                    searchUsers(query)
                }
        }
    }

    fun getWorkspaceName(): String = workspaceName

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = ResultData.Idle()
            loadedUsers.clear()
            currentPage = 1
            _hasMorePages.value = false
        }
    }

    private suspend fun searchUsers(query: String) {
        val token = authRepository.getAuthToken() ?: return

        _searchResults.value = ResultData.Loading()
        currentPage = 1
        loadedUsers.clear()

        val result = workspaceApi.searchUsers(
            workspaceId = workspaceId,
            emailQuery = query,
            page = currentPage,
            pageSize = pageSize,
            token = token
        )

        when (result) {
            is ResultData.Complete -> {
                loadedUsers.addAll(result.data.users)
                _searchResults.value = ResultData.Complete(loadedUsers.toList())
                _hasMorePages.value = result.data.hasNextPage
            }
            is ResultData.Error -> {
                _searchResults.value = ResultData.Error(result.exception)
            }
            else -> {}
        }
    }

    fun loadMoreResults() {
        if (_isLoadingMore.value || !_hasMorePages.value) return
        val query = _searchQuery.value
        if (query.length < 2) return

        viewModelScope.launch {
            val token = authRepository.getAuthToken() ?: return@launch

            _isLoadingMore.value = true
            currentPage++

            val result = workspaceApi.searchUsers(
                workspaceId = workspaceId,
                emailQuery = query,
                page = currentPage,
                pageSize = pageSize,
                token = token
            )

            when (result) {
                is ResultData.Complete -> {
                    loadedUsers.addAll(result.data.users)
                    _searchResults.value = ResultData.Complete(loadedUsers.toList())
                    _hasMorePages.value = result.data.hasNextPage
                }
                is ResultData.Error -> {
                    currentPage-- // Revert page on error
                }
                else -> {}
            }

            _isLoadingMore.value = false
        }
    }
}
