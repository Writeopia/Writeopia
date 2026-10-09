@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.utils.PasswordStrength
import io.writeopia.auth.utils.PasswordValidationResult
import io.writeopia.auth.utils.PasswordValidator
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import io.writeopia.sdk.serialization.data.toModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// The NavigationActivity won't leak because it is the single activity of the whole project
internal class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _workspace = MutableStateFlow("")
    val company = _workspace.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    // The code that verified [email], sent again so the backend can check it.
    private var verificationCode = ""

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _register = MutableStateFlow<ResultData<Boolean>>(ResultData.Idle())
    val register = _register.asStateFlow()

    val passwordValidation: StateFlow<PasswordValidationResult> = _password
        .map { PasswordValidator.validate(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PasswordValidator.validate("")
        )

    val canRegister: StateFlow<Boolean> = combine(
        _name,
        _username,
        _email,
        _workspace,
        passwordValidation
    ) { name, username, email, workspace, validation ->
        name.isNotBlank() &&
            username.isNotBlank() &&
            email.isNotBlank() &&
            workspace.isNotBlank() &&
            validation.strength == PasswordStrength.STRONG
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun nameChanged(name: String) {
        _name.value = name
    }

    fun usernameChanged(username: String) {
        _username.value = username
    }

    fun workspaceChanged(company: String) {
        _workspace.value = company
    }

    /**
     * The email is verified before registering, in the email confirmation step.
     */
    fun setVerifiedEmail(email: String, code: String) {
        _email.value = email
        verificationCode = code
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
                    workspaceName = _workspace.value,
                    password = _password.value,
                    username = _username.value,
                    verificationCode = verificationCode,
                )

                _register.value = when (result) {
                    is ResultData.Complete -> {
                        val user = result.data.writeopiaUser.toModel()

                        authRepository.saveUser(user = user, selected = true)

                        // The email was verified before registering, so the
                        // account is active and can be signed in right away.
                        signIn(result.data)
                        ResultData.Complete(true)
                    }

                    is ResultData.Error -> {
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
                _register.value = ResultData.Error(e)
            }
        }
    }

    private suspend fun signIn(response: RegisterResponse) {
        if (authRepository.useWebLogin) {
            // Web keeps the session in HttpOnly cookies, which only the web login sets.
            authApi.loginWeb(_email.value, _password.value)
            return
        }

        val accessToken = response.accessToken ?: return

        authRepository.saveTokens(
            userId = response.writeopiaUser.id,
            accessToken = accessToken,
            refreshToken = response.refreshToken,
            // 14 minutes from now, as a buffer before the access token expires.
            expiresAt = Clock.System.now().toEpochMilliseconds() + 14 * 60 * 1000L
        )
    }
}
