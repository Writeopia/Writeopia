package io.writeopia.auth.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class EmailConfirmationViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()

    private val _confirmState = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val confirmState = _confirmState.asStateFlow()

    private val _resendState = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val resendState = _resendState.asStateFlow()

    fun loadPendingEmail() {
        viewModelScope.launch {
            _email.value = authRepository.getPendingConfirmationEmail() ?: ""
        }
    }

    fun codeChanged(newCode: String) {
        // Only allow digits, max 6 characters
        val filtered = newCode.filter { it.isDigit() }.take(6)
        _code.value = filtered
    }

    fun onConfirm(onSuccess: () -> Unit) {
        if (_code.value.length != 6) {
            _confirmState.value = ResultData.Error(Exception("Code must be 6 digits"))
            return
        }

        _confirmState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.confirmEmail(_email.value, _code.value)

                _confirmState.value = when (result) {
                    is ResultData.Complete -> {
                        authRepository.clearPendingConfirmationEmail()
                        onSuccess()
                        result
                    }
                    is ResultData.Error -> {
                        delay(300)
                        result
                    }
                    else -> {
                        delay(300)
                        ResultData.Idle()
                    }
                }
            } catch (e: Exception) {
                delay(300)
                _confirmState.value = ResultData.Error(e)
            }
        }
    }

    fun onResend() {
        _resendState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.resendConfirmationEmail(_email.value)

                _resendState.value = when (result) {
                    is ResultData.Complete -> {
                        delay(300)
                        result
                    }
                    is ResultData.Error -> {
                        delay(300)
                        result
                    }
                    else -> {
                        delay(300)
                        ResultData.Idle()
                    }
                }
            } catch (e: Exception) {
                delay(300)
                _resendState.value = ResultData.Error(e)
            }
        }
    }

    fun resetResendState() {
        _resendState.value = ResultData.Idle()
    }

    fun resetConfirmState() {
        _confirmState.value = ResultData.Idle()
    }
}
