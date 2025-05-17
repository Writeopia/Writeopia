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
internal class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _register = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val register = _register.asStateFlow()

    fun nameChanged(name: String) {
        _name.value = name
    }

    fun emailChanged(email: String) {
        _email.value = email
    }

    fun passwordChanged(password: String) {
        _password.value = password
    }

    fun onRegister() {
        _register.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = authApi.register(
                    name = _name.value,
                    email = _email.value,
                    password = _password.value
                )

                _register.value = when (result) {
                    is ResultData.Complete -> {
                        val user = result.data.writeopiaUser.toModel()

                        authRepository.saveUser(user = user, selected = true)
                        authRepository.saveToken(user.id, result.data.token)

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
                e.printStackTrace()
                delay(300)
                _register.value = ResultData.Error(e)
            }
        }
    }
}
