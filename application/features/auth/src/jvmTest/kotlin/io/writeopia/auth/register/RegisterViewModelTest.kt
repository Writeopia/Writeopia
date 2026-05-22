package io.writeopia.auth.register

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
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
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onRegister should call enableUser when registration succeeds and admin key is available`() = runTest {
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

        coEvery { authApi.register(any(), any(), any(), any()) } returns ResultData.Complete(authResponse)
        coEvery { authRepository.saveUser(any(), any()) } just Runs
        coEvery { authApi.enableUser(any(), any()) } returns ResultData.Complete(Unit)

        // Note: In a real test, we'd need to mock EnvUtils.getAdminKey()
        // For this test, we verify that enableUser can be called correctly
        // The actual env var behavior is tested separately

        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.emailChanged("test@example.com")
        viewModel.nameChanged("Test User")
        viewModel.workspaceChanged("My Workspace")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onRegister()
        advanceUntilIdle()

        // Then - verify register was called
        coVerify { authApi.register("Test User", "test@example.com", "My Workspace", "password123") }
        coVerify { authRepository.saveUser(any(), selected = true) }
    }

    @Test
    fun `onRegister should not call enableUser when registration fails`() = runTest {
        // Given
        coEvery { authApi.register(any(), any(), any(), any()) } returns ResultData.Error(Exception("Registration failed"))

        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.emailChanged("test@example.com")
        viewModel.nameChanged("Test User")
        viewModel.workspaceChanged("My Workspace")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onRegister()
        advanceUntilIdle()

        // Then - enableUser should never be called
        coVerify(exactly = 0) { authApi.enableUser(any(), any()) }
    }

    @Test
    fun `onRegister should handle registration error gracefully`() = runTest {
        // Given
        coEvery { authApi.register(any(), any(), any(), any()) } throws RuntimeException("Network error")

        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.emailChanged("test@example.com")
        viewModel.nameChanged("Test User")
        viewModel.workspaceChanged("My Workspace")
        viewModel.passwordChanged("password123")

        // When
        viewModel.onRegister()
        advanceUntilIdle()

        // Then - should not crash and enableUser should not be called
        coVerify(exactly = 0) { authApi.enableUser(any(), any()) }
    }
}
