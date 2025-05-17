package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.map
import io.writeopia.common.utils.toBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthMenuViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _loginState: MutableStateFlow<ResultData<Boolean>> =
        MutableStateFlow(ResultData.Idle())
    val loginState = _loginState.asStateFlow()

    private val _isConnected = MutableStateFlow<ResultData<Boolean>>(ResultData.Complete(false))
    val isConnected = _isConnected.asStateFlow()

    fun saveUserChoiceOffline() {
//        authRepository.saveUserChoiceOffline()
    }

    fun emailChanged(name: String) {
        _email.value = name
    }

    fun passwordChanged(name: String) {
        _password.value = name
    }

    fun onLoginRequest() {
        _loginState.value = ResultData.Loading()

        viewModelScope.launch {
            val result = authApi.login(_email.value, _password.value)

            if (result is ResultData.Complete) {
                _loginState.value = result.map { true }
            } else if (result is Error) {
                _loginState.value = result.map { false }
            }
        }
    }
}
