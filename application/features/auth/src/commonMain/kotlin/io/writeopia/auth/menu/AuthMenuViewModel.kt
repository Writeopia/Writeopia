@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.LocalAiRepository
import io.writeopia.api.LocalAiApi
import io.writeopia.auth.core.data.AccountDeletionPendingException
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.data.GoogleSignUpRequiredException
import io.writeopia.auth.core.data.UsernameTakenException
import io.writeopia.auth.google.GoogleCredential
import io.writeopia.auth.google.toRequest
import io.writeopia.auth.register.LEGAL_TERMS
import io.writeopia.auth.register.LegalDocumentState
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.LoginStatus
import io.writeopia.common.utils.env.EnvUtils
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
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
    private val localAiRepository: LocalAiRepository,
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

    private val _accountDeletionPending = MutableStateFlow(false)
    val accountDeletionPending = _accountDeletionPending.asStateFlow()

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
            // Signed in but never got past the paywall (the app was closed on it). The stored tier
            // is not trusted: the paywall asks the backend and lets a premium user straight through.
            else -> LoginStatus.SUBSCRIPTION_REQUIRED
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

                localAiRepository.saveLocalAiUrl(userId, LocalAiApi.defaultUrl())
                configRepository.setTutorialNotes(true, userId)
            }

            localAiRepository.refreshConfiguration(userId)

            sideEffect()
        }
    }

    fun onLoginRequest() {
        _loginState.value = ResultData.Loading()
        _accountDeletionPending.value = false

        viewModelScope.launch {
            try {
                val result = if (authRepository.useWebLogin) {
                    authApi.loginWeb(_email.value, _password.value)
                } else {
                    authApi.login(_email.value, _password.value)
                }

                _loginState.value = handleAuthResult(result)
            } catch (e: Exception) {
                delay(300)
                _loginState.value = ResultData.Error(e)
            }
        }
    }

    private val _googleSignUp = MutableStateFlow<GoogleSignUpState?>(null)

    /** The first sign-in of a Google account: the username and the Terms to ask for. */
    val googleSignUp = _googleSignUp.asStateFlow()

    private val _legalDocument = MutableStateFlow<LegalDocumentState>(LegalDocumentState.Hidden)
    val legalDocument = _legalDocument.asStateFlow()
    private var legalJob: Job? = null

    /**
     * Sends the credential obtained from the platform Google UI to the backend. The backend
     * answers with the same payload as a password login, so the rest of the flow is shared; a
     * Google account with no Writeopia account opens the sign-up step instead.
     */
    fun onGoogleLoginRequest(credential: GoogleCredential) {
        _loginState.value = ResultData.Loading()
        _accountDeletionPending.value = false

        viewModelScope.launch {
            try {
                val result = loginWithGoogle(credential.toRequest())
                val signUp = (result as? ResultData.Error)?.exception as? GoogleSignUpRequiredException

                if (signUp != null) {
                    _googleSignUp.value = GoogleSignUpState(
                        idToken = signUp.details.idToken,
                        email = signUp.details.email,
                        username = signUp.details.suggestedUsername,
                    )
                    _loginState.value = ResultData.Idle()
                } else {
                    _loginState.value = handleAuthResult(result)
                }
            } catch (e: Exception) {
                delay(300)
                _loginState.value = ResultData.Error(e)
            }
        }
    }

    /** The platform Google UI failed before reaching the backend; surfaces the login error. */
    fun onGoogleSignInFailed(error: Throwable) {
        _accountDeletionPending.value = false
        _loginState.value = ResultData.Error(error as? Exception ?: Exception(error))
    }

    fun googleUsernameChanged(username: String) {
        _googleSignUp.value = _googleSignUp.value?.copy(username = username, error = null)
    }

    fun googleTermsAcceptedChanged(accepted: Boolean) {
        _googleSignUp.value = _googleSignUp.value?.copy(termsAccepted = accepted)
    }

    fun dismissGoogleSignUp() {
        _googleSignUp.value = null
    }

    /** Creates the account with the chosen username and the Terms as served now. */
    fun confirmGoogleSignUp() {
        val signUp = _googleSignUp.value ?: return
        if (!signUp.canCreate) return
        _googleSignUp.value = signUp.copy(inProgress = true, error = null)

        viewModelScope.launch {
            // The version of the texts the user agreed to is the one served right now: the
            // backend refuses an older one.
            val legalVersion = when (val legal = authApi.legalDocument(LEGAL_TERMS)) {
                is ResultData.Complete -> legal.data.version
                else -> {
                    _googleSignUp.value = signUp.copy(error = GoogleSignUpError.FAILED)
                    return@launch
                }
            }

            val result = loginWithGoogle(
                GoogleLoginRequest(
                    idToken = signUp.idToken,
                    username = signUp.username.trim(),
                    acceptedLegalVersion = legalVersion,
                )
            )
            val error = (result as? ResultData.Error)?.exception

            when {
                error is UsernameTakenException ->
                    _googleSignUp.value = signUp.copy(error = GoogleSignUpError.USERNAME_TAKEN)

                result is ResultData.Error && error !is AccountDeletionPendingException ->
                    _googleSignUp.value = signUp.copy(error = GoogleSignUpError.FAILED)

                else -> {
                    _googleSignUp.value = null
                    _loginState.value = handleAuthResult(result)
                }
            }
        }
    }

    /** Loads and shows the `terms` or `privacy` document, from the sign-up step. */
    fun openLegalDocument(type: String) {
        legalJob?.cancel()
        _legalDocument.value = LegalDocumentState.Loading(type)

        legalJob = viewModelScope.launch {
            val result = authApi.legalDocument(type)
            ensureActive()

            _legalDocument.value = when (result) {
                is ResultData.Complete -> LegalDocumentState.Loaded(result.data)
                else -> LegalDocumentState.Failed(type)
            }
        }
    }

    fun closeLegalDocument() {
        legalJob?.cancel()
        _legalDocument.value = LegalDocumentState.Hidden
    }

    private suspend fun loginWithGoogle(request: GoogleLoginRequest): ResultData<AuthResponse> =
        if (authRepository.useWebLogin) {
            authApi.loginWithGoogleWeb(request)
        } else {
            authApi.loginWithGoogle(request)
        }

    /** A login answer, by password or Google: the user and its tokens are kept, or the error shown. */
    private suspend fun handleAuthResult(result: ResultData<AuthResponse>): ResultData<Boolean> =
        when (result) {
            is ResultData.Complete -> {
                val user = result.data.writeopiaUser.toModel()

                // Check if user is enabled (email confirmed)
                if (!result.data.enabled) {
                    // User exists but email not confirmed
                    authRepository.savePendingConfirmationEmail(user.email)
                    _emailConfirmationRequired.value = true
                    result.map { true }
                } else {
                    _emailConfirmationRequired.value = false
                    EnvUtils.getAdminKey()?.let { adminKey ->
                        authApi.enableUser(user.email, adminKey)
                    }

                    authRepository.unselectAllUsers()
                    authRepository.saveUser(
                        user = user,
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
                _accountDeletionPending.value = result.exception is AccountDeletionPendingException
                result.map { false }
            }

            else -> {
                delay(300)
                ResultData.Idle()
            }
        }
}

enum class GoogleSignUpError { USERNAME_TAKEN, FAILED }

/** The sign-up step of a first Google sign-in. */
data class GoogleSignUpState(
    val idToken: String,
    val email: String,
    val username: String,
    val termsAccepted: Boolean = false,
    val inProgress: Boolean = false,
    val error: GoogleSignUpError? = null,
) {
    val canCreate: Boolean get() = termsAccepted && username.isNotBlank() && !inProgress
}
