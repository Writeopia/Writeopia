package io.writeopia.auth.menu

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.writeopia.OllamaRepository
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthMenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi
    private lateinit var configRepository: ConfigurationRepository
    private lateinit var notesUseCase: NotesUseCase
    private lateinit var ollamaRepository: OllamaRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
        configRepository = mockk(relaxed = true)
        notesUseCase = mockk(relaxed = true)
        ollamaRepository = mockk(relaxed = true)
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
            token = "jwt-token"
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)
        coEvery { authRepository.unselectAllUsers() } just Runs
        coEvery { authRepository.saveUser(any(), any()) } just Runs
        coEvery { authRepository.saveToken(any(), any()) } just Runs
        coEvery { authApi.enableUser(any(), any()) } returns ResultData.Complete(Unit)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            ollamaRepository = ollamaRepository
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
        coVerify { authRepository.saveToken("user-123", "jwt-token") }
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
            ollamaRepository = ollamaRepository
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
            ollamaRepository = ollamaRepository
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
    fun `onLoginRequest should save user and token on successful login`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-456",
            name = "Another User",
            email = "another@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            token = "new-jwt-token"
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            ollamaRepository = ollamaRepository
        )
        viewModel.emailChanged("another@example.com")
        viewModel.passwordChanged("secure-pass")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then
        coVerify { authRepository.unselectAllUsers() }
        coVerify { authRepository.saveUser(match { it.id == "user-456" }, selected = true) }
        coVerify { authRepository.saveToken("user-456", "new-jwt-token") }
    }

    @Test
    fun `onLoginRequest should not save token when token is null`() = runTest {
        // Given
        val testUser = WriteopiaUserApi(
            id = "user-789",
            name = "User Without Token",
            email = "notoken@example.com"
        )
        val authResponse = AuthResponse(
            writeopiaUser = testUser,
            token = null
        )

        coEvery { authApi.login(any(), any()) } returns ResultData.Complete(authResponse)

        val viewModel = AuthMenuViewModel(
            authRepository = authRepository,
            authApi = authApi,
            configRepository = configRepository,
            notesUseCase = notesUseCase,
            ollamaRepository = ollamaRepository
        )
        viewModel.emailChanged("notoken@example.com")
        viewModel.passwordChanged("password")

        // When
        viewModel.onLoginRequest()
        advanceUntilIdle()

        // Then - saveToken should not be called
        coVerify(exactly = 0) { authRepository.saveToken(any(), any()) }
    }
}
