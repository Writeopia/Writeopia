package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.map
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.toModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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

    fun emailChanged(name: String) {
        _email.value = name
    }

    fun passwordChanged(name: String) {
        _password.value = name
    }

    fun isLoggedIn(): Flow<Boolean> = flow {
        val user = authRepository.getUser()
        val loggedId = authRepository.isLoggedIn() || user.id != WriteopiaUser.DISCONNECTED

        emit(loggedId)
    }

    fun useOffline(sideEffect: () -> Unit) {
        viewModelScope.launch {
            authRepository.useOffline()
            sideEffect()
        }
    }

    fun onLoginRequest() {
        _loginState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.login(_email.value, _password.value)

                _loginState.value = when (result) {
                    is ResultData.Complete -> {
                        val user = result.data.writeopiaUser.toModel()

                        authRepository.saveUser(
                            user = user.copy(tier = Tier.PREMIUM),
                            selected = true
                        )
                        result.data.token?.let { token ->
                            authRepository.saveToken(user.id, token)
//                            AppConnectionInjection.singleton().setJwtToken(token)
                        }

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
                _loginState.value = ResultData.Error(e)
            }
        }
    }
}
