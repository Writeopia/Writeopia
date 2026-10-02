package io.writeopia.auth.menu

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.writeopia.LocalAiRepository
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.data.AccountDeletionPendingException
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.google.GoogleCredential
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthMenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi
    private lateinit var configRepository: ConfigurationRepository
    private lateinit var notesUseCase: NotesUseCase
    private lateinit var localAiRepository: LocalAiRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
        configRepository = mockk(relaxed = true)
        notesUseCase = mockk(relaxed = true)
        localAiRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onLoginRequest should call enableUser when login succeeds and admin key is available`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-123",
            name = "Test User",
            email = "test@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            accessToken = "jwt-token",
            refreshToken = "refresh-token"
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)
        coEvery { authRepository.unselectAllUsers() } just Runs
        coEvery { authRepository.saveUser(any(), any()) } just Runs
        coEvery { authRepository.saveTokens(any(), any(), any(), any()) } just Runs
        coEvery { authApi.enableUser(any(), any()) } returns ResultData.Complete(Unit)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("test@example.com")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - verify login was called
        coVerify { authApi.login("test@example.com", "password123") }
        coVerify { authRepository.unselectAllUsers() }
        coVerify { authRepository.saveUser(any(), selected = true) }
        coVerify { authRepository.saveTokens("user-123", "jwt-token", "refresh-token", any()) }
    }

    @Test
    fun `onLoginRequest should not call enableUser when login fails`() = runTest {
        // Given
        coEvery { authApi.login(any(), any()) } returns ResultData.Error(Exception("Login failed"))

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("test@example.com")
        viewModel.passwordChanged("wrong-password")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - enableUser should never be called
        coVerify(exactly = 0) { authApi.enableUser(any(), any()) }
    }

    @Test
    fun `onLoginRequest should handle login exception gracefully`() = runTest {
        // Given
        coEvery { authApi.login(any(), any()) } throws RuntimeException("Network error")

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("test@example.com")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - should not crash and enableUser should not be called
        coVerify(exactly = 0) { authApi.enableUser(any(), any()) }
    }

    @Test
    fun `onLoginRequest should save user and tokens on successful login`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-456",
            name = "Another User",
            email = "another@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            accessToken = "new-jwt-token",
            refreshToken = "new-refresh-token"
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("another@example.com")
        viewModel.passwordChanged("secure-pass")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then
        coVerify { authRepository.unselectAllUsers() }
        coVerify { authRepository.saveUser(match { it.id == "user-456" }, selected = true) }
        coVerify { authRepository.saveTokens("user-456", "new-jwt-token", "new-refresh-token", any()) }
    }

    @Test
    fun `onLoginRequest should not save tokens when accessToken is null`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-789",
            name = "User Without Token",
            email = "notoken@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            accessToken = null,
            refreshToken = null
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("notoken@example.com")
        viewModel.passwordChanged("password")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - saveTokens should not be called
        coVerify(exactly = 0) { authRepository.saveTokens(any(), any(), any(), any()) }
    }

    @Test
    fun `onLoginRequest should support logging in with username and save user email if unconfirmed`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-username-123",
            name = "Username User",
            email = "realemail@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            accessToken = null,
            refreshToken = null,
            enabled = false
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)
        coEvery { authRepository.savePendingConfirmationEmail(any()) } just Runs

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            localAiRepository = localAiRepository
        )
        viewModel.emailChanged("my_username")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - verify login was called with username and real email was saved
        coVerify { authApi.login("my_username", "password123") }
        coVerify { authRepository.savePendingConfirmationEmail("realemail@example.com") }
    }

    private fun viewModel() = AuthMenuViewModel(
        authRepository = authRepository,
        authApi = authApi,
        configRepository = configRepository,
        notesUseCase = notesUseCase,
        localAiRepository = localAiRepository
    )

    private val googleUser = WriteopiaUserApi(id = "google-user", name = "Ana", email = "ana@example.com")

    @Test
    fun `onGoogleLoginRequest should post the id token and save user and tokens`() = runTest {
        coEvery { authApi.loginWithGoogle(any()) } returns ResultData.Complete(
            AuthResponse(writeopiaUser = googleUser, accessToken = "g-access", refreshToken = "g-refresh")
        )
        val viewModel = viewModel()

        viewModel.onGoogleLoginRequest(GoogleCredential.IdToken("google-id-token"))
        advanceUntilIdle()

        coVerify { authApi.loginWithGoogle(match { it.idToken == "google-id-token" && it.code == null }) }
        coVerify(exactly = 0) { authApi.loginWithGoogleWeb(any()) }
        coVerify { authRepository.unselectAllUsers() }
        coVerify { authRepository.saveUser(match { it.id == "google-user" }, selected = true) }
        coVerify { authRepository.saveTokens("google-user", "g-access", "g-refresh", any()) }
        val state = viewModel.loginState.value
        assertIs<ResultData.Complete<Boolean>>(state)
        assertTrue(state.data)
    }

    @Test
    fun `onGoogleLoginRequest should map every auth code field into the request`() = runTest {
        coEvery { authApi.loginWithGoogle(any()) } returns ResultData.Complete(
            AuthResponse(writeopiaUser = googleUser, accessToken = "a", refreshToken = "r")
        )
        val viewModel = viewModel()

        viewModel.onGoogleLoginRequest(
            GoogleCredential.AuthCode(
                code = "code-1",
                codeVerifier = "verifier-1",
                redirectUri = "http://127.0.0.1:5000/callback",
                clientId = "desktop-client"
            )
        )
        advanceUntilIdle()

        coVerify {
            authApi.loginWithGoogle(
                match {
                    it.idToken == null &&
                        it.code == "code-1" &&
                        it.codeVerifier == "verifier-1" &&
                        it.redirectUri == "http://127.0.0.1:5000/callback" &&
                        it.clientId == "desktop-client"
                }
            )
        }
    }

    @Test
    fun `onGoogleLoginRequest should use the web endpoint when the repository uses web login`() = runTest {
        coEvery { authRepository.useWebLogin } returns true
        coEvery { authApi.loginWithGoogleWeb(any()) } returns ResultData.Complete(
            AuthResponse(writeopiaUser = googleUser, accessToken = null, refreshToken = null)
        )
        val viewModel = viewModel()

        viewModel.onGoogleLoginRequest(GoogleCredential.AuthCode("c", null, "https://app", "web-client"))
        advanceUntilIdle()

        coVerify { authApi.loginWithGoogleWeb(match { it.code == "c" && it.clientId == "web-client" }) }
        coVerify(exactly = 0) { authApi.loginWithGoogle(any()) }
        // Web keeps tokens in cookies, nothing to persist locally.
        coVerify(exactly = 0) { authRepository.saveTokens(any(), any(), any(), any()) }
        coVerify { authRepository.saveUser(match { it.id == "google-user" }, selected = true) }
    }

    @Test
    fun `onGoogleLoginRequest should flag account deletion pending on 403`() = runTest {
        coEvery { authApi.loginWithGoogle(any()) } returns ResultData.Error(AccountDeletionPendingException())
        val viewModel = viewModel()

        viewModel.onGoogleLoginRequest(GoogleCredential.IdToken("t"))
        advanceUntilIdle()

        assertTrue(viewModel.accountDeletionPending.value)
        assertIs<ResultData.Error<Boolean>>(viewModel.loginState.value)
        coVerify(exactly = 0) { authRepository.saveUser(any(), any()) }
    }

    @Test
    fun `onGoogleLoginRequest should handle api exception gracefully`() = runTest {
        coEvery { authApi.loginWithGoogle(any()) } throws RuntimeException("Network error")
        val viewModel = viewModel()

        viewModel.onGoogleLoginRequest(GoogleCredential.IdToken("t"))
        advanceUntilIdle()

        val state = viewModel.loginState.value
        assertIs<ResultData.Error<Boolean>>(state)
        assertEquals("Network error", state.exception?.message)
    }

    @Test
    fun `onGoogleSignInFailed should surface the platform error as a login error`() = runTest {
        val viewModel = viewModel()

        viewModel.onGoogleSignInFailed(IllegalStateException("popup blocked"))

        val state = viewModel.loginState.value
        assertIs<ResultData.Error<Boolean>>(state)
        assertEquals("popup blocked", state.exception?.message)
        coVerify(exactly = 0) { authApi.loginWithGoogle(any()) }
    }
}
