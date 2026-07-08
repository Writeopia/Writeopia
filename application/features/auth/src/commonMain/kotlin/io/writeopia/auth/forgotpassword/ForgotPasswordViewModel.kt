package io.writeopia.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ForgotPasswordViewModel(
    private val authApi: AuthApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()

    fun loadForgotPasswordData() {
        viewModelScope.launch {
            _email.value = authRepository.getForgotPasswordEmail() ?: ""
            _code.value = authRepository.getForgotPasswordCode() ?: ""
        }
    }

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _repeatPassword = MutableStateFlow("")
    val repeatPassword = _repeatPassword.asStateFlow()

    private val _sendCodeState = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val sendCodeState = _sendCodeState.asStateFlow()

    private val _verifyCodeState = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val verifyCodeState = _verifyCodeState.asStateFlow()

    private val _resetPasswordState = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val resetPasswordState = _resetPasswordState.asStateFlow()

    private val _resendCooldownSeconds = MutableStateFlow(0)
    val resendCooldownSeconds = _resendCooldownSeconds.asStateFlow()

    companion object {
        private const val RESEND_COOLDOWN_SECONDS = 30
    }

    fun emailChanged(newEmail: String) {
        _email.value = newEmail
        viewModelScope.launch {
            authRepository.saveForgotPasswordEmail(newEmail)
        }
    }

    fun codeChanged(newCode: String) {
        // Only allow digits, max 6 characters
        val filtered = newCode.filter { it.isDigit() }.take(6)
        _code.value = filtered
        viewModelScope.launch {
            authRepository.saveForgotPasswordCode(filtered)
        }
    }

    fun passwordChanged(newPassword: String) {
        _password.value = newPassword
    }

    fun repeatPasswordChanged(newRepeatPassword: String) {
        _repeatPassword.value = newRepeatPassword
    }

    fun onSendCode(onSuccess: () -> Unit) {
        if (_email.value.isBlank()) {
            _sendCodeState.value = ResultData.Error(Exception("Email is required"))
            return
        }

        _sendCodeState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.requestPasswordReset(_email.value)

                _sendCodeState.value = when (result) {
                    is ResultData.Complete -> {
                        startCooldownTimer()
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
                _sendCodeState.value = ResultData.Error(e)
            }
        }
    }

    fun onResendCode() {
        if (_resendCooldownSeconds.value > 0) return

        _sendCodeState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.requestPasswordReset(_email.value)

                _sendCodeState.value = when (result) {
                    is ResultData.Complete -> {
                        delay(300)
                        startCooldownTimer()
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
                _sendCodeState.value = ResultData.Error(e)
            }
        }
    }

    fun onVerifyCode(onSuccess: () -> Unit) {
        if (_code.value.length != 6) {
            _verifyCodeState.value = ResultData.Error(Exception("Code must be 6 digits"))
            return
        }

        _verifyCodeState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.verifyPasswordResetCode(_email.value, _code.value)

                _verifyCodeState.value = when (result) {
                    is ResultData.Complete -> {
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
                _verifyCodeState.value = ResultData.Error(e)
            }
        }
    }

    fun onResetPassword(onSuccess: () -> Unit) {
        if (_password.value.isBlank()) {
            _resetPasswordState.value = ResultData.Error(Exception("Password is required"))
            return
        }

        if (_password.value != _repeatPassword.value) {
            _resetPasswordState.value = ResultData.Error(Exception("Passwords do not match"))
            return
        }

        _resetPasswordState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.resetPasswordWithCode(_email.value, _code.value, _password.value)

                _resetPasswordState.value = when (result) {
                    is ResultData.Complete -> {
                        authRepository.clearForgotPasswordData()
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
                _resetPasswordState.value = ResultData.Error(e)
            }
        }
    }

    private fun startCooldownTimer() {
        viewModelScope.launch {
            _resendCooldownSeconds.value = RESEND_COOLDOWN_SECONDS
            while (_resendCooldownSeconds.value > 0) {
                delay(1000)
                _resendCooldownSeconds.value -= 1
            }
        }
    }

    fun resetStates() {
        _sendCodeState.value = ResultData.Idle()
        _verifyCodeState.value = ResultData.Idle()
        _resetPasswordState.value = ResultData.Idle()
    }
}
