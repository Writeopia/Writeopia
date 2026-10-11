package io.writeopia.auth.register

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi

    private val registerResponse = RegisterResponse(
        writeopiaUser = WriteopiaUserApi(
            id = "user-123",
            name = "Test User",
            email = "test@example.com"
        ),
        emailConfirmationRequired = false,
        accessToken = "access",
        refreshToken = "refresh",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
        coEvery { authApi.isUsernameAvailable(any()) } returns ResultData.Complete(true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun filledViewModel() = RegisterViewModel(authRepository, authApi).apply {
        setVerifiedEmail("test@example.com", "123456")
        nameChanged("Test User")
        usernameChanged("testuser")
        workspaceChanged("My Workspace")
        passwordChanged("password123")
    }

    @Test
    fun `onRegister should send the verification code and sign the new user in`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any()) } returns
            ResultData.Complete(registerResponse)

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        coVerify {
            authApi.register("Test User", "test@example.com", "My Workspace", "password123", "testuser", "123456")
        }
        coVerify { authRepository.saveUser(any(), selected = true) }
        coVerify {
            authRepository.saveTokens(
                userId = "user-123",
                accessToken = "access",
                refreshToken = "refresh",
                expiresAt = any()
            )
        }
        assertEquals(ResultData.Complete(true), viewModel.register.value)
    }

    @Test
    fun `on web onRegister should sign in with cookies instead of saving tokens`() = runTest {
        every { authRepository.useWebLogin } returns true
        coEvery { authApi.register(any(), any(), any(), any(), any(), any()) } returns
            ResultData.Complete(registerResponse)

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        coVerify { authApi.loginWeb("test@example.com", "password123") }
        coVerify(exactly = 0) { authRepository.saveTokens(any(), any(), any(), any()) }
    }

    @Test
    fun `onRegister should report a registration error`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any()) } returns
            ResultData.Error(Exception("Registration failed"))

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        assertIs<ResultData.Error<*>>(viewModel.register.value)
        coVerify(exactly = 0) { authRepository.saveUser(any(), any()) }
    }

    @Test
    fun `onRegister should handle registration error gracefully`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("Network error")

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        assertIs<ResultData.Error<*>>(viewModel.register.value)
    }

    @Test
    fun `a taken username is flagged and blocks registering`() = runTest {
        coEvery { authApi.isUsernameAvailable("taken") } returns ResultData.Complete(false)

        val viewModel = filledViewModel()
        viewModel.canRegister.launchIn(backgroundScope)
        viewModel.usernameChanged("taken")
        advanceUntilIdle()

        assertEquals(UsernameAvailability.TAKEN, viewModel.usernameAvailability.value)
        assertFalse(viewModel.canRegister.value)
    }

    @Test
    fun `a free username can be registered`() = runTest {
        val viewModel = filledViewModel()
        viewModel.passwordChanged("password123!")
        viewModel.canRegister.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(UsernameAvailability.AVAILABLE, viewModel.usernameAvailability.value)
        assertTrue(viewModel.canRegister.value)
    }

    @Test
    fun `only the username typed last is checked`() = runTest {
        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.usernameChanged("ana")
        viewModel.usernameChanged("ana_b")
        viewModel.usernameChanged("ana_bo")
        advanceUntilIdle()

        coVerify(exactly = 1) { authApi.isUsernameAvailable(any()) }
        coVerify { authApi.isUsernameAvailable("ana_bo") }
    }

    @Test
    fun `an invalid username is not checked`() = runTest {
        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.usernameChanged("ab")
        advanceUntilIdle()

        coVerify(exactly = 0) { authApi.isUsernameAvailable(any()) }
        assertEquals(UsernameAvailability.UNKNOWN, viewModel.usernameAvailability.value)
    }

    @Test
    fun `a failed check does not block registering`() = runTest {
        coEvery { authApi.isUsernameAvailable(any()) } returns ResultData.Error(Exception("offline"))

        val viewModel = filledViewModel()
        viewModel.passwordChanged("password123!")
        viewModel.canRegister.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(UsernameAvailability.UNKNOWN, viewModel.usernameAvailability.value)
        assertTrue(viewModel.canRegister.value)
    }
}
