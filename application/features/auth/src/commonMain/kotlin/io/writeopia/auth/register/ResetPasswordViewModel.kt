package io.writeopia.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.map
import io.writeopia.sdk.serialization.data.toModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// The NavigationActivity won't leak because it is the single activity of the whole project
internal class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _repeatPassword = MutableStateFlow("")
    val repeatPassword = _repeatPassword.asStateFlow()

    private val _register = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val register = _register.asStateFlow()

    fun passwordChanged(password: String) {
        _password.value = password
    }

    fun repeatPasswordChanged(password: String) {
        _password.value = password
    }

    fun onRegister() {
        _register.value = ResultData.Loading()

        viewModelScope.launch {
            try {

            } catch (e: Exception) {
                delay(300)
                _register.value = ResultData.Error(e)
            }
        }
    }
}
