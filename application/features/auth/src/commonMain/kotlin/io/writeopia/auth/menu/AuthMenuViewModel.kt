package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthManager
import io.writeopia.auth.core.repository.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.toBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthMenuViewModel(
    private val authManager: AuthManager,
    private val authRepository: AuthRepository
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
        authRepository.saveUserChoiceOffline()
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
            val result = authManager.signIn(_email.value, _password.value)

            if (result.toBoolean()) {
                try {
//                    introNotesUseCase.addIntroNotes(authManager.getUser().id)
                } catch (e: Exception) {
//                    Log.d("LoginViewModel", "Could not add intro notes. Error: ${e.message}")
                }
            } else if (result is Error) {
//                Log.d("LoginViewModel", "error when singing in: ${result.message}")
            }

            _loginState.value = result
        }
    }
}
