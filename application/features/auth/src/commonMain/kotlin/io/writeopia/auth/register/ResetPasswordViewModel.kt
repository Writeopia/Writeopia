package io.writeopia.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.ResultData
import io.writeopia.common.utils.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// The NavigationActivity won't leak because it is the single activity of the whole project
internal class ResetPasswordViewModel(
    private val authApi: AuthApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _repeatPassword = MutableStateFlow("")
    val repeatPassword = _repeatPassword.asStateFlow()

    private val _resetPassword = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val resetPassword = _resetPassword.asStateFlow()

    fun passwordChanged(password: String) {
        _password.value = password
    }

    fun repeatPasswordChanged(password: String) {
        _repeatPassword.value = password
    }

    fun onResetPassword() {
        if (_password.value != _repeatPassword.value) {
            // Show problem to user!
            return
        }

        _resetPassword.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val token = authRepository.getAuthToken()
                val result = authApi.resetPassword(_password.value, token ?: "")

                _resetPassword.value = when (result) {
                    is ResultData.Complete -> {
                        result.map { true }
                    }

                    is Error -> {
                        delay(300)
                        result.map { false }
                    }

                    else -> {
                        delay(300)
                        ResultData.Idle()
                    }
                }
            } catch (e: Exception) {
                delay(300)
                _resetPassword.value = ResultData.Error(e)
            }
        }
    }
}
