@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.OllamaRepository
import io.writeopia.api.OllamaApi
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.LoginStatus
import io.writeopia.common.utils.env.EnvUtils
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.utils.map
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.toModel
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.tutorials.Tutorials
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlin.sequences.forEach
import kotlin.sequences.map
import kotlin.time.ExperimentalTime

class AuthMenuViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
    private val configRepository: ConfigurationRepository,
    private val notesUseCase: NotesUseCase,
    private val ollamaRepository: OllamaRepository,
    private val json: Json = writeopiaJson,
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _loginState: MutableStateFlow<ResultData<Boolean>> =
        MutableStateFlow(ResultData.Idle())
    val loginState = _loginState.asStateFlow()

    private val _emailConfirmationRequired = MutableStateFlow(false)
    val emailConfirmationRequired = _emailConfirmationRequired.asStateFlow()

    fun emailChanged(name: String) {
        _email.value = name
    }

    fun passwordChanged(name: String) {
        _password.value = name
    }

    fun isLoggedIn(): Flow<LoginStatus> = flow {
        // Check if there's a pending email confirmation first
        val pendingConfirmationEmail = authRepository.getPendingConfirmationEmail()
        if (!pendingConfirmationEmail.isNullOrEmpty()) {
            emit(LoginStatus.EMAIL_NOT_CONFIRMED)
            return@flow
        }

        // Check user state
        val user = authRepository.getUser()

        // No user record exists - show auth screen
        if (user.id == WriteopiaUser.NO_USER) {
            emit(LoginStatus.OFFLINE_NOT_CHOSEN)
            return@flow
        }

        // User explicitly chose offline mode
        if (user.id == WriteopiaUser.DISCONNECTED) {
            emit(LoginStatus.OFFLINE_CHOSEN)
            return@flow
        }

        // Online mode - check if authenticated
        // For web: uses session cookie check (tokens are in HttpOnly cookies)
        // For other platforms: checks if token is available in storage
        val isAuthenticated = if (authRepository.useWebLogin) {
            authRepository.isLoggedIn()
        } else {
            !authRepository.getAuthToken().isNullOrEmpty()
        }

        if (!isAuthenticated) {
            emit(LoginStatus.OFFLINE_NOT_CHOSEN)
            return@flow
        }

        // Authenticated - check workspace
        val workspace = authRepository.getWorkspace()
        val status = when {
            workspace != null -> LoginStatus.ONLINE
            else -> LoginStatus.CHOOSE_WORKSPACE
        }
        emit(status)
    }

    fun useOffline(sideEffect: () -> Unit) {
        viewModelScope.launch {
            authRepository.useOffline()

            val userId = WriteopiaUser.disconnectedUser().id
            val workspace = Workspace.disconnectedWorkspace()
            val workspaceId = workspace.id

            if (!configRepository.hasFirstConfiguration(userId)) {
                val now = Clock.System.now()

                Tutorials.allTutorialsDocuments()
                    .map { documentAsJson ->
                        json.decodeFromString<DocumentApi>(documentAsJson)
                            .toModel()
                    }
                    .forEach { document ->
                        notesUseCase.saveDocumentDb(
                            document.copy(
                                parentId = document.parentId,
                                workspaceId = workspaceId,
                                createdAt = now,
                                lastUpdatedAt = now
                            )
                        )
                    }

                ollamaRepository.saveOllamaUrl(userId, OllamaApi.defaultUrl())
                configRepository.setTutorialNotes(true, userId)
            }

            ollamaRepository.refreshConfiguration(userId)

            sideEffect()
        }
    }

    fun onLoginRequest() {
        _loginState.value = ResultData.Loading()

        viewModelScope.launch {
            try {
                val result = if (authRepository.useWebLogin) {
                    authApi.loginWeb(_email.value, _password.value)
                } else {
                    authApi.login(_email.value, _password.value)
                }

                _loginState.value = when (result) {
                    is ResultData.Complete -> {
                        val user = result.data.writeopiaUser.toModel()

                        // Check if user is enabled (email confirmed)
                        if (!result.data.enabled) {
                            // User exists but email not confirmed
                            authRepository.savePendingConfirmationEmail(_email.value)
                            _emailConfirmationRequired.value = true
                            result.map { true }
                        } else {
                            _emailConfirmationRequired.value = false
                            EnvUtils.getAdminKey()?.let { adminKey ->
                                authApi.enableUser(_email.value, adminKey)
                            }

                            authRepository.unselectAllUsers()
                            authRepository.saveUser(
                                user = user.copy(tier = Tier.PREMIUM),
                                selected = true
                            )
                            val accessToken = result.data.accessToken
                            val refreshToken = result.data.refreshToken
                            if (accessToken != null) {
                                // Calculate expiry time (14 minutes from now as buffer)
                                val expiresAt = Clock.System.now().toEpochMilliseconds() + (14 * 60 * 1000L)
                                authRepository.saveTokens(
                                    userId = user.id,
                                    accessToken = accessToken,
                                    refreshToken = refreshToken,
                                    expiresAt = expiresAt
                                )
                            }

                            result.map { true }
                        }
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
                _loginState.value = ResultData.Error(e)
            }
        }
    }
}
