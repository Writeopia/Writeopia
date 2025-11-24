package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.OllamaRepository
import io.writeopia.api.OllamaApi
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.LoginStatus
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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.sequences.forEach
import kotlin.sequences.map

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

    fun emailChanged(name: String) {
        _email.value = name
    }

    fun passwordChanged(name: String) {
        _password.value = name
    }

    fun isLoggedIn(): Flow<LoginStatus> = flow {
        val user = authRepository.getUser()
        val workspace = authRepository.getWorkspace()
        val loggedId = authRepository.isLoggedIn() || user.id != WriteopiaUser.DISCONNECTED

        val status = when {
            loggedId && workspace != null -> LoginStatus.ONLINE

            loggedId && workspace == null -> LoginStatus.CHOOSE_WORKSPACE

            !loggedId && workspace != null -> LoginStatus.OFFLINE_CHOSEN

            else -> LoginStatus.OFFLINE_NOT_CHOSEN
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
                val result = authApi.login(_email.value, _password.value)

                _loginState.value = when (result) {
                    is ResultData.Complete -> {
                        val user = result.data.writeopiaUser.toModel()

                        authRepository.unselectAllUsers()
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
