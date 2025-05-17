package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.toBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AccountMenuKmpViewModel(
    private val authManager: AuthRepository,
    private val authRepository: AuthRepository,
) : AccountMenuViewModel, ViewModel() {
    private val _isLoggedIn: MutableStateFlow<ResultData<Boolean>> by lazy {
        MutableStateFlow(
            ResultData.Idle()
        )
    }
    override val isLoggedIn: StateFlow<ResultData<Boolean>> by lazy { _isLoggedIn.asStateFlow() }

    override fun checkLoggedIn() {
        viewModelScope.launch {
            _isLoggedIn.value = ResultData.Loading()
//            _isLoggedIn.value = authManager.isLoggedIn()
        }
    }

    override fun logout(onLogOutSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = authManager.logout()

            if (result.toBoolean()) {
                onLogOutSuccess()
            }
        }
    }

    override fun eraseOfflineByChoice(navigateToRegister: () -> Unit) {
        viewModelScope.launch {
//            authRepository.eraseUserChoiceOffline()
            navigateToRegister()
        }
    }
}
