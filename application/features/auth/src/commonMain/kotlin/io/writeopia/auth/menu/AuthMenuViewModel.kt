package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthManager
import io.writeopia.auth.core.repository.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthMenuViewModel(
    private val authManager: AuthManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isConnected = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val isConnected = _isConnected.asStateFlow()

    fun checkLoggedIn() {
        viewModelScope.launch(Dispatchers.Default) {
            _isConnected.value = ResultData.Loading()
            _isConnected.value =
                authManager.isLoggedIn().map { isConnected ->
                    isConnected || authRepository.isUserOfflineByChoice()
                }
        }
    }

    fun saveUserChoiceOffline() {
        authRepository.saveUserChoiceOffline()
    }
}
